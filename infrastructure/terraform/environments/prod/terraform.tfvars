aws_region = "us-east-1"

# From bootstrap/ outputs. Replace the placeholders before the first apply.
hosted_zone_id    = "ZCHANGEME"
ses_rule_set_name = "resistance"

# prod owns the apex: users forward to track@<domain>, app at tracker.<domain>.
mail_domain = "example.com"
app_host    = "tracker.example.com"

# The one that costs money. Applied only via the Terraform workflow's manual
# "Run workflow -> prod" (never on push), after the Deploy workflow has pushed
# images to resistance-prod/*.
app_enabled = false

# --- app settings (only read while app_enabled = true) ----------------------
vpc_cidr = "10.30.0.0/16"

# prod keeps its data: deletion protection on, a final snapshot on destroy,
# a week of backups, a month before a scheduled key deletion takes effect.
# db_multi_az adds a standby in a second AZ (about 2x the instance cost);
# db_instance_class is the next knob when the micro is not enough.
db_instance_class        = "db.t4g.micro"
db_multi_az              = false
db_backup_retention_days = 7
db_deletion_protection   = true
db_skip_final_snapshot   = false
alb_deletion_protection  = true
log_retention_days       = 90
kms_deletion_window_days = 30

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
