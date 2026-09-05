variable "name" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "public_subnet_ids" {
  type = list(string)
}

variable "alb_security_group_id" {
  type = string
}

variable "tasks_security_group_id" {
  type = string
}

variable "hosted_zone_id" {
  type = string
}

variable "app_host" {
  description = "Hostname the ALB answers on, e.g. tracker.example.com; the certificate and the DNS record are created for it."
  type        = string
}

variable "services" {
  description = "Service name => container port. Must match modules/ecr."
  type        = map(number)
  default = {
    mvc-service    = 8085
    intake-service = 8087
  }
}

variable "repository_urls" {
  type = map(string)
}

variable "image_tag" {
  description = "Tag to run, from module.ecr.image_tag (the git sha the Deploy workflow last pushed). Never a moving tag: a task definition has to name specific bytes for a rollback to mean anything."
  type        = string
}

variable "desired_count" {
  description = "Tasks per service. mvc-service sessions are sticky to a task, so more than one is safe."
  type        = number
  default     = 1
}

variable "task_cpu" {
  type    = number
  default = 256
}

variable "task_memory" {
  description = "MiB. Two Spring Boot apps on a 0.25 vCPU task want 1 GiB each; 512 works but restarts under load."
  type        = number
  default     = 1024
}

variable "log_retention_days" {
  type    = number
  default = 14
}

variable "alb_deletion_protection" {
  type    = bool
  default = false
}

variable "db_endpoint" {
  type = string
}

variable "db_port" {
  type = number
}

variable "db_secret_arn" {
  description = "RDS-managed master credentials (JSON with username/password)."
  type        = string
}

variable "kms_key_arn" {
  description = "Key the SSM parameters are encrypted with; the execution role needs Decrypt on it."
  type        = string
}

variable "parameter_arns" {
  description = "From modules/secrets: tracker_enc_key, intake_webhook_token, smtp_username, smtp_password."
  type        = map(string)
}

variable "smtp_host" {
  type = string
}

variable "otp_from_address" {
  type = string
}

variable "intake_address" {
  type = string
}

variable "intake_topic_arn" {
  type = string
}

variable "audit_url" {
  description = "AuditFlow ingestion URL (TRACKER_AUDIT_URL). Empty = auditing off."
  type        = string
  default     = ""
}

variable "audit_token_secret_arn" {
  description = "Secrets Manager secret (plain string) holding the AuditFlow ingestion token. A credential, so never a plain variable."
  type        = string
  default     = ""
}

variable "anthropic_api_key_secret_arn" {
  description = "Secrets Manager secret (plain string) holding the Anthropic API key for Claude-backed parsing. Empty = heuristics only."
  type        = string
  default     = ""
}

variable "tags" {
  type    = map(string)
  default = {}
}
