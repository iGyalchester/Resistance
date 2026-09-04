aws_region = "us-east-1"

# From bootstrap/ outputs. Replace the placeholders before the first apply.
hosted_zone_id    = "ZCHANGEME"
ses_rule_set_name = "resistance"

# dev lives on a subdomain so it can coexist with prod in one account:
# users forward to track@dev.<domain>, the app answers at tracker-dev.<domain>.
mail_domain = "dev.example.com"
app_host    = "tracker-dev.example.com"

# Flip to true for a demo day, back to false after (see README cost table).
app_enabled = false

# --- app settings (only read while app_enabled = true) ----------------------
vpc_cidr = "10.20.0.0/16"

# dev is disposable: no protection, no final snapshot, short retention.
db_instance_class        = "db.t4g.micro"
db_multi_az              = false
db_backup_retention_days = 1
db_deletion_protection   = false
db_skip_final_snapshot   = true
alb_deletion_protection  = false
log_retention_days       = 14
kms_deletion_window_days = 7

task_cpu      = 256
task_memory   = 1024
desired_count = 1
image_tag     = "latest"

# Optional integrations - secrets are ARNs of Secrets Manager secrets you
# create by hand, never the values themselves.
audit_url                    = ""
audit_token_secret_arn       = ""
anthropic_api_key_secret_arn = ""
