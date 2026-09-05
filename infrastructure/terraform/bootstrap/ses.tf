# SES receiving allows exactly one ACTIVE receipt rule set per account and
# region. If each environment created and activated its own, the last apply
# would silently switch mail delivery away from the other. So the set is
# created and activated once here; environments add rules to it for their
# own recipient (track@dev.<domain>, track@<domain>).

resource "aws_ses_receipt_rule_set" "this" {
  rule_set_name = var.ses_rule_set_name
}

resource "aws_ses_active_receipt_rule_set" "this" {
  rule_set_name = aws_ses_receipt_rule_set.this.rule_set_name
}
