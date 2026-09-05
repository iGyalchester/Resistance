output "repository_urls" {
  description = "Service name => ECR repository URL."
  value       = { for name, repo in aws_ecr_repository.service : name => repo.repository_url }
}

output "image_tag_parameter_name" {
  description = "SSM parameter the Deploy workflow writes the pushed image tag to."
  value       = aws_ssm_parameter.image_tag.name
}

output "image_tag" {
  description = "The tag the task definitions should run: whatever Deploy last wrote to the parameter above."
  value       = aws_ssm_parameter.image_tag.value
}

output "service_ports" {
  description = "Service name => container port, so other modules size security groups from one source of truth."
  value       = var.services
}
