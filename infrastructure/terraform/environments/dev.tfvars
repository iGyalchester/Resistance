# Applied against ../stack with -var-file. The environment itself is
# passed separately (-var environment=dev), because it also picks the
# state key and CI needs it before reading this file.

aws_region = "us-east-1"

# From bootstrap/ outputs. Replace the placeholders before the first apply.
hosted_zone_id           = "ZCHANGEME"
ses_rule_set_name        = "resistance"
permissions_boundary_arn = "arn:aws:iam::CHANGEME:policy/resistance-ci-boundary"

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

# No image_tag here on purpose: the Deploy workflow writes the sha it pushed
# to /resistance/<env>/image-tag and Terraform reads it back from there.

# Optional integrations - secrets are ARNs of Secrets Manager secrets you
# create by hand, never the values themselves.
audit_url                    = ""
audit_token_secret_arn       = ""
anthropic_api_key_secret_arn = ""
