variable "aws_region" {
  description = "Must match bootstrap's region (SES receiving is regional)."
  type        = string
  default     = "us-east-1"
}

variable "hosted_zone_id" {
  description = "From `terraform output hosted_zone_id` in bootstrap/."
  type        = string
}

variable "ses_rule_set_name" {
  description = "From `terraform output ses_rule_set_name` in bootstrap/."
  type        = string
  default     = "resistance"
}

variable "mail_domain" {
  description = "Domain this environment receives mail for (users forward to track@<mail_domain>)."
  type        = string
}

variable "app_host" {
  description = "Hostname the tracker is served at, e.g. tracker.example.com. Must be inside the hosted zone."
  type        = string
}

variable "app_enabled" {
  description = "Provision the network, database, secrets and Fargate services. Off by default: apply creates only the free pieces (ECR repositories, mail routing). Push images first (Deploy workflow), then flip - the ALB, Fargate and RDS bill from the moment this applies."
  type        = bool
  default     = false
}
