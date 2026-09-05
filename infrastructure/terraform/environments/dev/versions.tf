terraform {
  required_version = ">= 1.10.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.70"
    }
  }

  # Values supplied at `terraform init -backend-config=backend.hcl` (see
  # backend.hcl.example) - the bucket only exists after bootstrap/ has been
  # applied, so it cannot be hardcoded here for every fork/account.
  backend "s3" {}
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "resistance"
      Environment = "dev"
      ManagedBy   = "terraform"
    }
  }
}
