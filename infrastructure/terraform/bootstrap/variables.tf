variable "aws_region" {
  description = "Region for the state bucket, the OIDC role and SES receiving. SES receiving is only offered in some regions (us-east-1, us-west-2, eu-west-1 among them) - every environment must use the same region as this."
  type        = string
  default     = "us-east-1"
}

variable "state_bucket_name" {
  description = "Globally-unique S3 bucket name for Terraform remote state, e.g. resistance-tfstate-<account id>."
  type        = string
}

variable "github_org" {
  description = "GitHub user/organization that owns the repository allowed to assume the CI deploy role."
  type        = string
  default     = "iGyalchester"
}

variable "github_repos" {
  description = "Repository names (under github_org) whose GitHub Actions workflows may assume the CI deploy role."
  type        = list(string)
  default     = ["Resistance"]
}

variable "domain_name" {
  description = "The apex domain the tracker lives under, e.g. example.com. prod receives mail at track@<domain> and serves https://tracker.<domain>; dev uses dev.<domain> / tracker-dev.<domain>."
  type        = string
}

variable "create_hosted_zone" {
  description = "Create the Route53 hosted zone for domain_name (true), or reuse an existing one named by hosted_zone_id (false). A zone costs about $0.50/month."
  type        = bool
  default     = true
}

variable "hosted_zone_id" {
  description = "Existing Route53 hosted zone id for domain_name. Only read when create_hosted_zone = false."
  type        = string
  default     = ""
}

variable "ses_rule_set_name" {
  description = "Name of the SES receipt rule set. SES allows exactly ONE active rule set per account and region, which is why it lives here and not in an environment: dev and prod each add their own rule to it."
  type        = string
  default     = "resistance"
}
variable "create_oidc_provider" {
  description = "Create the account-wide GitHub OIDC provider (true) or read the one another bootstrap already created (false). AWS allows exactly one provider per URL per account, so exactly ONE of the two bootstraps in this account may set this to true. auditflow-infrastructure owns it; Resistance sets false."
  type        = bool
  default     = false
}
