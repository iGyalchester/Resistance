# Resistance

A job application tracker built as a Spring Boot multi-module monorepo,
refactored from the original
[darbyluv2code Spring Boot course](https://github.com/darbyluv2code/spring-boot-3-spring-6-hibernate-for-beginners)
layout of ~150 standalone demo projects into a single Maven build with
consolidated services, shared libraries, an ETL framework, and deployment
infrastructure.

## Domain model

- **JobApplication** - the central tracked record: company, position, and an
  `ApplicationStatus` enum (`APPLIED`, `SCREENING`, `INTERVIEW`, `OFFER`,
  `REJECTED`, `ACCEPTED`, `WITHDRAWN`), optionally linked to the Contact it
  came through (`contact_id`) and owned by the UserAccount that forwarded it
  (`owner_account_id`)
- **Contact** - recruiters, referrals and hiring managers you talk to
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
│   └── shared-utils/              CSV parsing and normalization helpers
│
├── etl/
│   ├── etl-core/                  Extract/Transform/Load framework interfaces + pipeline
│   ├── etl-data-processors/       CSV extractor, normalizers, entity mappers
│   ├── etl-validators/            Record validation rules
│   └── etl-runner/                Spring Boot app that orchestrates the pipelines
│
├── infrastructure/
│   ├── docker-compose.yml         MySQL + services + gateway
│   ├── docker/Dockerfile          Generic multi-stage image for any module
│   ├── kubernetes/                Namespace, MySQL, and application manifests
│   ├── aws/                       SES inbound -> SNS CloudFormation stack + guide
│   └── config/db-init/            Database schemas and seed data
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

1. resolves (or auto-creates) your **UserAccount** from the forwarding sender,
2. parses the company and position out of the confirmation
   (`HeuristicConfirmationEmailParser` - an interface, so an LLM-backed parser
   can slot in later), and
3. creates the **JobApplication** with status `APPLIED`, owned by you.
   Forwarding the same confirmation twice is a no-op.

Three inbound paths feed the same flow - pick whichever fits:

| Path | Use when | Setup |
|---|---|---|
| `POST /intake/email` JSON webhook | You use Mailgun/SendGrid/Postmark inbound parse, or want a curl smoke test | Set `intake.webhook-token`, point the provider at the endpoint |
| `POST /intake/aws-sns` | You run on AWS | Deploy `infrastructure/aws/ses-intake.yaml` (see `infrastructure/aws/README.md`), set `intake.aws.topic-arn` |
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

## Environments: dev vs qa

`intake-service` and `mvc-service` are profile-split:

- **dev** (the default, no flag needed): everything local. MySQL from
  docker-compose, OTP codes logged to the console, open intake webhook,
  plaintext PII. Nothing external to set up.
- **qa** (`--spring.profiles.active=qa`): everything on AWS, enforced. The
  qa property files have no fallback values, so the services fail at startup
  until RDS, the SES/SNS intake stack, SES SMTP credentials, the webhook
  secret, and the KMS data key are provisioned and injected as environment
  variables. The full checklist lives in `infrastructure/aws/README.md`.

## Security

What is actually in place today:

- **Passwordless OTP login** (mvc-service): codes stored as SHA-256 hashes
  only, 10-minute expiry, 5 attempts, one active code per account,
  constant-time comparison, no account enumeration; `/dashboard` is
  session-gated.
- **Intake hardening**: the webhook requires an `X-Intake-Token` shared
  secret and the SNS endpoint pins the topic ARN - both optional in dev,
  mandatory in qa.
- **PII encryption at rest**: AES-256-GCM via a JPA converter, keyed by an
  AWS KMS-generated data key (`TRACKER_ENC_KEY`). Currently applied to
  `user_account.phone`; extend by annotating fields with
  `@Convert(converter = EncryptedStringConverter.class)`. Fields used as
  lookup keys (account/contact email) stay plaintext for now - encrypting
  them needs deterministic encryption or a hash column first.
- **security-service / mvc-security-service**: the course-derived HTTP Basic
  and form-login demos with JDBC users, roles and bcrypt.

Known gaps, deliberately open in this phase: the admin-style `/applications`
and `/contacts` pages and the REST API are unauthenticated (dev
convenience); there is no TLS termination in-app (terminate at the ALB/
gateway); DB credentials sit in dev property files (qa takes them from the
environment).

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

## Where the course projects went

Each service is the final, most complete project of its course section,
repackaged under `com.resistance.*`, renamed into the job tracker domain
(Employee -> JobApplication, Student -> Contact, Instructor -> Recruiter,
Course -> JobPosting, Review -> Note), with entities, validation, and
exceptions extracted into `shared/`:

| Module | Origin |
|---|---|
| core-service | 02-spring-boot-spring-core / 09-java-config-bean |
| data-service | 03-spring-boot-hibernate-jpa-crud / 08-create-db-tables-automatically |
| rest-api-service | 04-spring-boot-rest-crud / 16-employee-with-spring-data-jpa (+ global exception handling from 06) |
| security-service | 05-spring-boot-rest-security / 07-jdbc-bcrypt-custom-table-names |
| mvc-service | 07-spring-boot-spring-mvc-crud / 04-01-employees-delete (+ validation demo from 06 / 20) |
| mvc-security-service | 08-spring-boot-spring-mvc-security / 13-jdbc-bcrypt-custom-tables |
| advanced-data-service | 09-spring-boot-jpa-advanced-mappings / 21-many-to-many-add-more-courses |
| shared-validation | 06-spring-boot-spring-mvc / 20-validationdemo-custom-validation-rule |

The original numbered course directories (including sections 01, 10, 11, 12
that had no target module) remain available in git history prior to this
refactor.
