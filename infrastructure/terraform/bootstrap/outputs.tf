output "state_bucket_name" {
  description = "Set as the TF_STATE_BUCKET repository variable; each environment's backend.hcl points here."
  value       = aws_s3_bucket.terraform_state.bucket
}

output "state_bucket_region" {
  description = "Set as the AWS_REGION repository variable."
  value       = var.aws_region
}

output "github_actions_role_arn" {
  description = "Set as the AWS_ROLE_ARN repository variable."
  value       = aws_iam_role.github_actions_deploy.arn
}

output "hosted_zone_id" {
  description = "Put in each environment's terraform.tfvars as hosted_zone_id."
  value       = local.hosted_zone_id
}

output "hosted_zone_name_servers" {
  description = "If the zone was created here, point your registrar's NS records at these."
  value       = local.name_servers
}

output "ci_boundary_policy_arn" {
  description = "Put in each environment's terraform.tfvars as permissions_boundary_arn; CI cannot create an IAM principal without it."
  value       = aws_iam_policy.ci_boundary.arn
}

output "ses_rule_set_name" {
  description = "Put in each environment's terraform.tfvars as ses_rule_set_name."
  value       = aws_ses_receipt_rule_set.this.rule_set_name
}
