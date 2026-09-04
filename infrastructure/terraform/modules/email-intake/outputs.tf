output "intake_address" {
  description = "The bare intake address; mvc-service shows each user track+<alias>@ under it (INTAKE_ADDRESS)."
  value       = local.intake_address
}

output "topic_arn" {
  description = "intake-service's INTAKE_AWS_TOPIC_ARN."
  value       = aws_sns_topic.intake.arn
}

output "raw_mail_bucket_name" {
  value = aws_s3_bucket.raw_mail.bucket
}

output "mail_domain" {
  value = aws_ses_domain_identity.this.domain
}
