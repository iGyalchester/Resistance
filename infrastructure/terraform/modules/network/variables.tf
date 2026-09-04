variable "name" {
  type = string
}

variable "vpc_cidr" {
  type    = string
  default = "10.20.0.0/16"
}

variable "azs" {
  description = "Two availability zones: the ALB requires subnets in at least two."
  type        = list(string)
}

variable "service_ports" {
  description = "Container ports the ALB may reach on the tasks."
  type        = list(number)
}

variable "tags" {
  type    = map(string)
  default = {}
}
