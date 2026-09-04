output "endpoint" {
  description = "Hostname only (DB_HOST)."
  value       = aws_db_instance.this.address
}

output "port" {
  value = aws_db_instance.this.port
}

output "master_user_secret_arn" {
  description = "Secrets Manager secret RDS manages; JSON with username and password keys."
  value       = aws_db_instance.this.master_user_secret[0].secret_arn
}

output "db_name" {
  value = aws_db_instance.this.db_name
}
