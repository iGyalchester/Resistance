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

## Alternatives

- **EventBridge**: route S3 `ObjectCreated` events from the raw-mail bucket
  through an EventBridge rule to an API Destination pointing at the same app.
  More moving parts for the same outcome; useful if you already standardize on
  EventBridge for integration events.
- **No AWS at all**: the plain `POST /intake/email` webhook works with any
  inbound-parse provider (Mailgun/SendGrid/Postmark), and the IMAP poller
  (`intake.imap.enabled=true`) works against any ordinary mailbox.
