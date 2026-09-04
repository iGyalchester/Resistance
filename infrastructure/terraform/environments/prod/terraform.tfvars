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
