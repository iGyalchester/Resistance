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

variable "permissions_boundary_arn" {
  description = "From `terraform output ci_boundary_policy_arn` in bootstrap/. Every IAM principal this environment creates carries it, and the CI role may not create one without it."
  type        = string
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

# --- everything below only matters while app_enabled = true ----------------

variable "azs" {
  type    = list(string)
  default = ["us-east-1a", "us-east-1b"]
}

variable "vpc_cidr" {
  type = string
}

variable "otp_local_part" {
  description = "OTP mail is sent from <otp_local_part>@<mail_domain>."
  type        = string
  default     = "otp"
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "db_multi_az" {
  type    = bool
  default = false
}

variable "db_backup_retention_days" {
  type    = number
  default = 1
}

variable "db_deletion_protection" {
  type    = bool
  default = false
}

variable "db_skip_final_snapshot" {
  type    = bool
  default = true
}

variable "alb_deletion_protection" {
  type    = bool
  default = false
}

variable "log_retention_days" {
  type    = number
  default = 14
}

variable "kms_deletion_window_days" {
  type    = number
  default = 7
}

variable "task_cpu" {
  type    = number
  default = 256
}

variable "task_memory" {
  type    = number
  default = 1024
}

variable "desired_count" {
  type    = number
  default = 1
}

variable "image_tag" {
  description = "Image tag the services run. The Deploy workflow pushes :latest plus the git SHA."
  type        = string
  default     = "latest"
}

variable "audit_url" {
  description = "AuditFlow ingestion-service URL. Empty = audit events are not emitted."
  type        = string
  default     = ""
}

variable "audit_token_secret_arn" {
  description = "Secrets Manager secret holding the AuditFlow ingestion token. Create it by hand; never put the token in tfvars."
  type        = string
  default     = ""
}

variable "anthropic_api_key_secret_arn" {
  description = "Secrets Manager secret holding the Anthropic API key. Empty = heuristic parsing only."
  type        = string
  default     = ""
}
