output "ecr_repository_urls" {
  value = module.ecr.repository_urls
}

output "intake_address" {
  value = module.email_intake.intake_address
}

output "intake_topic_arn" {
  value = module.email_intake.topic_arn
}

output "raw_mail_bucket_name" {
  value = module.email_intake.raw_mail_bucket_name
}

# null while app_enabled = false
output "app_url" {
  value = one(module.app[*].app_url)
}

output "ecs_cluster_name" {
  value = one(module.app[*].cluster_name)
}

output "db_endpoint" {
  value = one(module.database[*].endpoint)
}

output "db_master_user_secret_arn" {
  value = one(module.database[*].master_user_secret_arn)
}
