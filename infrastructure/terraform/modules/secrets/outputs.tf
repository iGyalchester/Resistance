output "kms_key_arn" {
  value = aws_kms_key.this.arn
}

output "kms_key_id" {
  value = aws_kms_key.this.key_id
}

output "parameter_arns" {
  description = "Logical name => SSM parameter ARN, for the task definitions' secrets."
  value = {
    tracker_enc_key      = aws_ssm_parameter.field_encryption_key.arn
    intake_webhook_token = aws_ssm_parameter.webhook_token.arn
    smtp_username        = aws_ssm_parameter.smtp_username.arn
    smtp_password        = aws_ssm_parameter.smtp_password.arn
  }
}
