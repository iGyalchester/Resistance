variable "name" {
  description = "Environment-scoped prefix, e.g. resistance-dev."
  type        = string
}

variable "environment" {
  description = "dev or prod; namespaces the image-tag parameter (/resistance/<environment>/image-tag), matching modules/secrets."
  type        = string
}

variable "services" {
  description = "Deployed services (name => container port). Only the two the tracker needs on AWS; rest-api-service and the course demos are never deployed."
  type        = map(number)
  default = {
    mvc-service    = 8085
    intake-service = 8087
  }
}

variable "tags" {
  type    = map(string)
  default = {}
}
