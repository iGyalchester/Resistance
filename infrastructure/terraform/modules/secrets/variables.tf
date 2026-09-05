variable "name" {
  type = string
}

variable "environment" {
  description = "dev or prod; namespaces the SSM parameters (/resistance/<environment>/...)."
  type        = string
}

variable "mail_domain" {
  description = "SES identity the SMTP user may send from."
  type        = string
}

variable "kms_deletion_window_days" {
  description = "Grace period before a scheduled key deletion takes effect. Longer for prod: a deleted key means encrypted fields are gone for good."
  type        = number
  default     = 7
}

variable "tags" {
  type    = map(string)
  default = {}
}
