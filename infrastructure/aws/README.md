# AWS notes for email intake

The AWS resources themselves are created by Terraform - see
[`../terraform/README.md`](../terraform/README.md). This page keeps the
facts about *how* intake works on AWS that are true regardless of tooling.

## The chain

```
user forwards email
      │
      ▼
SES receiving (MX record → inbound-smtp.<region>.amazonaws.com)
      │  receipt rule: store raw MIME in S3 + notify SNS
      ▼
SNS topic ──HTTPS subscription──> POST https://<app host>/intake/aws-sns
                                        │ (SnsIntakeController confirms the
                                        ▼  subscription, unwraps SES JSON)
                                  IntakeService (same flow as webhook/IMAP)
```

- SES receiving is only offered in some regions (us-east-1, us-west-2,
  eu-west-1 among them). The whole deployment uses one region because of it.
- SES allows exactly one *active* receipt rule set per account and region.
  That is why the rule set is created in `bootstrap/` and each environment
  only adds a rule for its own recipient.
- intake-service verifies every SNS message's signature against the AWS
  signing certificate (`intake.aws.verify-signature=true` in `qa`), so a
  forged POST to the endpoint is rejected even if the URL leaks.
- SES delivers the subscription confirmation to the running service, which
  confirms it automatically; look for "Confirmed SNS subscription" in the
  intake-service logs.

## Personal intake aliases

Account routing is based on the recipient address, not the sender: each
account gets a random alias and its personal address `track+<alias>@domain`.
SES receipt rules match the bare recipient (`track@domain`) and deliver
plus-tagged variants to the same rule, so no extra AWS setup is needed.
In `qa`, `intake.require-alias=true` (mail to the bare address provisions
nothing) and `INTAKE_ADDRESS=track@<domain>` is injected into mvc-service so
dashboards render each user's personal address.

## OTP email through SES

No code changes needed: mvc-service's `qa` mail settings point at SES SMTP
(`email-smtp.<region>.amazonaws.com`, port 587, STARTTLS). SES SMTP
credentials are *derived from an IAM access key*; the Terraform app module
creates a send-only IAM user and derives them, so you never do it by hand.
Until the account has SES production access, mail is only delivered to
addresses you have verified in the SES console.

## PII encryption

QA/production encrypt PII columns (currently `user_account.phone`; annotate
more fields with `@Convert(converter = EncryptedStringConverter.class)` to
extend) with AES-256-GCM. The key is generated once by Terraform, stored as
an SSM SecureString encrypted under a KMS customer-managed key, and injected
as `TRACKER_ENC_KEY`. Values are written as `enc:v1:<base64(iv||ciphertext)>`;
rows written before encryption was enabled still read back (plaintext
passthrough on decrypt). Losing the key makes encrypted values
unrecoverable, which is why the Terraform state bucket is versioned.

## QA environment checklist

QA runs with `--spring.profiles.active=qa`, which strips every local default:
the services **fail at startup** until these exist and are injected as
environment variables. Terraform's app module sets all of them on the ECS
task definitions.

| Variable | Source | Used by |
|---|---|---|
| `DB_HOST`, `DB_PORT`, `DB_USERNAME`, `DB_PASSWORD` | RDS MySQL; credentials from the RDS-managed secret | intake, mvc |
| `INTAKE_WEBHOOK_TOKEN` | SSM SecureString (generated) | intake |
| `INTAKE_AWS_TOPIC_ARN` | the `email-intake` module's topic | intake |
| `SMTP_HOST`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `OTP_FROM_ADDRESS` | SES SMTP endpoint + derived credentials; `otp@<mail domain>` | mvc |
| `TRACKER_ENC_KEY` | SSM SecureString (generated, KMS-encrypted) | intake, mvc |
| `INTAKE_ADDRESS` | `track@<mail domain>` | mvc |
| `TRACKER_AUDIT_URL`, `TRACKER_AUDIT_TOKEN` *(optional)* | AuditFlow ingestion; token from Secrets Manager | intake, mvc |
| `ANTHROPIC_API_KEY` *(optional)* | Secrets Manager, for Claude-backed parsing | intake |

Dev needs none of this: no profile flag (dev is the default), local MySQL
from docker-compose, logged OTP codes, open webhook, plaintext PII.

## Alternatives

- **EventBridge**: route S3 `ObjectCreated` events from the raw-mail bucket
  through an EventBridge rule to an API Destination pointing at the same app.
  More moving parts for the same outcome; useful if you already standardize on
  EventBridge for integration events.
- **No AWS at all**: the plain `POST /intake/email` webhook works with any
  inbound-parse provider (Mailgun/SendGrid/Postmark), and the IMAP poller
  (`intake.imap.enabled=true`) works against any ordinary mailbox.
