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

  name = local.name
  tags = local.tags
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
