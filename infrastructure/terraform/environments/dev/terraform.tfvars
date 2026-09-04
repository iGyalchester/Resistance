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
