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
      │  receipt rule: archive the raw MIME in S3, publish the message to SNS
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
- The rule has exactly **one** action: an `s3_action` with a `topic_arn`.
  It writes the raw MIME to the bucket and publishes a notification naming
  the object (`receipt.action.bucketName` / `objectKey`). intake-service
  reads the message back from there with its task role.
- Why not an `sns_action`, which embeds the base64 MIME in the notification
  directly? Because SNS refuses a notification larger than **150 KB** and
  SES bounces the mail rather than delivering it - so a confirmation email
  with a logo attached is rejected outright. Reading from S3 has no ceiling
  below SES's own 40 MB. intake-service still prefers an inline `content`
  field when one is present, which keeps the local and test paths working
  without a bucket.
- One action, not both, on purpose: two actions publish two notifications
  for the same mail, and whichever arrived first would decide how it parsed.
- intake-service's permissions for this are a **task role** (distinct from
  the ECS execution role) allowing `s3:GetObject` on that bucket and nothing
  else. No `ListBucket` - the object key always arrives in the notification.
  mvc-service gets no task role at all. Note that the CI permissions
  boundary must allow `s3:GetObject` too, or the role's own policy is capped
  away and mail silently arrives without a body; `bootstrap/oidc.tf` has it,
  and bootstrap has to be re-applied by hand for it to take effect.
- intake-service verifies every SNS message's signature against the AWS
  signing certificate (`intake.aws.verify-signature=true` in `qa`), so a
  forged POST to the endpoint is rejected even if the URL leaks.
- SES delivers the subscription confirmation to the running service, which
  confirms it automatically; look for "Confirmed SNS subscription" in the
  intake-service logs.

## Personal intake aliases

Account routing is based on the recipient address, not the sender: each
account gets a random alias and its personal address `track+<alias>@domain`.
The receipt rule's recipient condition is the **whole mail domain**, not one
address. SES matches a plus-tagged address only when that exact address is
listed, so a rule scoped to `track@domain` would never see the personal
`track+<alias>@domain` addresses the dashboard hands out; a domain condition
catches every one of them. Mail sent to any other address at the domain (a
reply to the OTP sender, say) reaches intake too and is dropped for carrying
no alias.
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

Note that SES availability is **not** wired into the health endpoint. Boot
would happily do that for you - `spring.mail.host` is set, so it registers a
`MailHealthIndicator` - but the ALB probes health every 30 seconds per task
and requires a 200, which would turn any SES hiccup into every task being
drained and replaced at once. `management.health.mail.enabled=false` keeps
it out. If SES is down you will see warnings from the OTP mailer, and users
cannot receive codes; the rest of the tracker keeps working.

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
