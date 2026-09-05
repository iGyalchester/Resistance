# The Route53 hosted zone is account-level: both environments put records in
# it (dev.<domain> mail, tracker-dev.<domain>, and prod's apex/tracker names),
# so no single environment may own it. Create it here, or point at one you
# already have.

resource "aws_route53_zone" "this" {
  count = var.create_hosted_zone ? 1 : 0

  name    = var.domain_name
  comment = "Resistance job tracker - managed by infrastructure/terraform/bootstrap"
}

data "aws_route53_zone" "existing" {
  count = var.create_hosted_zone ? 0 : 1

  zone_id = var.hosted_zone_id
}

locals {
  hosted_zone_id = var.create_hosted_zone ? aws_route53_zone.this[0].zone_id : data.aws_route53_zone.existing[0].zone_id
  name_servers   = var.create_hosted_zone ? aws_route53_zone.this[0].name_servers : data.aws_route53_zone.existing[0].name_servers
}
