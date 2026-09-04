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
