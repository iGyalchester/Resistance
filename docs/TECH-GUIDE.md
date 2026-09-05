# Tech Guide — what everything is and why it's here

A plain-language tour of every technology in this repo, written for someone
who can read Java but hasn't met all of these tools before. Each section
says **what** the thing is, **why** this project uses it, and **where** to
see it in the code. Read it top to bottom once; after that, use the
[cheat sheet](#cheat-sheet-where-to-look-when) at the bottom.

---

## The big picture

The tracker's core trick: you never fill in a form. You forward a
"we received your application" email, and a chain of services turns it
into a row on your dashboard:

```
you forward an email
   │
   ▼
intake-service ── parses "who applied where, and what happened"
   │                 (regex first, Claude for the hard ones)
   ▼
MySQL ─────────── one job_application row, owned by your account
   ▲
   │
mvc-service ───── the website: log in with a one-time code,
                  see YOUR applications on /dashboard
   │
   └──▶ AuditFlow (optional) ── both services also emit security audit
        events (logins, data changes, PII access) to a sister project;
        see "Audit events out to AuditFlow" below
```

Everything else in the repo supports that flow (shared code, deployment
files, CI) or is a self-contained learning demo (core-service, the
security demos, advanced-data-service, the ETL modules).

---

## Build & project structure

### Maven multi-module build

**What:** Maven is Java's build tool; a *multi-module* build is one parent
`pom.xml` that lists many sub-projects ("modules") built together in
dependency order.
**Why:** the services share code (`shared/`), so they must build against
the same versions in one command instead of 18 separate projects.
**Where:** the root [`pom.xml`](../pom.xml) lists every module; each module's
own `pom.xml` declares only what *it* needs. `mvn clean package` at the root
builds everything.

### Spring Boot

**What:** the framework everything runs on. Its core idea is *dependency
injection*: you declare classes as **beans** (`@Service`, `@Component`,
`@Configuration` + `@Bean`) and Spring constructs them and passes them into
each other's constructors — you never write `new IntakeService(...)`
yourself, Spring does, with the right arguments.
**Why:** wiring, configuration, web servers, and database access come
mostly for free, so each service stays small.
**Where:** every `*Application.java` is an entry point; look at
`IntakeService`'s constructor to see injection: it just *declares* the
repositories it needs, and Spring provides them.

Two Spring patterns worth knowing here:

- **`@Value("${some.property:default}")`** pulls a value from
  `application.properties` (or an environment variable) into a constructor
  argument. That's how `intake.require-alias` reaches the code.
- **Conditional beans** (`@ConditionalOnProperty`, or an `if` inside a
  `@Bean` method) mean a feature only exists when configured — e.g.
  `ParserConfig` only builds the Claude parser when an API key is set.

### Spring profiles (dev vs qa)

**What:** a *profile* is a named set of extra configuration.
`application.properties` is always loaded; `application-dev.properties`
is added on top when the `dev` profile is active, `application-qa.properties`
when `qa` is.
**Why:** dev should run with zero setup (local MySQL, logged codes, open
webhook); qa should *refuse to start* until real AWS resources are wired in.
The qa file achieves that by using `${DB_HOST}` **without** a default —
an unresolvable placeholder crashes startup, which is the point ("fail
fast" beats silently running misconfigured).
**Where:** `services/intake-service/src/main/resources/application-*.properties`
and the same in mvc-service. `spring.profiles.default=dev` makes dev the
no-flag default.

---

## The database layer

### JPA / Hibernate (the `@Entity` classes)

**What:** JPA is Java's standard for mapping classes to database tables;
Hibernate is the engine that implements it. A class annotated `@Entity`
with `@Table`/`@Column` mappings *is* a table row.
**Why:** you write `applicationRepository.save(app)` instead of SQL.
**Where:** `shared/shared-models/.../entity/` — `JobApplication` is the
best one to read first: it shows an enum column
(`@Enumerated(EnumType.STRING)` stores `"APPLIED"` as text, not a fragile
number) and two **`@ManyToOne`** links (many applications → one Contact,
many applications → one UserAccount), which become plain foreign-key
columns (`contact_id`, `owner_account_id`) in MySQL.

### Spring Data repositories (the interfaces with no code)

**What:** an interface like
`interface JobApplicationRepository extends JpaRepository<JobApplication, Integer>`
gets a full implementation *generated at runtime* — save, findById, delete.
Better: Spring parses **method names** into queries.
`findByOwnerIdAndCompanyNameIgnoreCase(...)` becomes
`WHERE owner_account_id = ? AND LOWER(company_name) = LOWER(?)`.
**Why:** zero boilerplate — and one sharp edge: the name is only checked
at *startup*, not compile time. (A stale `findAllByOrderByLastNameAsc`
survived a rename here and would have crashed boot; CI caught it.)
**Where:** every `dao/` package.

### The JPA attribute converter (field encryption hook)

**What:** an `AttributeConverter` sits between a Java field and its
database column, transforming the value in both directions.
**Why:** that's the seam where PII encryption lives — the code reads and
writes `account.getPhone()` normally, while the *column* holds ciphertext.
**Where:** `shared-models/.../EncryptedStringConverter.java`, applied with
`@Convert` on `UserAccount.phone`. The crypto itself is below under
[Encryption](#field-encryption-aes-gcm--kms).

---

## The web layer (mvc-service)

### Spring MVC + Thymeleaf

**What:** Spring MVC maps URLs to controller methods; Thymeleaf is the
template engine — HTML files with `th:` attributes that get filled in
server-side (`th:each` loops, `th:text` inserts, `th:field` binds a form
input to a Java object's field).
**Why:** simple server-rendered pages, no JavaScript framework needed. (The
React app in `frontend/` is the client-rendered counterpart — see
[the React section](#the-react-front-end-frontend) for the comparison.)
**Where:** `mvc-service/.../controller/` + `src/main/resources/templates/`.
`JobApplicationController` + `applications/list-applications.html` is the
canonical pair.

### Spring Security (sessions, CSRF, who may see what)

**What:** the framework that decides which requests need a logged-in user.
Our `SecurityConfig` says: everything except `/login/**` requires an
authenticated *session* (server-side memory tied to a browser cookie);
anonymous visitors get redirected to the login page. Logging in rotates the
session id (blocking "session fixation" attacks) and stores a security
context that the framework checks on every request.
**CSRF** ("cross-site request forgery"): a malicious site can make your
browser submit forms to ours using your cookie. Spring Security's defense -
a secret token required in every state-changing POST - is on, and Thymeleaf
injects the token into every `th:action` form automatically. That's also
why deletes are POST forms instead of links: a GET that changes state can
be triggered by a simple `<img>` tag.
**Owner-scoping:** the multi-user boundary lives in
`JobApplicationServiceImpl` - every query and mutation takes the acting
account's id, and someone else's application is indistinguishable from a
missing one. The tests in `JobApplicationOwnershipTests` demonstrate each
denied path.
**Where:** `mvc-service/.../auth/SecurityConfig.java`,
`auth/LoginController.java`, `service/JobApplicationServiceImpl.java`.

### Passwordless OTP login

**What/why:** users never chose a password (their account was created by
forwarding an email), so login works by proving control of the email inbox:
we send a 6-digit one-time code, they type it back. Security properties
worth noticing in `auth/OtpService`:

- only the **SHA-256 hash** of the code is stored — a database leak reveals
  nothing usable;
- codes expire in 10 minutes and allow 5 attempts;
- requesting a code for an unknown email behaves *identically* to a known
  one, so an attacker can't probe which emails have accounts
  ("no account enumeration");
- hashes are compared with `MessageDigest.isEqual`, which takes constant
  time — a normal `equals` returns faster on early mismatches, and that
  timing difference is measurable ("timing attack").

- code **requests are rate-limited** (`OtpRequestThrottle`): 3 per email
  per 15 minutes and 10 per IP per hour, with identical responses when
  throttled so an attacker learns nothing - this stops OTP inbox-bombing;
- expired codes are swept hourly by `LoginCodePurgeJob` so the table
  doesn't grow forever.

**Where:** `mvc-service/.../auth/`. `OtpNotifier` is the delivery
abstraction: a log-to-console implementation for dev, SMTP when
`spring.mail.host` is configured.

---

## The React front end (frontend/)

### React, components, JSX

**What:** React is a JavaScript library for building UIs out of
**components** — functions that take data and return what the screen should
show. The markup-in-JavaScript syntax (`<StatusBadge status={app.status} />`)
is called **JSX**. When data changes (you call a `useState` setter), React
re-runs the affected components and updates only the changed parts of the
page. That's the whole mental model: *UI = function(state)*.
**Why:** this is the other way to build a web UI. Our Thymeleaf pages are
**server-rendered**: every click loads a whole new HTML page built by the
server. The React app is a **SPA** ("single-page application"): the browser
loads it once, then it fetches raw JSON from the server and redraws itself —
snappier interactions, and the skill most frontend job postings ask for.
Both UIs run side by side against the same services, so you can compare
them page for page.
**Where:** `frontend/src/`. Start with `App.tsx` (the route table), then
`pages/DashboardPage.tsx` — fetch data in `useEffect`, hold it in
`useState`, render a table from it.

### TypeScript

**What:** JavaScript plus compile-time types — the same deal Java gives
you. `interface ApplicationView` in `src/api/types.ts` mirrors the Java
`ApplicationView` record field for field; misspell a property and
`tsc` fails the build instead of the browser failing the user.
**Why:** virtually every production React codebase uses it.

### Vite and the dev proxy

**What:** Vite is the build tool: `npm run dev` serves the app with instant
hot reload at `localhost:5173`, `npm run build` type-checks and bundles it
into static files in `dist/`. The `server.proxy` entry in `vite.config.ts`
forwards `/api/**` to mvc-service on 8085, so the browser talks to *one*
origin — no CORS configuration, and the session cookie flows naturally.
**Where:** `frontend/vite.config.ts`, scripts in `frontend/package.json`.

### The JSON API the SPA talks to

**What:** `@RestController` classes in `mvc-service/.../api/` — same
Spring MVC as the page controllers, but returning objects that Spring
serializes to JSON instead of template names. They reuse the exact same
`OtpService`, throttles, and owner-scoped `JobApplicationService` as the
Thymeleaf pages; `SessionAuthenticator` is the shared piece that turns a
verified code into an authenticated session for both. Entities never go on
the wire — flat records (`ApplicationView`, `MeView`) do, so lazy-loading
proxies and fields like the owner link can't leak by accident.
**Why not rest-api-service?** that module (port 8083) is the unsecured
legacy demo. The real API lives where the security machinery already is.
**Where:** `api/AuthApiController.java`, `api/ApplicationApiController.java`,
tests in `src/test/java/com/resistance/mvc/api/`.

### Auth from a SPA: the session cookie and the CSRF dance

**What:** the React app logs in with the same OTP flow and gets the same
session cookie as the Thymeleaf pages — no tokens, no JWT. Two SPA-specific
wrinkles:

- **401 instead of redirect:** an anonymous *page* request should bounce to
  `/login`; an anonymous *fetch* should not receive a 302 to an HTML page.
  `SecurityConfig` gives `/api/**` its own entry point returning
  `401 {"error":"unauthenticated"}`, which `src/api/client.ts` turns into a
  client-side redirect to the login route.
- **CSRF for JavaScript:** Thymeleaf gets its CSRF token injected into
  forms server-side; JavaScript can't. So the token also lives in a
  readable `XSRF-TOKEN` cookie, and the client echoes it back in an
  `X-XSRF-TOKEN` header on every POST. `SpaCsrfTokenRequestHandler` is
  Spring Security's documented recipe for accepting both styles at once.

**Where:** `auth/SecurityConfig.java`, `auth/SpaCsrfTokenRequestHandler.java`,
`frontend/src/api/client.ts`, `frontend/src/auth/AuthContext.tsx` (the
route guard that asks `GET /api/auth/me` "who am I?" on page load).

### Vitest + React Testing Library

**What:** the frontend's JUnit. Vitest runs the tests; React Testing
Library renders components into a simulated browser (jsdom) and interacts
the way a user would — find the field labeled "Email address", type into
it, click the button, assert what appears. `fetch` is stubbed per test, so
the whole login → code → dashboard journey runs in milliseconds with no
server.
**Where:** `frontend/src/test/`. `LoginFlow.test.tsx` is the canonical one.

---

## Email intake (intake-service)

### The three inbound paths

Servers can't receive email directly, so three adapters all normalize into
one `InboundEmail` record and one `IntakeService.process(...)` flow:

| Path | What it is | File |
|---|---|---|
| JSON webhook | An email provider (Mailgun/SendGrid/Postmark) POSTs each received email as JSON to us | `web/EmailIntakeController` |
| AWS SES → SNS | AWS receives the email, publishes a notification, and SNS POSTs it to us (see [AWS section](#the-aws-pieces)) | `web/SnsIntakeController` |
| IMAP polling | We log into an ordinary mailbox every minute and read unread mail — zero provider setup | `imap/ImapPollingService` |

### Personal intake aliases (the trust model)

**What:** "plus addressing" — mail servers deliver `track+anything@domain`
to the same inbox as `track@domain`, and the `+anything` part rides along
in the recipient header. Each account gets a random tag
(`track+a8f3k2xq99@domain` is *your* address).
**Why this matters for security:** the `From` header of an email is
trivially fakeable, so it must never decide whose account an email lands
in. The recipient alias can't be guessed, so *knowing your own alias* is
what authorizes filing into your account. Mail to an unknown alias is
dropped without creating anything.
**Where:** `IntakeService.extractAlias(...)` and the routing block at the
top of `process(...)`. In qa, `intake.require-alias=true` also disables
the bare-address bootstrap path.

### Parsing: heuristics first, Claude second

**Heuristics** (`parser/HeuristicConfirmationEmailParser`): confirmation
emails are formulaic, so ordered regular expressions extract company and
position, phrase lists classify the email's meaning (rejection → REJECTED,
invite → INTERVIEW, offer → OFFER), and the embedded forwarded `From:` line
identifies a human recruiter to save as a Contact. Free, instant, offline,
and fully unit-tested.

**Claude** (`parser/claude/ClaudeConfirmationEmailParser`): when the
heuristics find nothing *and* an `ANTHROPIC_API_KEY` is configured, the
email goes to the Claude API (model `claude-opus-5`) through the official
Anthropic Java SDK. Three things to understand about how it's done:

1. **Structured output:** instead of asking for prose and hoping, we hand
   the SDK a Java record (`ExtractedApplication`) and the API guarantees
   the response matches that schema — `.outputConfig(ExtractedApplication.class)`
   returns a *typed* object, no JSON string parsing.
2. **The email is treated as hostile input:** the prompt wraps it in
   `<email>` tags and pins Claude to extraction-only, so an email containing
   "ignore previous instructions and..." is just text to describe. This is
   the standard defense against *prompt injection*.
3. **The model's output is also untrusted:** `sanitize(...)` caps field
   lengths, parses the status against our enum, and regex-validates the
   contact email before anything is persisted. Any API error or safety
   refusal degrades to "could not parse" — the same as the heuristics
   shrugging.

`parser/FallbackConfirmationEmailParser` chains the two;
`parser/ParserConfig` decides at startup which chain exists (no key = no
Claude, fully offline).

### Status history & notifications

**What:** every status transition is recorded as a `status_history` row
(`from_status` NULL marks creation, `source` says whether an email or a
manual edit drove it), and `JobApplication` carries `applied_at`/`updated_at`
timestamps set by *JPA lifecycle callbacks* (`@PrePersist`/`@PreUpdate` -
methods the persistence layer runs automatically around saves).
**Why:** the history table is the raw material for funnel metrics
(time-in-stage, response rates), and it means a status overwrite never
loses information.
**Notifications:** when intake creates or moves an application, a
`StatusNotifier` tells the owner ("Acme Corp moved to INTERVIEW") - logged
in dev, emailed when SMTP is configured; a notification failure never
fails the intake itself. An opt-in `WeeklyDigestJob`
(`tracker.digest.enabled=true`) sends a Monday summary per account.
Message text lives in `Notifications` as pure functions so it's testable
without any mail server.
**Where:** `shared-models/.../StatusHistory.java`,
`intake-service/.../notify/`, recording in `IntakeService` and
`mvc-service/.../JobApplicationServiceImpl`.

---

## Audit events out to AuditFlow

### What and why

Resistance now *emits* audit events - one line per security-relevant
moment - to the owner's other project,
[AuditFlow](https://github.com/iGyalchester/auditflow-platform), a
compliance-monitoring platform. Every OTP request, login success/failure,
application create/update/delete, profile (PII) access, and intake
provisioning becomes an `AuditEvent` that AuditFlow classifies against
SOC 2/GDPR controls, stores as evidence, and can alert on (e.g. a rule
flagging repeated LOGIN_FAILUREs). This answers the question every
multi-user PII-holding app eventually gets asked: *who did what, when?*

### How it works

`AuditEventClient` (in `shared/shared-utils`, pure JDK - no new
dependencies) POSTs JSON to AuditFlow's ingestion endpoint with a shared
`X-Audit-Token` secret. Three properties control it
(`tracker.audit.url/token/customer-id`); a blank URL disables it, which is
the dev default. Two design rules worth internalizing:

- **Auditing must never break the app.** Emission is asynchronous with a
  2-second timeout and swallows every failure (logged, dropped). That
  makes delivery *at-most-once* - an honest, documented tradeoff. The
  "real" compliance answer is a transactional outbox (events written to
  our DB in the same transaction, relayed with retries); that's future
  work, chosen against for v1 simplicity.
- **Emit at the seams that already enforce security.** The calls sit in
  LoginController/AuthApiController (auth), JobApplicationServiceImpl
  (the owner-scoping boundary), ProfileController (encrypted PII), and
  IntakeService (provisioning) - the same choke points the security model
  already flows through, so nothing can be audited inconsistently.

**Where:** `shared-utils/.../audit/AuditEventClient.java` (with its own
test suite incl. a hung-server test proving emit never blocks),
`mvc-service/.../audit/AuditConfig.java`, and the emit calls at those
seams. The full two-system walkthrough lives in `docs/E2E-TEST-PLAN.md`.

---

## The AWS pieces

Used only in qa/production; dev needs none of this.

| Service | One-sentence explanation | Role here |
|---|---|---|
| **SES** (Simple Email Service) | AWS's email send/receive service | *Receives* mail for `track@yourdomain.com` (via an MX DNS record) and can also *send* our OTP emails over SMTP |
| **SNS** (Simple Notification Service) | publish/subscribe messaging — a "topic" pushes messages to subscribers | SES publishes each received email to a topic; the topic POSTs it to `/intake/aws-sns` |
| **S3** | file storage | keeps the raw email (30-day expiry) |
| **KMS** (Key Management Service) | managed encryption keys | protects the data key used for field encryption (below) |
| **Route53** | AWS's DNS service; a "hosted zone" is one domain's set of records | holds the MX record that sends `track@…` mail to SES, the SES verification/DKIM records, and later the app's hostname |
| **ECR** (Elastic Container Registry) | a private Docker image store | the Deploy workflow pushes one image per service here; ECS pulls from it |
| **ECS Fargate** | runs containers without servers to manage: you say "this image, this much CPU and memory, this many copies" and AWS finds room for it | one *service* each for mvc-service and intake-service; a *task* is one running copy |
| **ALB** (Application Load Balancer) | the public front door: terminates HTTPS, checks each task's health, and routes by path | `/intake/*` goes to intake-service, everything else to mvc-service; login sessions stick to one task |
| **ACM** (Certificate Manager) | free TLS certificates, renewed automatically | the ALB's certificate for `tracker.<domain>`, proven by a DNS record Terraform writes |
| **RDS** (Relational Database Service) | managed MySQL: backups, patching, failover handled for you | the same MySQL 8 the local container runs; its master password is generated and rotated by RDS in Secrets Manager |
| **SSM Parameter Store** / **Secrets Manager** | places to keep secrets encrypted, with an audit trail of who read them | the field-encryption key, the webhook token and the SMTP credentials (Parameter Store); the database password (Secrets Manager); ECS injects them as environment variables at start |
| **Terraform** | infrastructure-as-code: `.tf` files describe the resources you want, `terraform apply` makes AWS match them | [`infrastructure/terraform/`](../infrastructure/terraform/) creates every AWS resource above, for a `dev` and a `prod` environment |

### Terraform, state, and why CI has no AWS keys

Terraform reads every `.tf` file in a directory, works out what AWS
resources they describe, compares that with what exists, and applies the
difference. The comparison needs a memory of what it created last time:
the **state** file. It lives in an S3 bucket so a laptop and a CI runner
see the same memory, and a lockfile stops two applies from racing. A
**module** is a folder of `.tf` files you call like a function
(`module "ecr" { source = "../../modules/ecr" ... }`); `environments/dev`
and `environments/prod` call the same modules with different inputs.

The chicken-and-egg problem: the state bucket cannot be created by a
configuration whose state lives in that bucket. So `bootstrap/` is a small
configuration you apply once by hand; it creates the bucket, the CI role,
and the two account-level things AWS allows only one of (the hosted zone
and the active SES receipt rule set).

CI never holds an AWS key. GitHub issues each workflow run a short-lived
signed token (**OIDC**, the same idea as "log in with Google"), and the
bootstrap role is configured to trust tokens that name *this* repository
on `main`, a pull request, or a named environment. AWS swaps that token for
temporary credentials that expire with the job. There is nothing to leak
and nothing to rotate.

**Why the CI role has a permissions boundary.** Terraform has to create IAM
principals - the ECS execution role that pulls images and reads secrets, and
the SES SMTP user. So the CI role needs `iam:CreateRole` and
`iam:PutRolePolicy`, and that pair is an escalation ladder: IAM lets you
write a policy onto a role you created that is *broader than your own*, then
pass or assume it. Naming the roles it may touch does not close that, because
the danger is the policy content, not the target.

A **permissions boundary** does close it. It is a policy attached to a
principal that says "whatever else is granted, never more than this" - the
principal ends up with the intersection. `bootstrap/oidc.tf` creates one
(`resistance-ci-boundary`) listing what those two principals genuinely need:
pull an image, write logs, read the injected secrets, send mail. The CI
role may then create a role or write a policy *only when the target carries
that boundary* (an `iam:PermissionsBoundary` condition), may only pass a role
to `ecs-tasks.amazonaws.com`, and is explicitly denied the two calls that
would take a boundary back off. The worst a compromised pull request can now
reach for is a role that can read this app's own secrets - not an admin.

### Why SNS messages are signature-verified

Anyone who discovers our `/intake/aws-sns` URL can POST JSON to it, and
*everything in that JSON* — including the topic name we allowlist — is
attacker-writable. The one thing that can't be forged is the **signature**:
AWS signs each message with a private key and includes a URL to the
matching certificate. `aws/SnsSignatureVerifier` rebuilds the exact string
AWS signed (the "canonical string" — specific fields in a specific order)
and checks the RSA signature. One subtlety: the certificate URL is also in
the attacker-writable body, so `UrlSigningKeyResolver` refuses any URL that
isn't `https` on an `sns.<region>.amazonaws.com` host — otherwise an
attacker signs with their own key and points us at their own certificate.

### Field encryption (AES-GCM + KMS)

**What:** `AesGcmFieldEncryptor` (in `shared-utils`) encrypts individual
PII values with AES-256-GCM — a mode that both hides the value and detects
tampering. Each value gets a random IV (so equal inputs produce different
ciphertexts) and is stored as `enc:v1:<base64>`; anything without that
prefix is treated as old plaintext and passed through, so turning
encryption on doesn't break existing rows.
**Where does the key come from?** Never from the app. Terraform generates
32 random bytes once, stores them as an SSM SecureString encrypted under a
KMS customer-managed key, and the ECS task definition injects the value as
`TRACKER_ENC_KEY`. Dev runs without a key (plaintext, with a logged
warning). Details in
[`infrastructure/aws/README.md`](../infrastructure/aws/README.md).

---

## Running & shipping

| Thing | What it is | Where |
|---|---|---|
| **Docker / docker-compose** | containers = apps packaged with their environment; compose starts a whole set (MySQL + services + gateway) with one command | `infrastructure/docker-compose.yml`, generic image recipe in `infrastructure/docker/Dockerfile` |
| **Kubernetes manifests** | YAML describing how a cluster should run the same containers (replicas, ports, env) | `infrastructure/kubernetes/` |
| **DB init scripts** | plain SQL that creates schemas and seed rows; both local MySQL and CI mount them | `infrastructure/config/db-init/` |
| **API gateway** | one front door on port 8080 that forwards `/rest-api/**`, `/intake/**` etc. to the right service — a hand-rolled ~80-line proxy, deliberately not a framework | `api-gateway/` |
| **GitHub Actions CI** | on every push, GitHub spins up a runner, starts MySQL with our real init scripts, runs `mvn verify` (compile + all tests, including full Spring context startup), and uploads the built jars; a second job type-checks, tests, and builds the React app with Node | `.github/workflows/build.yml` |
| **CodeQL** | GitHub's static security analysis - scans the Java code for vulnerability patterns on every PR and weekly | `.github/workflows/codeql.yml` |
| **Terraform workflow** | on a pull request: format, validate, a security scan and a plan for both environments; on a push to `main`: applies `dev`; `prod` applies only from a manual run | `.github/workflows/terraform.yml` |
| **Deploy workflow** | manual: builds the two service jars once, stamps an image per service from `Dockerfile.runtime`, pushes to ECR, and tells ECS to roll | `.github/workflows/deploy.yml` |
| **Health endpoint** | `/actuator/health` answers `{"status":"UP"}` when the app and its database connection are fine; the load balancer polls it and replaces a task that stops answering | Spring Boot Actuator, exposed in `application.properties`, permitted in `SecurityConfig` |
| **Dependabot** | opens PRs when Maven dependencies or Actions versions have updates (which often carry security fixes) | `.github/dependabot.yml` |

The CI detail worth appreciating: because it boots a *real* MySQL with the
*real* schemas, it catches whole classes of bugs a compile can't — wrong
derived-query names, entities drifting from the SQL, demo runners assuming
data that isn't seeded. All three have actually happened in this repo's
history and were caught exactly there.

---

## Cheat sheet: where to look when...

| You want to understand... | Read |
|---|---|
| How an email becomes a tracked application | `intake-service/.../service/IntakeService.java` (top to bottom) |
| How parsing decides company/position/status | `parser/HeuristicConfirmationEmailParser.java`, then `parser/claude/` |
| How login works without passwords | `mvc-service/.../auth/OtpService.java` |
| Who may access which page | `mvc-service/.../auth/SecurityConfig.java` |
| How the React app is wired together | `frontend/src/App.tsx`, then `pages/DashboardPage.tsx` |
| What JSON the SPA sends and receives | `mvc-service/.../api/` records + `frontend/src/api/types.ts` |
| How a fetch call carries login + CSRF | `frontend/src/api/client.ts` |
| What gets audited and where events go | `shared-utils/.../audit/AuditEventClient.java` + `docs/E2E-TEST-PLAN.md` |
| Why another user's data is invisible | `mvc-service/.../service/JobApplicationServiceImpl.java` + `JobApplicationOwnershipTests` |
| Where status changes are recorded and announced | `StatusHistory` entity + `intake-service/.../notify/` |
| How a page gets its data | `controller/JobApplicationController.java` + matching template |
| What a table looks like | the `@Entity` class **and** its `CREATE TABLE` in `db-init/02-job-tracker.sql` |
| Why qa won't start | `application-qa.properties` (placeholders with no defaults) |
| What AWS resources exist, and what they cost | `infrastructure/terraform/environments/dev/main.tf`, then the modules it calls; cost table in `infrastructure/terraform/README.md` |
| Why one thing is applied by hand and the rest from CI | `infrastructure/terraform/bootstrap/` (read the comments at the top of each file) |
| Where `DB_PASSWORD` and the other secrets come from on AWS | `modules/secrets/main.tf`, then the `secrets` list in `modules/app/main.tf` |
| Why there is no NAT gateway, and what that costs | the comment at the top of `modules/network/main.tf` |
| How the tables get created on an empty RDS | `application-qa.properties` (`spring.sql.init`) + `shared-models/.../db/job-tracker-schema.sql` + `SchemaFilesInSyncTests` |
| What CI actually runs | `.github/workflows/build.yml` |

If a class puzzles you, its Javadoc comment states the *why*; the unit
tests next to it (`src/test/java/...`) show the intended behavior with
concrete examples — often the fastest explanation of all.
