# Terraform for the tracker

Everything the `qa` profile's fail-fast environment variables need, created
by code instead of by hand, applied from GitHub Actions with no AWS keys
stored anywhere. Read `docs/TECH-GUIDE.md` ("The AWS pieces") first if
Terraform, ECS or OIDC are new words.

```
bootstrap/          applied ONCE by hand with your own credentials: the state bucket,
                    the CI role, and the two account-level things AWS allows only one
                    of (the Route53 hosted zone, the active SES receipt rule set)
modules/
  ecr/              one image repository per deployed service
  email-intake/     SES receiving for one mail domain: verification, DKIM, MX,
                    raw-mail bucket, SNS topic, receipt rule, HTTPS subscription
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
| One environment with `app_enabled = true` | ≈ $45: ALB ≈ $16, two small Fargate tasks ≈ $15, `db.t4g.micro` MySQL ≈ $12 (free-tier eligible for a new account's first year), KMS key $1 |

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
   `hosted_zone_id` output and `example.com` with your domain. Commit.
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

Requires the app modules from the next slice (network, database, secrets,
app). Until then, `app_enabled` is accepted and ignored.

## Working locally

CI writes `backend.hcl` from the repository variables. To plan from your
machine, copy `backend.hcl.example` to `backend.hcl` in the environment
directory, fill in the bucket, and:

```bash
cd infrastructure/terraform/environments/dev
terraform init -backend-config=backend.hcl
terraform plan -var-file=terraform.tfvars
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

### The plan job is skipped on my pull request

It skips itself while the `AWS_ROLE_ARN` repository variable is unset
(step 3 above). Format, validate and tfsec still run.

### Mail to `track@dev.<domain>` bounces

Check, in order: the registrar's nameservers point at the hosted zone
(`dig NS <domain>`); the MX record resolves (`dig MX dev.<domain>`); the
identity shows *Verified* in the SES console; the receipt rule set named
in `ses_rule_set_name` is the *active* one (bootstrap makes it so, but a
console click can change it).
