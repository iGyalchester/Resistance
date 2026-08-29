# AWS wiring for email intake

The tracker's inbound-email trigger on AWS is **SES inbound receiving → SNS →
HTTPS subscription** into `intake-service`'s `/intake/aws-sns` endpoint.
(EventBridge cannot receive email itself; see *Alternatives* below for where it
can sit in the chain.)

```
user forwards email
      │
      ▼
SES receiving (MX record → inbound-smtp.<region>.amazonaws.com)
      │  receipt rule: store raw MIME in S3 + notify SNS
      ▼
SNS topic ──HTTPS subscription──> POST https://<your-host>/intake/aws-sns
                                        │ (SnsIntakeController confirms the
                                        ▼  subscription, unwraps SES JSON)
                                  IntakeService (same flow as webhook/IMAP)
```

## Deploy

1. Verify your domain in SES and add the MX record
   (`10 inbound-smtp.<region>.amazonaws.com`). SES receiving is only offered in
   certain regions (e.g. us-east-1, us-west-2, eu-west-1).
2. Deploy the stack:

   ```bash
   aws cloudformation deploy \
     --template-file infrastructure/aws/ses-intake.yaml \
     --stack-name resistance-intake \
     --parameter-overrides \
       IntakeRecipient=track@yourdomain.com \
       IntakeEndpointUrl=https://tracker.yourdomain.com/intake/aws-sns
   ```

3. Activate the rule set (one-time; SES allows a single active set per account):

   ```bash
   aws ses set-active-receipt-rule-set --rule-set-name resistance-intake
   ```

4. Configure intake-service with the stack's `TopicArn` output:

   ```properties
   intake.aws.topic-arn=arn:aws:sns:...:resistance-intake
   ```

   SNS delivers the subscription-confirmation to the running service, which
   confirms it automatically — check the intake-service logs for
   "Confirmed SNS subscription".

## OTP email through SES

No code changes needed — point mvc-service's mail settings at SES SMTP:

```properties
spring.mail.host=email-smtp.<region>.amazonaws.com
spring.mail.port=587
spring.mail.username=<ses-smtp-username>
spring.mail.password=<ses-smtp-password>
spring.mail.properties.mail.smtp.starttls.enable=true
tracker.otp.from=no-reply@yourdomain.com
```

## PII encryption with KMS keys

QA/production encrypt PII columns (currently `user_account.phone`; annotate
more fields with `@Convert(converter = EncryptedStringConverter.class)` to
extend) using AES-256-GCM with a **KMS-managed data key**. The application
never creates key material — you generate it once with KMS and inject it:

```bash
# one-time: create the customer-managed key
aws kms create-key --description "resistance-tracker field encryption"

# generate a 256-bit data key under it
aws kms generate-data-key --key-id <key-id> --key-spec AES_256 \
  --query Plaintext --output text          # base64 - this is TRACKER_ENC_KEY

# store it in Secrets Manager rather than plain env files
aws secretsmanager create-secret --name resistance/tracker-enc-key \
  --secret-string '<base64-key>'
```

Inject the secret as `TRACKER_ENC_KEY` into intake-service and mvc-service
(both read it as `tracker.encryption.key` in the qa profile). Values are
written as `enc:v1:<base64(iv||ciphertext)>`; rows written before encryption
was enabled still read back (plaintext passthrough on decrypt). Losing the
key makes encrypted values unrecoverable — keep the KMS `CiphertextBlob`
output of `generate-data-key` if you want to re-derive it via `kms decrypt`.

## QA environment checklist

QA runs with `--spring.profiles.active=qa`, which strips every local default:
the services **fail at startup** until these exist in AWS and are injected
as environment variables:

| Variable | AWS resource | Used by |
|---|---|---|
| `DB_HOST`, `DB_USERNAME`, `DB_PASSWORD` | RDS/Aurora MySQL (run `infrastructure/config/db-init/` against it) | intake, mvc |
| `INTAKE_WEBHOOK_TOKEN` | Secrets Manager secret | intake |
| `INTAKE_AWS_TOPIC_ARN` | `ses-intake.yaml` stack output | intake |
| `SMTP_HOST`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `OTP_FROM_ADDRESS` | SES SMTP credentials + verified sender | mvc |
| `TRACKER_ENC_KEY` | KMS data key (above) | intake, mvc |

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
