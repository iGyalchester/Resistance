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

   **The GitHub OIDC provider is shared with auditflow-infrastructure.** AWS
   allows exactly one provider per URL per account, and both repos need one
   for `token.actions.githubusercontent.com`. auditflow-infrastructure
   creates it; this bootstrap reads it, which is why
   `create_oidc_provider` defaults to **false** here. If you are applying
   this bootstrap into an account where that provider does not exist yet,
   pass `-var="create_oidc_provider=true"` and set the other repo's to
   false instead - but only ever one of the two. Applying both with `true`
   is what used to fail with `EntityAlreadyExists`.
3. **Repository variables** (Settings → Secrets and variables → Actions →
   Variables), from `terraform output`:

   | Variable | From |
   |---|---|
   | `AWS_REGION` | `state_bucket_region` |
   | `AWS_ROLE_ARN` | `github_actions_role_arn` |
   | `AWS_PLAN_ROLE_ARN` | `github_actions_plan_role_arn` |
   | `TF_STATE_BUCKET` | `state_bucket_name` |

   Two roles, on purpose. `AWS_ROLE_ARN` can write to the account and is
   assumable only from `main` and from an environment-bound job.
   `AWS_PLAN_ROLE_ARN` is read-only and is the only one a pull-request
   workflow can assume, because the deploy role's trust policy no longer
   accepts the `pull_request` subject. Any workflow a PR triggers used to
   hold write credentials - including a workflow whose own diff came from
   that PR.

   Also create two GitHub Environments named `dev` and `prod` (Settings →
   Environments); the workflow binds each **apply** job to one so the OIDC
   token carries the environment name. The plan job is deliberately *not*
   bound: an environment-bound job presents `environment:<name>` as its
   subject instead of `pull_request`, which would defeat the plan role's
   trust policy.
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

0. **Apply once first.** The image tag lives in an SSM parameter that
   `modules/ecr` creates, so the environment has to exist before anything can
   be deployed into it. A push to `main` (dev) or a manual Terraform run
   (prod) creates the ECR repositories and the parameter, seeded with the
   placeholder value `bootstrap`.
1. **Deploy workflow** (Actions → Deploy → Run workflow → `dev`, from `main`).
   It builds the two jars once, stamps one image per service from
   `infrastructure/docker/Dockerfile.runtime`, pushes `:<git sha>` to the
   environment's ECR repositories, writes that sha into
   `/resistance/dev/image-tag`, and reports that there is no cluster to roll
   yet.
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

Later deploys are just the Deploy workflow again. It pushes `:<git sha>`,
writes the sha to the parameter, then dispatches this workflow with
`rollout_only=true` and waits for it. That apply refuses to proceed if the
plan touches anything except the task definitions and the services, so an
image deploy can never quietly apply unrelated infrastructure that happens
to be sitting on `main`.

Deploy then waits for the services to stabilise and checks that each one's
PRIMARY deployment really is the *new* task definition revision - "stable"
alone is not enough, because a circuit-breaker rollback also ends stable, on
the old revision. If anything fails it puts the previous sha back in the
parameter and fails the run.

**Why it is built this way.** `:latest` used to be the deployed tag. Because
the tag never changed, the task definition never changed either: ECS could
not tell one release from the next, `--force-new-deployment` was the only way
to make it restart, and the circuit breaker's "rollback" rolled back to the
identical image that had just failed. Now the repositories are IMMUTABLE, the
tag is the commit sha, `skip_destroy` keeps old task definition revisions
ACTIVE, and rolling back means pointing at a revision that still exists and
still names the bytes it always named.

**Rolling back by hand:** put the last good sha into the parameter and run
this workflow with `rollout_only=true`.

```bash
aws ssm put-parameter --name /resistance/dev/image-tag --type String --value <good sha> --overwrite
gh workflow run terraform.yml -f environment=dev -f rollout_only=true
```

## Going live (prod)

The same steps against `prod`, applied by hand from the Actions tab rather
than by a push:

0. Terraform → Run workflow → `prod` once, to create prod's ECR
   repositories and its `/resistance/prod/image-tag` parameter. Deploy fails
   with a clear message until they exist.
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

It skips itself while the `AWS_PLAN_ROLE_ARN` repository variable is unset
(step 3 above). Format, validate and tfsec still run. If you set
`AWS_ROLE_ARN` but not `AWS_PLAN_ROLE_ARN`, the plan job stays skipped by
design rather than falling back to the write-capable role.

### The plan job fails with `AccessDenied` on some read

`ReadOnlyAccess` plus S3/KMS on the state bucket is what the plan role
carries. A genuinely new read-only action means adding it to
`github_actions_plan_extra` in `bootstrap/oidc.tf` and re-applying
bootstrap by hand. Do not "fix" it by pointing the plan job back at
`AWS_ROLE_ARN`.

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
