# Inbound email for one environment:
#
#   user forwards email to track@<mail_domain>
#     -> MX record sends it to SES receiving
#     -> receipt rule archives the raw MIME in S3 and publishes the message
#        itself to an SNS topic
#     -> topic POSTs to intake-service's /intake/aws-sns (the subscription
#        itself is created by modules/app, which knows when the service is up)
#
# and the SES domain identity that lets mvc-service send OTP mail from the
# same domain. Everything here is free or pennies (S3 for a few emails), so
# it is applied even while the app itself is switched off.

data "aws_region" "current" {}
data "aws_caller_identity" "current" {}

locals {
  intake_address = "${var.intake_local_part}@${var.mail_domain}"
}

# --- the domain: verified for SES, signed with DKIM, MX pointed at SES -------

resource "aws_ses_domain_identity" "this" {
  domain = var.mail_domain
}

resource "aws_route53_record" "ses_verification" {
  zone_id = var.hosted_zone_id
  name    = "_amazonses.${var.mail_domain}"
  type    = "TXT"
  ttl     = 600
  records = [aws_ses_domain_identity.this.verification_token]
}

# Blocks until SES has seen the TXT record (usually a minute or two after
# Route53 publishes it), so a successful apply means mail can actually flow.
resource "aws_ses_domain_identity_verification" "this" {
  domain     = aws_ses_domain_identity.this.domain
  depends_on = [aws_route53_record.ses_verification]

  timeouts {
    create = "10m"
  }
}

# DKIM: receivers can check our OTP mail really came from this domain, which
# keeps it out of spam folders.
resource "aws_ses_domain_dkim" "this" {
  domain = aws_ses_domain_identity.this.domain
}

resource "aws_route53_record" "dkim" {
  count = 3

  zone_id = var.hosted_zone_id
  name    = "${aws_ses_domain_dkim.this.dkim_tokens[count.index]}._domainkey.${var.mail_domain}"
  type    = "CNAME"
  ttl     = 600
  records = ["${aws_ses_domain_dkim.this.dkim_tokens[count.index]}.dkim.amazonses.com"]
}

resource "aws_route53_record" "mx" {
  zone_id = var.hosted_zone_id
  name    = var.mail_domain
  type    = "MX"
  ttl     = 600
  records = ["10 inbound-smtp.${data.aws_region.current.name}.amazonaws.com"]
}

# --- where SES puts the raw message ------------------------------------------

resource "aws_s3_bucket" "raw_mail" {
  bucket = "${var.name}-raw-mail-${data.aws_caller_identity.current.account_id}"
  tags   = var.tags
}

resource "aws_s3_bucket_public_access_block" "raw_mail" {
  bucket                  = aws_s3_bucket.raw_mail.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "raw_mail" {
  bucket = aws_s3_bucket.raw_mail.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "raw_mail" {
  bucket = aws_s3_bucket.raw_mail.id

  rule {
    id     = "expire-raw-mail"
    status = "Enabled"
    filter {}
    expiration {
      days = var.raw_mail_expiry_days
    }
  }
}

data "aws_iam_policy_document" "raw_mail" {
  statement {
    sid     = "AllowSesPuts"
    effect  = "Allow"
    actions = ["s3:PutObject"]
    principals {
      type        = "Service"
      identifiers = ["ses.amazonaws.com"]
    }
    resources = ["${aws_s3_bucket.raw_mail.arn}/*"]
    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
  }
}

resource "aws_s3_bucket_policy" "raw_mail" {
  bucket     = aws_s3_bucket.raw_mail.id
  policy     = data.aws_iam_policy_document.raw_mail.json
  depends_on = [aws_s3_bucket_public_access_block.raw_mail]
}

# --- the topic SES notifies, and the rule that ties it together ---------------

resource "aws_sns_topic" "intake" {
  name = "${var.name}-intake"
  tags = var.tags
}

data "aws_iam_policy_document" "intake_topic" {
  statement {
    sid     = "AllowSesPublish"
    effect  = "Allow"
    actions = ["sns:Publish"]
    principals {
      type        = "Service"
      identifiers = ["ses.amazonaws.com"]
    }
    resources = [aws_sns_topic.intake.arn]
    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
  }
}

resource "aws_sns_topic_policy" "intake" {
  arn    = aws_sns_topic.intake.arn
  policy = data.aws_iam_policy_document.intake_topic.json
}

resource "aws_ses_receipt_rule" "intake" {
  name          = "${var.name}-forward-to-tracker"
  rule_set_name = var.rule_set_name
  # Every local part at this domain, not only track@: SES matches a
  # plus-tagged address only when that exact address is listed, so the
  # personal track+<alias>@ addresses the dashboard hands out would never
  # match a rule scoped to the bare address. Mail sent anywhere else at the
  # domain (a reply to the OTP sender, say) reaches intake and is dropped
  # for carrying no alias (intake.require-alias=true in qa).
  recipients   = [var.mail_domain]
  enabled      = true
  scan_enabled = true

  # One action, one notification. The object is the message; the
  # notification names it (receipt.action.bucketName/objectKey) and
  # intake-service reads it back through its task role.
  #
  # This replaced an sns_action, which embeds the base64 MIME directly in the
  # notification. That worked, but SNS refuses a notification over 150 KB and
  # SES bounces the mail outright - so a confirmation email with a logo
  # attached was rejected rather than delivered. Reading from S3 has no
  # ceiling below SES's own 40 MB.
  #
  # Note there is exactly one action, not both. Two actions publish two
  # notifications for the same mail, and whichever arrived first would decide
  # how it parsed.
  s3_action {
    bucket_name = aws_s3_bucket.raw_mail.bucket
    topic_arn   = aws_sns_topic.intake.arn
    position    = 1
  }

  depends_on = [
    aws_s3_bucket_policy.raw_mail,
    aws_sns_topic_policy.intake,
    aws_ses_domain_identity_verification.this,
  ]
}

# The HTTPS subscription to this topic lives in modules/app, not here: SNS
# confirms it by calling the endpoint, so it can only be created once the
# service answering that URL is actually running. See the comment there.
