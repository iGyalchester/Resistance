# Terraform for the tracker

Everything the `qa` profile's fail-fast environment variables need, created
by code instead of by hand, applied from GitHub Actions with no AWS keys
stored anywhere. Read `docs/TECH-GUIDE.md` ("The AWS pieces") first if
Terraform, ECS or OIDC are new words.

```
bootstrap/          applied ONCE by hand with your own credentials: the state bucket,
                    the CI role and the permissions boundary that caps every IAM
                    principal CI creates, and the two account-level things AWS allows
                    only one of (the Route53 hosted zone, the active SES receipt rule set)
modules/
  ecr/              one image repository per deployed service
  email-intake/     SES receiving for one mail domain: verification, DKIM, MX,
                    raw-mail archive bucket, SNS topic, a receipt rule that
                    publishes each message (body included) to SNS
  network/          VPC: public subnets (ALB + tasks), private subnets (database),
                    three security groups; deliberately no NAT gateway
  secrets/          KMS key; generated field-encryption key, webhook token and
                    SES SMTP credentials as KMS-encrypted SSM parameters
  database/         MySQL on RDS, credentials managed by RDS in Secrets Manager
  app/              ECS Fargate cluster, two services, HTTPS ALB with a certificate
                    and DNS record, /intake/* routed to intake-service, and the
                    topic's HTTPS subscription (it can only be confirmed once the
                    service answering it is up, so it belongs with the service)
environments/
  dev/              track@dev.<domain>, https://tracker-dev.<domain>
  prod/             track@<domain>,     https://tracker.<domain>
```

Two environments share one AWS account and one hosted zone, and differ
only in `terraform.tfvars`. `dev` is applied automatically on every push to
`main`; `prod` is applied only when you run the Terraform workflow by hand
and pick it.

## What it costs

| State | Monthly |
|---|---|
| Both environments with `app_enabled = false` (the default) | ≈ $0.50 for the hosted zone, plus cents of S3 for raw mail |
| One environment with `app_enabled = true` | ≈ $50: ALB ≈ $16, two Fargate tasks (0.25 vCPU, 1 GiB) ≈ $24, `db.t4g.micro` MySQL ≈ $12 (free-tier eligible for a new account's first year), KMS key $1 |
| prod on all the time, dev flipped on for a demo day | ≈ $50 + about $1.50 per dev day |
| prod with `db_multi_az = true` | + ≈ $12 for the standby |

`app_enabled` is the only switch that costs real money. ECR repositories,
SES identities, SNS topics and receipt rules are free while idle, so the
always-on set can stay applied indefinitely.

## Getting started (once)

1. **A domain.** Either let bootstrap create the Route53 zone
   (`create_hosted_zone = true`, then point your registrar's nameservers at
   the `hosted_zone_name_servers` output) or pass an existing zone's id.
2. **Bootstrap**, with your own admin credentials in the shell:

   ```bash
   cd infrastructure/terraform/bootstrap
   terraform init
   terraform apply \
     -var="state_bucket_name=resistance-tfstate-<your account id>" \
     -var="domain_name=<your domain>"
   ```

   Keep `bootstrap/terraform.tfstate` somewhere safe (it is gitignored); it
   is the only state not stored in S3, because it creates the S3 bucket.
3. **Repository variables** (Settings → Secrets and variables → Actions →
   Variables), from `terraform output`:

   | Variable | From |
   |---|---|
   | `AWS_REGION` | `state_bucket_region` |
   | `AWS_ROLE_ARN` | `github_actions_role_arn` |
   | `TF_STATE_BUCKET` | `state_bucket_name` |

   Also create two GitHub Environments named `dev` and `prod` (Settings →
   Environments); the workflow binds each apply job to one so the OIDC
   token carries the environment name.
4. **Fill in the tfvars.** In `environments/dev/terraform.tfvars` and
   `environments/prod/terraform.tfvars` replace `ZCHANGEME` with the
   `hosted_zone_id` output, the `permissions_boundary_arn` placeholder with
   the `ci_boundary_policy_arn` output, and `example.com` with your domain.
   Commit. The boundary is not optional: the CI role is only allowed to
   create IAM principals that carry it, so an apply with the placeholder
   still in place fails on `iam:CreateRole`.
5. **Merge to `main`.** The Terraform workflow applies `dev`: ECR
   repositories, SES verification and DKIM records, the MX record for
   `dev.<domain>`, the raw-mail bucket, the SNS topic and the receipt rule.
   The apply waits until SES has seen the verification record (a minute or
   two), so a green run means mail can already be received.
6. **SES sandbox.** New accounts can only *send* to verified addresses.
   Request production access once (SES console → Account dashboard); it is
   free and account-wide, so it covers both environments.

`prod`'s always-on set is applied the same way from the Actions tab:
Terraform → Run workflow → `prod`.

## Turning the app on

Order matters: images first, then compute. Fargate pulls the image on
start; with an empty repository every task crash-loops while the ALB and
RDS bill by the hour.

1. **Deploy workflow** (Actions → Deploy → Run workflow → `dev`). It builds
   the two jars once, stamps one image per service from
   `infrastructure/docker/Dockerfile.runtime`, pushes `:latest` and `:<git sha>`
   to the environment's ECR repositories, and reports that there is no
   cluster to roll yet.
2. Set `app_enabled = true` in `environments/dev/terraform.tfvars`, merge
   to `main`. The apply creates the VPC, the database (about ten minutes
   the first time), the secrets, the certificate (validated by DNS records
   in the hosted zone, a few minutes), the load balancer, the two services,
   and last the SNS subscription to
   `https://tracker-dev.<domain>/intake/aws-sns`. The subscription is
   deliberately last: SNS confirms it by calling the endpoint, so the ECS
   service is created with `wait_for_steady_state` and the subscription
   waits on both it and the DNS record.
3. Check, in order:
   - `curl https://tracker-dev.<domain>/actuator/health` → `{"status":"UP"}`
     (the ALB uses the same path; ECS shows both services *healthy*).
   - `curl https://tracker-dev.<domain>/intake/actuator/health` is a 404 on
     purpose: only `/intake/*` reaches intake-service and it has no such
     path. Its health is what the ALB target group reports.
   - intake-service logs (CloudWatch → `/resistance/resistance-dev/services`)
     contain "Confirmed SNS subscription".
   - Request a login code on the site; the OTP mail arrives from
     `otp@dev.<domain>` (only to verified addresses while in the SES sandbox).
   - Forward a confirmation email to your personal `track+<alias>@dev.<domain>`
     address from the dashboard; it appears as an application.
4. When the demo is over, set `app_enabled = false` and merge. What
   survives: the ECR images, the raw-mail bucket, the CloudWatch logs.
   What does not: the database (dev takes no final snapshot), the secrets
   (a fresh key next time, so encrypted rows would not survive anyway).

Later deploys are just the Deploy workflow again: it pushes the new
`:latest` and forces a new deployment; the circuit breaker rolls back if
the new tasks never become healthy.

## Going live (prod)

The same three steps against `prod`, applied by hand from the Actions tab
rather than by a push:

1. Deploy → Run workflow → `prod`.
2. `app_enabled = true` in `environments/prod/terraform.tfvars`, merge, then
   Terraform → Run workflow → `prod`. prod's tfvars keep the data: deletion
   protection on the instance and the load balancer, a final snapshot on
   destroy, seven days of backups, ninety days of logs, a thirty-day window
   before a scheduled key deletion takes effect.
3. SES production access (once per account, free) so login codes reach
   anyone, not only verified addresses. Then `dig MX <domain>` shows SES,
   `dig tracker.<domain>` shows the load balancer, and the DKIM records
   show *Successful* in the SES console.

To scale: `db_instance_class` and `db_multi_az` for the database,
`task_cpu` / `task_memory` / `desired_count` for the services (mvc-service
sessions stick to a task, so more than one is safe). Each is one line of
tfvars and one apply.

## Optional integrations

Both are ARNs of Secrets Manager secrets you create by hand, so the value
never touches a file in this repository:

```bash
aws secretsmanager create-secret --name resistance/prod/audit-token --secret-string '<token>'
aws secretsmanager create-secret --name resistance/prod/anthropic-api-key --secret-string '<key>'
```

then `audit_url` + `audit_token_secret_arn` (AuditFlow) and
`anthropic_api_key_secret_arn` (Claude-backed parsing) in tfvars.

## Working locally

CI writes `backend.hcl` from the repository variables. To plan from your
machine, copy `backend.hcl.example` to `backend.hcl` in the environment
directory, fill in the bucket, and:

```bash
cd infrastructure/terraform/environments/dev
terraform init -backend-config=backend.hcl
terraform plan -var-file=terraform.tfvars
```

The lock files carry checksums for linux, macOS (Intel and Apple silicon)
and Windows, so `init` works from any of them. If you add or upgrade a
provider, regenerate them for all four rather than letting your own machine
narrow the file - otherwise everyone else's `init` fails on a checksum
mismatch:

```bash
terraform providers lock   -platform=linux_amd64 -platform=windows_amd64   -platform=darwin_arm64 -platform=darwin_amd64
```

`terraform fmt -check -recursive` and `terraform validate` need no AWS
credentials and are what the pull-request job runs.

## Troubleshooting

### `AccessDeniedException` naming an AWS action, in the apply job

The CI role's permissions come from `bootstrap/oidc.tf`, which only you
apply. The role cannot widen its own policy, so when a change starts using
an AWS service the policy does not list, the workflow fails with an
`AccessDenied` for that action until bootstrap is re-applied:

```bash
cd infrastructure/terraform/bootstrap
terraform apply -var="state_bucket_name=<bucket>" -var="domain_name=<domain>"
```

Then re-run the failed workflow. `oidc.tf` lists every service the
environments use today, so this should only happen after a new module
brings a new service.

### `AccessDenied` on `iam:CreateRole` or `iam:PutRolePolicy`

The CI role may only create IAM principals that carry the permissions
boundary `bootstrap/` publishes (`resistance-ci-boundary`), and only under
the names `resistance-*`. Two causes:

- **`permissions_boundary_arn` is still the placeholder** in the
  environment's `terraform.tfvars`. Apply `bootstrap/`, then copy
  `terraform output ci_boundary_policy_arn` into both tfvars files.
- **A new module creates an IAM principal without setting
  `permissions_boundary`.** Add it, threading the variable through the
  environment like `modules/app` and `modules/secrets` do. If the principal
  needs a permission the boundary does not list, widen the boundary in
  `bootstrap/oidc.tf` and re-apply bootstrap - the boundary is a ceiling, so
  a permission missing there is invisible in the principal's own policy and
  shows up only as a runtime `AccessDenied`.

Note that `iam:DeleteRolePermissionsBoundary` is explicitly denied, so
removing `permissions_boundary` from a module fails the apply rather than
silently widening a principal. That is deliberate.

### The plan job is skipped on my pull request

It skips itself while the `AWS_ROLE_ARN` repository variable is unset
(step 3 above). Format, validate and tfsec still run.

### A service keeps restarting

CloudWatch → `/resistance/<name>/services` → the service's stream. The
usual causes: the `qa` profile fails fast on a missing variable (the log
names it; compare with `application-qa.properties`), or the database is
still starting (the first apply can take ten minutes; ECS retries). A
task that starts but is never *healthy* is the load balancer failing
`/actuator/health`, which includes a database ping.

### The SNS subscription stays "pending confirmation"

SNS could not reach `https://<app host>/intake/aws-sns` within the
confirmation window (10 minutes). The apply already waits for the ECS
service to reach steady state and for the DNS record before creating the
subscription, so the usual remaining causes are the certificate not being
validated yet or intake-service failing its health check. Fix that, then
re-run the apply; the subscription resource retries the confirmation.

### Mail to `track@dev.<domain>` bounces

Check, in order: the registrar's nameservers point at the hosted zone
(`dig NS <domain>`); the MX record resolves (`dig MX dev.<domain>`); the
identity shows *Verified* in the SES console; the receipt rule set named
in `ses_rule_set_name` is the *active* one (bootstrap makes it so, but a
console click can change it).
