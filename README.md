# Resistance

A job application tracker built as a Spring Boot multi-module monorepo:
consolidated services, shared libraries, an ETL framework, a React
frontend, and deployment infrastructure - with email-driven intake and
passwordless login at its core.

> **New to some of this tech?** Read [docs/TECH-GUIDE.md](docs/TECH-GUIDE.md) -
> a plain-language tour of every technology in the stack (Maven modules,
> Spring, JPA, SES/SNS/KMS, the OTP login, the Claude parser, CI), each with
> what it is, why it's here, and where to see it in the code.
>
> **Want to learn it by reading the code?** [docs/CODE-TOUR.md](docs/CODE-TOUR.md)
> is a file-by-file reading order — login, tenancy, email intake, audit
> events out, the React app — with the test that proves each stop.

## How it fits together

![Resistance services push audit events to AuditFlow ingestion, and AuditFlow's collector agent pulls the MySQL query log; ingestion feeds Kafka, enrichment, and S3/Aurora evidence stores](docs/img/resistance-auditflow-flow.svg)

Teal is this repo; ochre is [AuditFlow](https://github.com/iGyalchester/auditflow-platform),
its compliance-evidence sibling. Two routes cross the boundary: the services
**push** security events as they happen (fire-and-forget, so auditing can never
break the tracker), and AuditFlow's collector agent **pulls** the database's own
query log (checkpointed, at-least-once). Details under
[Audit trail](#audit-trail-auditflow-integration).

## Domain model

- **JobApplication** - the central tracked record: company, position, and an
  `ApplicationStatus` enum (`APPLIED`, `SCREENING`, `INTERVIEW`, `OFFER`,
  `REJECTED`, `ACCEPTED`, `WITHDRAWN`), optionally linked to the Contact it
  came through (`contact_id`) and owned by the UserAccount that forwarded it
  (`owner_account_id`)
- **Contact** - recruiters, referrals and hiring managers you talk to, owned
  by the UserAccount whose address book they are in (`owner_account_id`); the
  same recruiter writing to two users produces two rows, one each
- **UserAccount / LoginCode** - tracker users, auto-provisioned by email
  intake; passwordless login via hashed one-time codes
- **Recruiter / RecruiterDetail / JobPosting / Note / Candidate** - the advanced
  JPA mapping demos (1-1, 1-N, N-N) expressed in tracker terms

## Project layout

```
Resistance/
├── services/
│   ├── core-service/              Spring Core: DI, qualifiers, scopes, Java config (port 8081)
│   ├── data-service/              JPA/Hibernate CRUD command-line demo (Contact)
│   ├── rest-api-service/          REST CRUD API for tracked job applications (port 8083)
│   ├── security-service/          REST API + JDBC users/roles/bcrypt security (port 8084)
│   ├── mvc-service/               Spring MVC + Thymeleaf application CRUD & forms (port 8085)
│   ├── mvc-security-service/      MVC form login, roles, custom tables (port 8086)
│   ├── advanced-data-service/     JPA advanced mappings CLI demo (1-1, 1-N, N-N)
│   └── intake-service/            Email intake: webhook / AWS SES+SNS / IMAP (port 8087)
│
├── shared/
│   ├── shared-models/             JPA entities and DTOs
│   ├── shared-validation/         Custom Bean Validation constraints (@ReferralCode)
│   ├── shared-exceptions/         Common exceptions and API error payloads
│   └── shared-utils/              CSV/name helpers, field encryption, audit event client
│
├── etl/
│   ├── etl-core/                  Extract/Transform/Load framework interfaces + pipeline
│   ├── etl-data-processors/       CSV extractor, normalizers, entity mappers
│   ├── etl-validators/            Record validation rules
│   └── etl-runner/                Spring Boot app that orchestrates the pipelines
│
├── infrastructure/
│   ├── docker-compose.yml         MySQL + services + gateway
│   ├── docker/Dockerfile          Generic multi-stage image for any module (+ Dockerfile.runtime for CI)
│   ├── kubernetes/                Namespace, MySQL, and application manifests
│   ├── terraform/                 AWS by code: bootstrap (once), modules, dev + prod envs
│   ├── aws/                       How email intake works on AWS (SES -> SNS -> app)
│   └── config/db-init/            Database schemas and seed data
│
├── frontend/                      React + TypeScript SPA (login + dashboard, Vite)
│
└── api-gateway/                   Routes /{service}/** to the matching service (port 8080)
```

## Building

Requires JDK 26 and Maven 3.9+ (Spring Boot 4.1).

```bash
mvn clean package          # everything
mvn -pl services/rest-api-service -am package   # one service + its dependencies
```

## Running locally

Every JPA-backed module expects MySQL with the schemas in
`infrastructure/config/db-init/` and credentials `springstudent`/`springstudent`.
The quickest way to get one:

```bash
docker compose -f infrastructure/docker-compose.yml up mysql
```

The init scripts only run on an empty data directory, so if you already have
a `resistance_mysql-data` volume from before a schema change, reset it first
(this drops local data):

```bash
docker compose -f infrastructure/docker-compose.yml down -v
```

Then run any service, e.g.:

```bash
mvn -pl services/rest-api-service -am spring-boot:run
```

`DB_HOST`/`DB_PORT` environment variables override the default
`localhost:3306` in every service.

Full stack (MySQL, four web services, gateway):

```bash
docker compose -f infrastructure/docker-compose.yml up --build
```

The gateway then serves e.g. `http://localhost:8080/rest-api/api/applications`.

## Email intake & passwordless login

The zero-form workflow: when a company sends "we received your application",
forward that email to your tracker's intake address. `intake-service` then

1. resolves your **UserAccount** - preferably from the **personal intake
   alias** in the recipient address (`track+<alias>@domain`), which is the
   trust anchor: knowing your alias is what authorizes filing into your
   account, so a spoofed From header buys an attacker nothing. Your first
   forward to the bare address provisions the account and hands out the
   alias (shown on the dashboard and echoed by the webhook); with
   `intake.require-alias=true` (qa) the bare address accepts nothing.
2. parses the email: regex heuristics first (free, offline), and - when an
   `ANTHROPIC_API_KEY` is configured - **Claude** (`claude-opus-5` via the
   official Anthropic Java SDK, schema-validated structured output, model
   output sanitized before use) for the emails the heuristics can't read.
   Rejections flip the application to `REJECTED`, interview invites to
   `INTERVIEW`, offers to `OFFER`; a human recruiter's reply is captured
   as a linked Contact.
3. creates or updates the **JobApplication** accordingly, records every
   status transition in **status_history** (fuel for funnel metrics), and
   notifies you ("Acme Corp moved to INTERVIEW" - logged in dev, emailed
   when SMTP is configured; `tracker.digest.enabled=true` adds a Monday
   weekly summary). Forwarding the same confirmation twice is a no-op.

Three inbound paths feed the same flow - pick whichever fits:

| Path | Use when | Setup |
|---|---|---|
| `POST /intake/email` JSON webhook | You use Mailgun/SendGrid/Postmark inbound parse, or want a curl smoke test | Set `intake.webhook-token`, point the provider at the endpoint |
| `POST /intake/aws-sns` | You run on AWS | Terraform's `email-intake` module creates the SES→SNS chain (the receipt rule publishes the message body to SNS and archives the raw MIME in S3) and sets `intake.aws.topic-arn` (see `infrastructure/aws/README.md`) |
| IMAP polling | Any ordinary mailbox, e.g. Gmail + app password | `intake.imap.enabled=true` + host/username/password |

Smoke test with curl:

```bash
curl -s -X POST localhost:8087/intake/email \
  -H 'Content-Type: application/json' \
  -d '{
    "fromAddress": "you@gmail.com",
    "fromName": "Your Name",
    "subject": "Fwd: Thank you for applying to Acme Corp",
    "body": "We received your application for the Backend Engineer position at Acme Corp."
  }'
```

**Logging in** (mvc-service, port 8085): go to `/login`, enter your email, and
submit the 6-digit one-time code you receive - no password exists anywhere.
Codes are stored hashed, expire after 10 minutes, and allow 5 attempts. With no
SMTP configured the code is printed in the mvc-service log (dev mode); set
`spring.mail.*` (any SMTP server, including AWS SES's) for real delivery. After
login, `/dashboard` shows only your applications.

## React front end

`frontend/` is a React 19 + TypeScript single-page app (Vite) covering the
login flow and a read-only dashboard so far. It talks to a JSON API in
mvc-service (`/api/auth/*`, `/api/applications`) that reuses the exact same
OTP service, throttles, session auth, and owner-scoping as the Thymeleaf
pages — the two UIs run side by side until the React app reaches parity.

```bash
# terminal 1: the backend (needs MySQL, see "Running locally")
mvn -pl services/mvc-service -am spring-boot:run

# terminal 2: the frontend with hot reload
cd frontend
npm install
npm run dev          # http://localhost:5173, /api proxied to :8085
```

`npm run build` type-checks (`tsc`) and produces static files in
`frontend/dist/`; `npm test` runs the Vitest + React Testing Library suite.
CI builds and tests the frontend in its own job.

| Endpoint | What |
|---|---|
| `POST /api/auth/code` | Request a login code (same generic answer for any address) |
| `POST /api/auth/login` | Verify the code; authenticates the session, returns the user |
| `GET /api/auth/me` | Who is logged in (401 when nobody) |
| `POST /api/auth/logout` | End the session |
| `GET /api/applications` | The session owner's applications |

Auth is the session cookie itself — no tokens. CSRF tokens ride in the
readable `XSRF-TOKEN` cookie and come back as an `X-XSRF-TOKEN` header;
anonymous `/api/**` calls get `401 {"error":"unauthenticated"}` instead of
a login redirect. The plain-language walkthrough of all of this is in
[docs/TECH-GUIDE.md](docs/TECH-GUIDE.md).

## Environments: dev vs qa

`intake-service` and `mvc-service` are profile-split:

- **dev** (the default, no flag needed): everything local. MySQL from
  docker-compose, OTP codes logged to the console, open intake webhook,
  plaintext PII. Nothing external to set up.
- **qa** (`--spring.profiles.active=qa`): everything on AWS, enforced. The
  qa property files have no fallback values, so the services fail at startup
  until RDS, the SES/SNS intake chain, SES SMTP credentials, the webhook
  secret, and the KMS-protected data key are provisioned and injected as
  environment variables. Terraform creates all of them (next section); the
  variable-by-variable checklist lives in `infrastructure/aws/README.md`.

## Deploying to AWS (Terraform)

`infrastructure/terraform/` describes the whole AWS footprint as code and
GitHub Actions applies it through a short-lived OIDC role, so no AWS keys
are stored in the repository. Two environments, `dev` and `prod`, share one
account and one domain: `dev` receives mail at `track@dev.<domain>` and
serves `tracker-dev.<domain>`; `prod` owns `track@<domain>` and
`tracker.<domain>`.

Cost is gated on one flag per environment. With `app_enabled = false` (the
default) an apply creates only free things: image repositories, the SES
domain verification and MX records, the raw-mail bucket, the SNS topic and
the receipt rule. Flipping it to `true` adds the network, the MySQL
instance, the secrets and the two Fargate services behind an HTTPS load
balancer, which together cost about $50 a month while they run.

- `dev` is applied on every push to `main`; `prod` only when the Terraform
  workflow is run by hand and `prod` is chosen.
- The **Deploy** workflow (manual, `main` only) builds the two service
  images, pushes them tagged with the commit sha, records that sha in
  `/resistance/<env>/image-tag`, and asks the Terraform workflow to roll the
  services onto it. Run it before flipping `app_enabled`, and again for
  every release. Images are immutable and Terraform stays the only writer of
  task definitions, so each release is a distinct revision and a rollback
  goes back to a specific build rather than to a moving `:latest`.
- On AWS the services apply an idempotent copy of the schema at startup
  (`shared-models/.../db/job-tracker-schema.sql`, `CREATE TABLE IF NOT
  EXISTS` only); a test keeps it identical to the local init script.
- The load balancer terminates HTTPS and forwards plain HTTP, so the `qa`
  profile sets `server.forward-headers-strategy=native` in both services.
  Without it, login redirects come back as `http://`, cookies lose their
  `Secure` flag, and every request looks like it came from the load
  balancer — which would put every user in one OTP rate-limit bucket.
- One-time setup (bootstrap, repository variables, the domain) and the
  troubleshooting notes are in
  [`infrastructure/terraform/README.md`](infrastructure/terraform/README.md).

## Security

What is actually in place today:

- **Spring Security across mvc-service**: every page except the login flow
  requires an authenticated session; applications *and* contacts are
  owner-scoped at the service layer (another user's rows are
  indistinguishable from missing ones, and a posted contact id belonging to
  someone else is refused on save); CSRF protection is on and deletes are
  POSTs; login rotates the session id.
- **Passwordless OTP login**: codes stored as SHA-256 hashes only,
  10-minute expiry, 5 attempts, one active code per account, constant-time
  comparison, no account enumeration - and code requests are rate-limited
  per email and per IP. Expired codes are purged hourly.
- **Intake hardening**: the webhook requires an `X-Intake-Token` shared
  secret; the SNS endpoint **verifies the SNS message signature** (RSA
  against the AWS signing certificate, cert-URL host pinned to
  `sns.*.amazonaws.com`) and pins the topic ARN; account routing trusts
  the personal intake alias, not the spoofable From header. All hard
  requirements in qa; dev relaxes the signature check for curl testing.
- **LLM output treated as untrusted**: the Claude parser pins the prompt to
  extraction-only over delimited email content, and sanitizes the structured
  response (length caps, enum parse, email validation) before persistence;
  any API error or refusal degrades to "not parsed".
- **PII encryption at rest**: AES-256-GCM via a JPA converter, keyed by an
  AWS KMS-generated data key (`TRACKER_ENC_KEY`). Currently applied to
  `user_account.phone`; extend by annotating fields with
  `@Convert(converter = EncryptedStringConverter.class)`. Fields used as
  lookup keys (account/contact email) stay plaintext for now - encrypting
  them needs deterministic encryption or a hash column first.
- **security-service / mvc-security-service**: standalone HTTP Basic and
  form-login demos with JDBC users, roles and bcrypt.

- **Supply chain**: Dependabot watches Maven and Actions versions; CodeQL
  scans the Java code on PRs and weekly. Enable branch protection on
  `main` (require the Build check) in the repo settings - that part can't
  live in the codebase.

Remaining known gaps: `rest-api-service` (port 8083) is the *unsecured
legacy demo* of the REST API - its secured twin is `security-service` -
so never expose 8083 publicly; TLS terminates at the ALB/gateway, not
in-app; dev DB credentials sit in property files (qa takes everything
from the environment).

## Audit trail (AuditFlow integration)

Set `TRACKER_AUDIT_URL` (and `TRACKER_AUDIT_TOKEN`) and mvc-service +
intake-service emit audit events - OTP requests, login successes and
failures, application creates/updates/deletes, profile (PII) access,
intake provisioning - to an
[AuditFlow](https://github.com/iGyalchester/auditflow-platform) ingestion
endpoint, where they become SOC 2/GDPR evidence and can trigger alert
rules (e.g. repeated login failures). Emission is asynchronous and
fire-and-forget: auditing being down never breaks the tracker
(at-most-once delivery, by design). Blank URL = disabled, the dev
default.

`TRACKER_AUDIT_TOKEN` has to match the token half of an
`AUDIT_INGESTION_TOKENS` entry on the AuditFlow side, and that entry names
the tenant the token may write as. Ours is `resistance=<secret>`, matching
`tracker.audit.customer-id=resistance`; events claiming any other
`customerId` come back 403.

Each event carries `occurredAt`, taken from this machine's clock at the
moment the audited thing happened rather than whenever the asynchronous
send lands. Delivery being slow no longer moves an event into the next
report window.

The full two-system test walkthrough is in
[docs/E2E-TEST-PLAN.md](docs/E2E-TEST-PLAN.md).

## Production database (decision pending)

Dev/qa run MySQL (local container / RDS). If production moves to DynamoDB
or MongoDB/DocumentDB: the per-service Spring Data repositories are the
seam - DocumentDB is the smaller step (swap `spring-data-jpa` for
`spring-data-mongodb`, entities keep their shape), while DynamoDB implies
single-table modeling and rewriting the repository layer per service. The
field-encryption converter is JPA-specific; its `FieldEncryptor` core in
`shared-utils` is storage-agnostic and would carry over.

## ETL

`etl-runner` imports job applications from CSV through the
extract → validate → transform → load pipeline defined in `etl-core`:

```bash
mvn -pl etl/etl-runner -am spring-boot:run
```

By default it processes the bundled `sample-data/applications.csv` in dry-run
mode. Set `etl.dry-run=false` to write to the database and
`etl.input-location=file:/path/to/file.csv` to point at your own data.

## Origin

The domain model (applications, contacts, recruiters, postings) was
originally seeded from a Spring Boot course codebase; it has since been
fully rewritten and extended into the tracker documented above.
