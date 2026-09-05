# Lets GitHub Actions assume an AWS role via short-lived OIDC tokens instead
# of long-lived access keys stored as repo secrets - removes an entire class
# of credential-leak risk and is the direction AWS/GitHub both steer CI/CD
# integrations towards.

data "aws_caller_identity" "current" {}

data "tls_certificate" "github_actions" {
  url = "https://token.actions.githubusercontent.com/.well-known/openid-configuration"
}

resource "aws_iam_openid_connect_provider" "github_actions" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.github_actions.certificates[0].sha1_fingerprint]
}

locals {
  # The only IAM principals the environments create, and therefore the only
  # ones CI may touch: modules/app's "<name>-ecs-execution" role and
  # modules/secrets' "<name>-ses-smtp" user, where name is resistance-<env>.
  ci_managed_roles = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/resistance-*"
  ci_managed_users = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:user/resistance-*"

  # Two axes of variation in GitHub's OIDC subject:
  # - Jobs bound to a GitHub Environment present `environment:<name>`
  #   instead of the ref/pull_request forms.
  # - GitHub's immutable-reference format appends "@<numeric id>" to the
  #   owner and repo segments (repo:org@123/name@456:...), so each pattern
  #   needs a classic and an @-suffixed variant.
  github_subjects = flatten([
    for repo in var.github_repos : [
      "repo:${var.github_org}/${repo}:ref:refs/heads/main",
      "repo:${var.github_org}/${repo}:pull_request",
      "repo:${var.github_org}/${repo}:environment:*",
      "repo:${var.github_org}@*/${repo}@*:ref:refs/heads/main",
      "repo:${var.github_org}@*/${repo}@*:pull_request",
      "repo:${var.github_org}@*/${repo}@*:environment:*",
    ]
  ])
}

data "aws_iam_policy_document" "github_actions_trust" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github_actions.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values   = local.github_subjects
    }
  }
}

resource "aws_iam_role" "github_actions_deploy" {
  name               = "resistance-github-actions-deploy"
  assume_role_policy = data.aws_iam_policy_document.github_actions_trust.json
}

# The ceiling on every IAM principal CI creates. A permissions boundary is
# not a grant: it is the maximum a role or user can ever have, no matter
# what policy is later written onto it. Without one, "CI may write IAM
# policies" quietly means "CI may become administrator" - it can create a
# role, attach a policy far broader than its own, and pass or assume it.
#
# Everything listed is something the two principals the environments create
# genuinely need: modules/app's ECS execution role (pull the image, ship
# logs, read the injected secrets) and modules/secrets' SES SMTP user (send
# mail). Resources stay "*" here on purpose - the boundary caps the actions,
# the principals' own policies do the resource scoping, and a principal only
# ever gets the intersection of the two.
data "aws_iam_policy_document" "ci_boundary" {
  statement {
    sid    = "PullImagesAndShipLogs"
    effect = "Allow"
    actions = [
      "ecr:GetAuthorizationToken",
      "ecr:BatchCheckLayerAvailability",
      "ecr:GetDownloadUrlForLayer",
      "ecr:BatchGetImage",
      "logs:CreateLogGroup",
      "logs:CreateLogStream",
      "logs:PutLogEvents",
      "logs:DescribeLogStreams",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "ReadInjectedSecrets"
    effect = "Allow"
    actions = [
      "secretsmanager:GetSecretValue",
      "ssm:GetParameter",
      "ssm:GetParameters",
      "kms:Decrypt",
    ]
    resources = ["*"]
  }

  statement {
    sid       = "SendMail"
    effect    = "Allow"
    actions   = ["ses:SendEmail", "ses:SendRawEmail"]
    resources = ["*"]
  }

  # intake-service's task role reads the MIME SES archived. The boundary is
  # a ceiling, not a grant: the role's own policy is scoped to the one
  # bucket. Without this line that policy would be capped away and the
  # service would fall back to header-only parsing, which looks like a
  # parser bug rather than a permissions one.
  #
  # Read only, and no s3:ListBucket: the object key always arrives in the
  # notification, so nothing needs to enumerate the bucket.
  statement {
    sid       = "ReadArchivedMail"
    effect    = "Allow"
    actions   = ["s3:GetObject"]
    resources = ["arn:aws:s3:::resistance-*-raw-mail-*/*"]
  }
}

resource "aws_iam_policy" "ci_boundary" {
  name        = "resistance-ci-boundary"
  description = "Permissions boundary every IAM principal created by CI must carry. Each environment references it as permissions_boundary_arn."
  policy      = data.aws_iam_policy_document.ci_boundary.json
}

# Every AWS service the environments touch, listed up front. This policy is
# applied by hand and the role cannot widen it, so a service missing here
# surfaces later as an AccessDenied in CI (README: "Bootstrap drift").
# Service-scoped rather than resource-scoped: tightening to specific ARNs is
# a natural step once the first apply has produced real resource ids.
#
# IAM is the exception and gets its own statements below. Any pull request
# runs the plan job as this role, so "may write IAM" has to mean "may write
# these two principals, capped by the boundary" and nothing more.
data "aws_iam_policy_document" "github_actions_deploy" {
  statement {
    sid    = "TrackerServices"
    effect = "Allow"
    actions = [
      "s3:*",
      "ecr:*",
      "ecs:*",
      "elasticloadbalancing:*",
      "ec2:*",
      "rds:*",
      "ses:*",
      "sns:*",
      "kms:*",
      "ssm:*",
      "secretsmanager:*",
      "logs:*",
      "cloudwatch:*",
      "route53:*",
      "acm:*",
      "application-autoscaling:*",
    ]
    resources = ["*"]
  }

  # Reading, tagging and deleting the principals this stack owns. Their names
  # are fixed by the modules: a "<name>-ecs-execution" role and a
  # "<name>-ses-smtp" user, where name is resistance-<environment>.
  statement {
    sid    = "ManageTrackerPrincipals"
    effect = "Allow"
    actions = [
      "iam:GetRole",
      "iam:DeleteRole",
      "iam:TagRole",
      "iam:UntagRole",
      "iam:ListRoleTags",
      "iam:UpdateAssumeRolePolicy",
      "iam:DeleteRolePolicy",
      "iam:GetRolePolicy",
      "iam:DetachRolePolicy",
      "iam:ListRolePolicies",
      "iam:ListAttachedRolePolicies",
      "iam:ListInstanceProfilesForRole",
      # the SES SMTP sender is an IAM user with one access key (secrets module)
      "iam:GetUser",
      "iam:DeleteUser",
      "iam:TagUser",
      "iam:UntagUser",
      "iam:ListUserTags",
      "iam:DeleteUserPolicy",
      "iam:GetUserPolicy",
      "iam:ListUserPolicies",
      "iam:ListAttachedUserPolicies",
      "iam:ListGroupsForUser",
      "iam:CreateAccessKey",
      "iam:DeleteAccessKey",
      "iam:ListAccessKeys",
      "iam:UpdateAccessKey",
    ]
    resources = [local.ci_managed_roles, local.ci_managed_users]
  }

  # Creating a principal, or writing any policy onto one, is allowed only
  # while that principal carries the boundary. Resource scoping on its own
  # would not be enough: IAM happily lets a principal write an inline policy
  # broader than its own onto a role it is allowed to create.
  statement {
    sid    = "CreateTrackerPrincipalsOnlyWithTheBoundary"
    effect = "Allow"
    actions = [
      "iam:CreateRole",
      "iam:CreateUser",
      "iam:PutRolePolicy",
      "iam:PutUserPolicy",
      "iam:AttachRolePolicy",
      "iam:AttachUserPolicy",
    ]
    resources = [local.ci_managed_roles, local.ci_managed_users]

    condition {
      test     = "StringEquals"
      variable = "iam:PermissionsBoundary"
      values   = [aws_iam_policy.ci_boundary.arn]
    }
  }

  # Attaching a managed policy makes the provider read it back.
  statement {
    sid       = "ReadOwnManagedPolicies"
    effect    = "Allow"
    actions   = ["iam:GetPolicy", "iam:GetPolicyVersion", "iam:ListPolicyVersions"]
    resources = ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:policy/resistance-*"]
  }

  # Handing the execution role to a task definition. Unconditioned, PassRole
  # is itself the escalation: pass a powerful role to something you control
  # and read its credentials back out.
  statement {
    sid       = "PassRolesToEcsTasksOnly"
    effect    = "Allow"
    actions   = ["iam:PassRole"]
    resources = [local.ci_managed_roles]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ecs-tasks.amazonaws.com"]
    }
  }

  # ECS and Application Auto Scaling create their own service-linked roles on
  # first use; those live under a reserved path and are harmless.
  statement {
    sid       = "ServiceLinkedRoles"
    effect    = "Allow"
    actions   = ["iam:CreateServiceLinkedRole"]
    resources = ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/aws-service-role/*"]
  }

  # Creating with a boundary buys nothing if the boundary can be taken off a
  # moment later. An explicit Deny cannot be overridden by any Allow, present
  # or future. It also means that dropping permissions_boundary from a module
  # fails the apply loudly rather than silently widening a principal.
  statement {
    sid    = "NeverStripTheBoundary"
    effect = "Deny"
    actions = [
      "iam:DeleteRolePermissionsBoundary",
      "iam:DeleteUserPermissionsBoundary",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "github_actions_deploy" {
  name   = "resistance-tracker-services"
  role   = aws_iam_role.github_actions_deploy.id
  policy = data.aws_iam_policy_document.github_actions_deploy.json
}
