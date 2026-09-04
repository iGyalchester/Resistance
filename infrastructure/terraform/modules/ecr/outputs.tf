output "repository_urls" {
  description = "Service name => ECR repository URL."
  value       = { for name, repo in aws_ecr_repository.service : name => repo.repository_url }
}
