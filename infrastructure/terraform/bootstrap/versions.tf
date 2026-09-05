terraform {
  required_version = ">= 1.10.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.70"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }

  # Deliberately no backend block: this module creates the remote state
  # bucket that every environment stores its state in, so it cannot depend
  # on that bucket existing yet. Its own state stays local - see README.md.
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project   = "resistance"
      ManagedBy = "terraform"
      Layer     = "bootstrap"
    }
  }
}
