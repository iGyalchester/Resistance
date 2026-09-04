variable "name" {
  description = "Environment-scoped prefix, e.g. resistance-dev."
  type        = string
}

variable "mail_domain" {
  description = "Domain this environment receives and sends mail for: <domain> for prod, dev.<domain> for dev. Users forward to track@<mail_domain>; OTP mail is sent from the same domain."
  type        = string
}

variable "hosted_zone_id" {
  description = "Route53 hosted zone (from bootstrap) that mail_domain's verification, DKIM and MX records go into."
  type        = string
}

variable "rule_set_name" {
  description = "The account-level SES receipt rule set (from bootstrap) this environment adds its rule to."
  type        = string
}

variable "intake_local_part" {
  description = "Local part of the intake address; the recipient is <intake_local_part>@<mail_domain>. Plus-tagged variants (track+alias@) match the same rule."
  type        = string
  default     = "track"
}

variable "raw_mail_expiry_days" {
  description = "Days to keep the raw MIME message in S3 after SES stores it. The parsed application lives in the database; the raw mail is only for debugging a bad parse."
  type        = number
  default     = 30
}

variable "intake_endpoint_url" {
  description = "Public HTTPS URL of intake-service's /intake/aws-sns endpoint. Empty until the app is deployed: SNS can only confirm an HTTPS subscription against a running endpoint, so the subscription is created only when this is set."
  type        = string
  default     = ""
}

variable "tags" {
  type    = map(string)
  default = {}
}
