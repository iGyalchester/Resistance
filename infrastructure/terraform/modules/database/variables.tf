variable "name" {
  type = string
}

variable "private_subnet_ids" {
  type = list(string)
}

variable "security_group_id" {
  type = string
}

variable "kms_key_arn" {
  description = "Encrypts the storage and snapshots."
  type        = string
}

variable "instance_class" {
  description = "db.t4g.micro is free-tier eligible for a new account's first year and plenty for a personal tracker."
  type        = string
  default     = "db.t4g.micro"
}

variable "allocated_storage_gb" {
  type    = number
  default = 20
}

variable "multi_az" {
  description = "A standby in a second AZ with automatic failover. Roughly doubles the instance cost."
  type        = bool
  default     = false
}

variable "backup_retention_days" {
  type    = number
  default = 1
}

variable "deletion_protection" {
  type    = bool
  default = false
}

variable "skip_final_snapshot" {
  description = "false keeps a final snapshot when the instance is destroyed (prod)."
  type        = bool
  default     = true
}

variable "tags" {
  type    = map(string)
  default = {}
}
