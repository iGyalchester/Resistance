# Every secret the qa profile needs, generated here so no human ever types
# or copies one:
#
#   TRACKER_ENC_KEY       32 random bytes, the AES-256-GCM field-encryption key
#   INTAKE_WEBHOOK_TOKEN  the shared secret for POST /intake/email
#   SMTP_USERNAME/PASSWORD  SES SMTP credentials, derived from an IAM access key
#
# All are stored as SSM Parameter Store SecureStrings encrypted under this
# environment's KMS key, and reach the containers only through the ECS
# task definition's `secrets` (the execution role reads them at start).
# They exist in Terraform state too, which is why the state bucket is
# encrypted, versioned and TLS-only.

data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

locals {
  prefix = "/resistance/${var.environment}"
}

# --- the key that protects everything below ----------------------------------

data "aws_iam_policy_document" "key" {
  statement {
    sid    = "EnableRootAccountAccess"
    effect = "Allow"
    principals {
      type        = "AWS"
      identifiers = ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"]
    }
    actions   = ["kms:*"]
    resources = ["*"]
  }
}

resource "aws_kms_key" "this" {
  description             = "Resistance ${var.environment}: SSM secrets and RDS storage"
  deletion_window_in_days = var.kms_deletion_window_days
  enable_key_rotation     = true
  policy                  = data.aws_iam_policy_document.key.json
  tags                    = var.tags
}

resource "aws_kms_alias" "this" {
  name          = "alias/${var.name}"
  target_key_id = aws_kms_key.this.key_id
}

# --- field-encryption key ----------------------------------------------------

resource "random_bytes" "field_encryption_key" {
  length = 32
}

resource "aws_ssm_parameter" "field_encryption_key" {
  name        = "${local.prefix}/tracker-enc-key"
  description = "AES-256-GCM data key for PII columns (TRACKER_ENC_KEY). Losing this loses the encrypted values."
  type        = "SecureString"
  key_id      = aws_kms_key.this.key_id
  value       = random_bytes.field_encryption_key.base64
  tags        = var.tags
}

# --- webhook token -----------------------------------------------------------

resource "random_password" "webhook_token" {
  length  = 48
  special = false
}

resource "aws_ssm_parameter" "webhook_token" {
  name        = "${local.prefix}/intake-webhook-token"
  description = "Shared secret for POST /intake/email (INTAKE_WEBHOOK_TOKEN)."
  type        = "SecureString"
  key_id      = aws_kms_key.this.key_id
  value       = random_password.webhook_token.result
  tags        = var.tags
}

# --- SES SMTP credentials ----------------------------------------------------
#
# SES SMTP does not take an IAM role: its credentials are an IAM access key
# id plus a password derived from the secret key. So this is the one place
# a long-lived key exists - on a user that can do exactly one thing, send
# mail from this environment's domain, and whose credentials no person
# ever sees.

resource "aws_iam_user" "ses_smtp" {
  name                 = "${var.name}-ses-smtp"
  permissions_boundary = var.permissions_boundary_arn
  tags                 = var.tags
}

data "aws_iam_policy_document" "ses_smtp" {
  statement {
    effect    = "Allow"
    actions   = ["ses:SendRawEmail", "ses:SendEmail"]
    resources = ["arn:aws:ses:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:identity/${var.mail_domain}"]
  }
}

resource "aws_iam_user_policy" "ses_smtp" {
  name   = "send-from-${replace(var.mail_domain, ".", "-")}"
  user   = aws_iam_user.ses_smtp.name
  policy = data.aws_iam_policy_document.ses_smtp.json
}

resource "aws_iam_access_key" "ses_smtp" {
  user = aws_iam_user.ses_smtp.name
}

resource "aws_ssm_parameter" "smtp_username" {
  name        = "${local.prefix}/smtp-username"
  description = "SES SMTP username (SMTP_USERNAME)."
  type        = "SecureString"
  key_id      = aws_kms_key.this.key_id
  value       = aws_iam_access_key.ses_smtp.id
  tags        = var.tags
}

resource "aws_ssm_parameter" "smtp_password" {
  name        = "${local.prefix}/smtp-password"
  description = "SES SMTP password derived from the access key (SMTP_PASSWORD)."
  type        = "SecureString"
  key_id      = aws_kms_key.this.key_id
  value       = aws_iam_access_key.ses_smtp.ses_smtp_password_v4
  tags        = var.tags
}
