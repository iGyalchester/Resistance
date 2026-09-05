# The tracker itself: two Fargate services behind one HTTPS load balancer.
#
#   https://<app_host>/intake/*  -> intake-service (webhook, SNS endpoint)
#   https://<app_host>/*         -> mvc-service    (pages, /api, login)
#
# Fargate because there is no instance to size or patch for two small
# containers; the ALB because SNS will only deliver to a valid HTTPS
# endpoint and because it is the natural place for the certificate and
# the path split (the repo's hand-rolled api-gateway is a dev convenience,
# not something to run in production).

data "aws_region" "current" {}

resource "aws_ecs_cluster" "this" {
  name = var.name

  setting {
    name  = "containerInsights"
    value = "enabled"
  }

  tags = var.tags
}

resource "aws_cloudwatch_log_group" "services" {
  name              = "/resistance/${var.name}/services"
  retention_in_days = var.log_retention_days
  tags              = var.tags
}

# --- certificate + DNS -------------------------------------------------------

resource "aws_acm_certificate" "this" {
  domain_name       = var.app_host
  validation_method = "DNS"
  tags              = var.tags

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_route53_record" "cert_validation" {
  for_each = {
    for dvo in aws_acm_certificate.this.domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      record = dvo.resource_record_value
      type   = dvo.resource_record_type
    }
  }

  zone_id         = var.hosted_zone_id
  name            = each.value.name
  type            = each.value.type
  ttl             = 60
  records         = [each.value.record]
  allow_overwrite = true
}

resource "aws_acm_certificate_validation" "this" {
  certificate_arn         = aws_acm_certificate.this.arn
  validation_record_fqdns = [for r in aws_route53_record.cert_validation : r.fqdn]
}

# --- load balancer -----------------------------------------------------------

resource "aws_lb" "this" {
  name                       = "${var.name}-alb"
  internal                   = false
  load_balancer_type         = "application"
  security_groups            = [var.alb_security_group_id]
  subnets                    = var.public_subnet_ids
  enable_deletion_protection = var.alb_deletion_protection
  drop_invalid_header_fields = true
  tags                       = var.tags
}

resource "aws_lb_target_group" "service" {
  for_each = var.services

  name        = "${var.name}-${replace(each.key, "-service", "")}"
  port        = each.value
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"

  health_check {
    path                = "/actuator/health"
    matcher             = "200"
    interval            = 30
    healthy_threshold   = 2
    unhealthy_threshold = 5
  }

  # Login state lives in the servlet session on one task, so a browser
  # must keep talking to the task it logged in on.
  stickiness {
    type            = "lb_cookie"
    cookie_duration = 86400
    enabled         = each.key == "mvc-service"
  }

  tags = var.tags
}

resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.this.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = aws_acm_certificate_validation.this.certificate_arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.service["mvc-service"].arn
  }

  tags = var.tags
}

resource "aws_lb_listener_rule" "intake" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 10

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.service["intake-service"].arn
  }

  condition {
    path_pattern {
      values = ["/intake/*"]
    }
  }

  tags = var.tags
}

resource "aws_lb_listener" "http_redirect" {
  load_balancer_arn = aws_lb.this.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "redirect"
    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }

  tags = var.tags
}

resource "aws_route53_record" "app" {
  zone_id = var.hosted_zone_id
  name    = var.app_host
  type    = "A"

  alias {
    name                   = aws_lb.this.dns_name
    zone_id                = aws_lb.this.zone_id
    evaluate_target_health = true
  }
}

# --- IAM: the execution role starts the container. The code itself needs
#     almost no AWS permissions - mail goes out over SMTP and SNS pushes to
#     us - with one exception: intake-service reads the raw MIME that SES
#     archived, so it gets a task role scoped to that bucket. ---------------

data "aws_iam_policy_document" "task_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "execution" {
  name                 = "${var.name}-ecs-execution"
  assume_role_policy   = data.aws_iam_policy_document.task_assume.json
  permissions_boundary = var.permissions_boundary_arn
  tags                 = var.tags
}

resource "aws_iam_role_policy_attachment" "execution_managed" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "execution_secrets" {
  statement {
    sid       = "ReadSecrets"
    effect    = "Allow"
    actions   = ["secretsmanager:GetSecretValue"]
    resources = compact([var.db_secret_arn, var.audit_token_secret_arn, var.anthropic_api_key_secret_arn])
  }

  statement {
    sid       = "ReadParameters"
    effect    = "Allow"
    actions   = ["ssm:GetParameters"]
    resources = values(var.parameter_arns)
  }

  statement {
    sid       = "DecryptParameters"
    effect    = "Allow"
    actions   = ["kms:Decrypt"]
    resources = [var.kms_key_arn]
  }
}

resource "aws_iam_role_policy" "execution_secrets" {
  name   = "${var.name}-read-secrets"
  role   = aws_iam_role.execution.id
  policy = data.aws_iam_policy_document.execution_secrets.json
}

# The role the *application* runs as, as opposed to the execution role the
# ECS agent uses to pull the image and read secrets. Only intake-service
# gets it, and it can do exactly one thing: read objects out of the raw-mail
# bucket. No ListBucket - the object key always arrives in the notification,
# so the service never needs to discover what is in there.
resource "aws_iam_role" "intake_task" {
  name                 = "${var.name}-intake-task"
  assume_role_policy   = data.aws_iam_policy_document.task_assume.json
  permissions_boundary = var.permissions_boundary_arn
  tags                 = var.tags
}

data "aws_iam_policy_document" "intake_raw_mail" {
  statement {
    sid       = "ReadArchivedMail"
    effect    = "Allow"
    actions   = ["s3:GetObject"]
    resources = ["arn:aws:s3:::${var.raw_mail_bucket_name}/*"]
  }
}

resource "aws_iam_role_policy" "intake_raw_mail" {
  name   = "${var.name}-read-raw-mail"
  role   = aws_iam_role.intake_task.id
  policy = data.aws_iam_policy_document.intake_raw_mail.json
}

# --- task definitions: exactly the qa profile's environment -----------------

locals {
  common_environment = [
    { name = "SPRING_PROFILES_ACTIVE", value = "qa" },
    { name = "DB_HOST", value = var.db_endpoint },
    { name = "DB_PORT", value = tostring(var.db_port) },
    { name = "TRACKER_AUDIT_URL", value = var.audit_url },
    { name = "AWS_REGION", value = data.aws_region.current.name },
    # let the JVM size its heap from the task's memory limit
    { name = "JAVA_TOOL_OPTIONS", value = "-XX:MaxRAMPercentage=75" },
  ]

  service_environment = {
    mvc-service = [
      { name = "SMTP_HOST", value = var.smtp_host },
      { name = "SMTP_PORT", value = "587" },
      { name = "OTP_FROM_ADDRESS", value = var.otp_from_address },
      { name = "INTAKE_ADDRESS", value = var.intake_address },
    ]
    intake-service = [
      { name = "INTAKE_AWS_TOPIC_ARN", value = var.intake_topic_arn },
      { name = "INTAKE_RAW_MAIL_BUCKET", value = var.raw_mail_bucket_name },
    ]
  }

  common_secrets = concat(
    [
      { name = "DB_USERNAME", valueFrom = "${var.db_secret_arn}:username::" },
      { name = "DB_PASSWORD", valueFrom = "${var.db_secret_arn}:password::" },
      { name = "TRACKER_ENC_KEY", valueFrom = var.parameter_arns["tracker_enc_key"] },
    ],
    var.audit_token_secret_arn != "" ? [
      { name = "TRACKER_AUDIT_TOKEN", valueFrom = var.audit_token_secret_arn },
    ] : []
  )

  service_secrets = {
    mvc-service = [
      { name = "SMTP_USERNAME", valueFrom = var.parameter_arns["smtp_username"] },
      { name = "SMTP_PASSWORD", valueFrom = var.parameter_arns["smtp_password"] },
    ]
    intake-service = concat(
      [
        { name = "INTAKE_WEBHOOK_TOKEN", valueFrom = var.parameter_arns["intake_webhook_token"] },
      ],
      var.anthropic_api_key_secret_arn != "" ? [
        { name = "ANTHROPIC_API_KEY", valueFrom = var.anthropic_api_key_secret_arn },
      ] : []
    )
  }
}

resource "aws_ecs_task_definition" "service" {
  for_each = var.services

  family                   = "${var.name}-${each.key}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.task_cpu
  memory                   = var.task_memory
  execution_role_arn       = aws_iam_role.execution.arn

  # Only intake-service needs an identity of its own, to read the archived
  # MIME out of the raw-mail bucket. mvc-service gets none: it talks to
  # MySQL and SMTP and has no business holding AWS credentials.
  task_role_arn = each.key == "intake-service" ? aws_iam_role.intake_task.arn : null

  # Keep old revisions ACTIVE. Rolling back is then "point the service at
  # revision N-1", which needs revision N-1 to still exist; without this
  # Terraform deregisters it the moment a new one is registered.
  skip_destroy = true

  container_definitions = jsonencode([
    {
      name      = each.key
      image     = "${var.repository_urls[each.key]}:${var.image_tag}"
      essential = true
      portMappings = [
        { containerPort = each.value, protocol = "tcp" }
      ]
      environment = concat(local.common_environment, local.service_environment[each.key])
      secrets     = concat(local.common_secrets, local.service_secrets[each.key])
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.services.name
          awslogs-region        = data.aws_region.current.name
          awslogs-stream-prefix = each.key
        }
      }
    }
  ])

  tags = var.tags
}

resource "aws_ecs_service" "service" {
  for_each = var.services

  name            = each.key
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.service[each.key].arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  # Spring Boot needs a while before /actuator/health answers; do not let
  # the ALB kill a task that is still starting.
  health_check_grace_period_seconds = 120

  network_configuration {
    subnets          = var.public_subnet_ids
    security_groups  = [var.tasks_security_group_id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.service[each.key].arn
    container_name   = each.key
    container_port   = each.value
  }

  # A deployment whose new tasks never become healthy rolls back instead
  # of leaving the service half-dead.
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  # Make "created" mean "tasks are running and healthy behind the ALB".
  # Without this the resource completes as soon as ECS accepts the desired
  # count, which is minutes before anything answers a request - and the SNS
  # subscription below depends on something answering.
  wait_for_steady_state = true

  depends_on = [aws_lb_listener.https]

  tags = var.tags
}

# --- SNS: subscribe intake-service to the topic ----------------------------
#
# This lives here rather than in modules/email-intake because SNS confirms an
# HTTPS subscription by POSTing to the endpoint and waiting for the service
# to fetch the SubscribeURL back (SnsIntakeController does that). So the
# subscription can only be created once DNS resolves, the certificate is
# attached and the tasks are healthy. The two dependencies below cover that:
# the DNS record directly, and the service both for health (it now waits for
# steady state) and, transitively, for the validated HTTPS listener it
# already depends on.
#
# Nothing is leaked by a public endpoint URL: every message's signature is
# verified against the AWS signing certificate before it is acted on.
resource "aws_sns_topic_subscription" "intake" {
  topic_arn              = var.intake_topic_arn
  protocol               = "https"
  endpoint               = "https://${var.app_host}/intake/aws-sns"
  endpoint_auto_confirms = true

  # The default is 1 minute. A cold task plus DNS propagation regularly needs
  # longer, and a timeout here leaves a "pending confirmation" subscription
  # that silently drops every email until someone notices.
  confirmation_timeout_in_minutes = 10

  depends_on = [
    aws_ecs_service.service,
    aws_route53_record.app,
  ]
}
