output "repository_urls" {
  description = "Service name => ECR repository URL."
  value       = { for name, repo in aws_ecr_repository.service : name => repo.repository_url }
}

output "service_ports" {
  description = "Service name => container port, so other modules size security groups from one source of truth."
  value       = var.services
}
