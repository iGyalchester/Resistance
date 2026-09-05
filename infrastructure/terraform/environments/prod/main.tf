locals {
  environment = "prod"
  name        = "resistance-${local.environment}"
  tags = {
    Environment = local.environment
  }
}

# Always on (free): the image registry and the mail routing.

module "ecr" {
  source = "../../modules/ecr"

  name        = local.name
  environment = local.environment
  tags        = local.tags
}

module "email_intake" {
  source = "../../modules/email-intake"

  name           = local.name
  mail_domain    = var.mail_domain
  hosted_zone_id = var.hosted_zone_id
  rule_set_name  = var.ses_rule_set_name

  # The SNS subscription can only be confirmed by a running intake-service,
  # so it exists only while the app does.
  intake_endpoint_url = var.app_enabled ? "https://${var.app_host}/intake/aws-sns" : ""

  tags = local.tags
}

# Gated (costs money while on): the network, the database, the secrets and
# the two Fargate services behind an HTTPS load balancer. Push images first
# (Deploy workflow), then flip app_enabled - with an empty registry every
# task would crash-loop on image pull while the ALB and RDS bill by the hour.

module "network" {
  source = "../../modules/network"
  count  = var.app_enabled ? 1 : 0

  name          = local.name
  vpc_cidr      = var.vpc_cidr
  azs           = var.azs
  service_ports = values(module.ecr.service_ports)
  tags          = local.tags
}

module "secrets" {
  source = "../../modules/secrets"
  count  = var.app_enabled ? 1 : 0

  name                     = local.name
  environment              = local.environment
  mail_domain              = var.mail_domain
  kms_deletion_window_days = var.kms_deletion_window_days
  tags                     = local.tags
}

module "database" {
  source = "../../modules/database"
  count  = var.app_enabled ? 1 : 0

  name                  = local.name
  private_subnet_ids    = module.network[0].private_subnet_ids
  security_group_id     = module.network[0].database_security_group_id
  kms_key_arn           = module.secrets[0].kms_key_arn
  instance_class        = var.db_instance_class
  multi_az              = var.db_multi_az
  backup_retention_days = var.db_backup_retention_days
  deletion_protection   = var.db_deletion_protection
  skip_final_snapshot   = var.db_skip_final_snapshot
  tags                  = local.tags
}

module "app" {
  source = "../../modules/app"
  count  = var.app_enabled ? 1 : 0

  name                    = local.name
  vpc_id                  = module.network[0].vpc_id
  public_subnet_ids       = module.network[0].public_subnet_ids
  alb_security_group_id   = module.network[0].alb_security_group_id
  tasks_security_group_id = module.network[0].tasks_security_group_id
  hosted_zone_id          = var.hosted_zone_id
  app_host                = var.app_host

  repository_urls = module.ecr.repository_urls

  # Not a tfvars setting: the Deploy workflow writes the sha it pushed into
  # the SSM parameter modules/ecr manages, and this reads it back. One
  # writer for images, one writer for task definitions, no drift between.
  image_tag     = module.ecr.image_tag
  desired_count = var.desired_count
  task_cpu      = var.task_cpu
  task_memory   = var.task_memory

  log_retention_days      = var.log_retention_days
  alb_deletion_protection = var.alb_deletion_protection

  db_endpoint    = module.database[0].endpoint
  db_port        = module.database[0].port
  db_secret_arn  = module.database[0].master_user_secret_arn
  kms_key_arn    = module.secrets[0].kms_key_arn
  parameter_arns = module.secrets[0].parameter_arns

  smtp_host        = "email-smtp.${var.aws_region}.amazonaws.com"
  otp_from_address = "${var.otp_local_part}@${var.mail_domain}"
  intake_address   = module.email_intake.intake_address
  intake_topic_arn = module.email_intake.topic_arn

  audit_url                    = var.audit_url
  audit_token_secret_arn       = var.audit_token_secret_arn
  anthropic_api_key_secret_arn = var.anthropic_api_key_secret_arn

  tags = local.tags
}
