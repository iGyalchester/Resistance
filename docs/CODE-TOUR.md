# Code tour — read Resistance from the front door to the database

This is for someone who wants to understand the tracker by **reading the
code**. It follows the two things that happen in this system — a person
logging in and looking at their applications, and a forwarded email
becoming a row — and at each stop tells you which file to open, what to
notice, and which test proves it. If a technology is new to you, read its
section in `docs/TECH-GUIDE.md` first; that guide explains *what* Spring
Security or JPA is, this one shows *where we use it and why*.

Before you start:

- The real app is `services/mvc-service` (web + JSON API) and
  `services/intake-service` (email in). Everything else under `services/`
  is a small demo kept for reference; `shared/` holds what both real
  services need.
- `mvn -B clean install -Djava.version=21` runs the unit tests without a
  database; the tests that boot Spring against MySQL run in CI.
  `cd frontend && npm ci && npm test -- --run` for the React side.

---

## Stop 0 — The data (`shared/shared-models`, `infrastructure/config/db-init`)

- `shared/shared-models/src/main/java/com/resistance/shared/models/entity/JobApplication.java`
  — the central record. Notice `owner_account_id`: every application
  belongs to exactly one account, and everything at Stop 2 is about
  enforcing that.
- `UserAccount.java` (same folder) — a person, identified by email, with an
  **intake alias**. `LoginCode.java` — a hashed one-time code with an
  expiry. `StatusHistory.java` — one row per status change, so the
  timeline is never lost.
- `infrastructure/config/db-init/02-job-tracker.sql` — the schema as the
  database sees it. Read this once next to the entity classes; the column
  names map one-to-one.

---

## Stop 1 — Logging in without a password (`services/mvc-service`, package `auth`)

1. `auth/SecurityConfig.java` — which URLs are public (`/login`, the code
   endpoints, static assets) and which need a session. Notice the CSRF
   setup: the token lives in a cookie the React app can read, and
   `auth/SpaCsrfTokenRequestHandler.java` is the small piece Spring needs
   for that to be safe. Also notice `/api/**` gets a JSON 401 instead of a
   redirect to the login page — a browser wants the redirect, a SPA wants
   the status code.
2. `auth/LoginController.java` (the HTML form) and
   `api/AuthApiController.java` (the JSON API) — two front doors, same
   sequence. Read one, then the other, and notice they share the next three
   classes so they can never drift apart.
3. `auth/OtpRequestThrottle.java` — how many codes one email or one IP may
   request. Runs before anything is sent.
4. `auth/OtpService.java` — issues a 6-digit code, stores only its SHA-256
   hash, verifies with expiry and attempt limits. Read `requestCode`: an
   unknown email gets the *same* response as a known one, on purpose (no
   account enumeration).
5. `auth/SessionAuthenticator.java` — turns "code verified" into "logged
   in": rotates the session id (fixation protection), records the account
   id, stores the Spring Security context. This is the one place that
   knows the full sequence.
6. `auth/LoginCodePurgeJob.java` — expired codes do not live forever.

Proof: `OtpServiceTests`, `OtpRequestThrottleTests`,
`AuthApiControllerTests` (including the session-rotation case that caught
a real bug), `LoginCodePurgeJobTests`.

---

## Stop 2 — Your applications, and only yours (`services/mvc-service`, packages `service`, `controller`, `api`)

1. `service/JobApplicationService.java` then
   `service/JobApplicationServiceImpl.java` — **every** method takes the
   owner. Notice there is no "find by id" without an owner; a lookup for
   someone else's row is a not-found, not a permission error. This is
   where tenancy is enforced, deliberately below the controllers so no
   controller can forget.
2. `controller/JobApplicationController.java`,
   `controller/ContactController.java`, `controller/DashboardController.java`
   — the Thymeleaf pages. They read the account id from the session and
   pass it down; they never trust an id from the URL alone.
3. `api/ApplicationApiController.java` + `api/ApplicationView.java` — the
   same data as JSON for React. Notice the DTO: entities are never
   serialized directly (lazy proxies, and the owner field would leak).
4. `controller/ProfileController.java` — profile view/update; it is also
   an audit emit point (Stop 4).
5. `dao/` — Spring Data repositories. Mostly empty interfaces; the method
   names *are* the queries.
6. Encryption of sensitive columns: `auth/EncryptionConfig.java` chooses
   the key (KMS in `qa`, a local one in `dev`), and
   `shared/shared-utils/.../crypto/AesGcmFieldEncryptor.java` does the
   work through a JPA attribute converter, so entities stay plain.

Proof: `JobApplicationOwnershipTests` — read this one even if you skip the
others; it is the tenancy contract as executable examples.
`ApplicationApiControllerTests`, `AesGcmFieldEncryptorTests`.

---

## Stop 3 — A forwarded email becomes a row (`services/intake-service`)

Three ways in, one service, one parser chain:

1. The doors:
   - `web/SnsIntakeController.java` — AWS SES delivers mail as an SNS
     notification. **Before** the body is trusted,
     `aws/SnsSignatureVerifier.java` checks Amazon's signature using a
     certificate fetched by `aws/SigningKeyResolver.java`. Read the
     verifier: it also refuses certificate URLs that are not Amazon's.
   - `web/EmailIntakeController.java` — a plain webhook for local testing.
   - `imap/ImapPollingService.java` — polls a mailbox for people without
     AWS.
2. `service/IntakeService.java` — the heart of intake. Read it top to
   bottom:
   - the **recipient alias** (`track+<alias>@…`) decides which account the
     email belongs to. Never the From header, which anyone can forge. The
     comment says so; so does `util/IntakeAddresses.java` in mvc-service,
     which mints those aliases.
   - an unknown alias can **auto-provision** an account.
   - a confirmation creates an application; a status email updates one and
     writes `StatusHistory`; a recruiter mentioned in the mail becomes a
     `Contact`.
3. The parsers, tried in order by `parser/ParserConfig.java`:
   `parser/HeuristicConfirmationEmailParser.java` (regex, free, handles the
   common shapes) then `parser/claude/ClaudeConfirmationEmailParser.java`
   (an LLM for the hard ones, optional), behind
   `parser/FallbackConfirmationEmailParser.java`. Read the Claude parser's
   input sanitisation and its test: email text is untrusted input to a
   model, and the test throws prompt-injection at it.
4. `notify/StatusNotifier.java` and `notify/WeeklyDigestJob.java` — what
   goes back out to the person.

Proof: `SnsSignatureVerifierTests`, `IntakeServiceTests`,
`HeuristicConfirmationEmailParserTests`, `ClaudeParserSanitizationTests`,
`NotificationsTests`.

---

## Stop 4 — Audit events out to AuditFlow (`shared/shared-utils`, both services)

1. `shared/shared-utils/src/main/java/com/resistance/shared/utils/audit/AuditEventClient.java`
   — pure JDK, asynchronous, two-second cap, swallows every failure. Read
   the class comment: auditing must never break the app, so delivery is
   **at-most-once by design**, and the trade-off is written down rather
   than hidden. Blank URL means disabled, which is the dev default.
2. `mvc/audit/AuditConfig.java` and `intake/audit/AuditConfig.java` — the
   bean, from `tracker.audit.*` properties.
3. The emit points, which are the security seams and nothing else: OTP
   requested / login success / login failure (Stop 1 controllers), create
   / update / delete of an application (`JobApplicationServiceImpl`, after
   the ownership check — a refused foreign-owner call emits nothing),
   profile view / update (`ProfileController`), account provisioned and
   intake create / update (`IntakeService`).
4. What happens on the other side is the AuditFlow repo's own
   `docs/CODE-TOUR.md`, Stop 1 onward. The diagram in this README shows
   both halves.

Proof: `AuditEventClientTests` (including "emit returns in under a second
while the server hangs"), and the emission cases inside
`JobApplicationOwnershipTests` and `IntakeServiceTests`.

---

## Stop 5 — The React front end (`frontend/src`)

1. `main.tsx` → `App.tsx` — the routes: two login pages and the dashboard,
   with a guard that asks `/api/auth/me` once.
2. `api/client.ts` — the only place `fetch` is called. Notice it reads the
   CSRF cookie into the `X-XSRF-TOKEN` header (the other half of Stop 1's
   cookie setup) and turns a 401 into "go to login".
3. `auth/AuthContext.tsx` — who is logged in, for the rest of the tree.
4. `pages/LoginEmailPage.tsx`, `pages/LoginCodePage.tsx`,
   `pages/DashboardPage.tsx` — the screens; `api/types.ts` mirrors the
   Java DTOs from Stop 2.
5. In development, Vite proxies `/api` to port 8085 (`vite.config.ts`), so
   the browser sees one origin and the session cookie just works.

Proof: `src/test/client.test.ts`, `src/test/LoginFlow.test.tsx`,
`src/test/DashboardPage.test.tsx`.

---

## Stop 6 — How it runs

- `infrastructure/docker-compose.yml` — MySQL plus the services; the
  `db-init` folder from Stop 0 is mounted so a fresh database has the
  schema and seed data.
- `application.properties` in each service: the `dev` profile is fully
  local; `qa` requires real AWS values and fails fast without them
  (`infrastructure/aws/README.md`).
- `.github/workflows/build.yml` (Maven with a MySQL service container, and
  the frontend job) and `codeql.yml`.

---

## If you only have an hour

Stop 0, then `SecurityConfig` → `OtpService` → `SessionAuthenticator` →
`JobApplicationServiceImpl` → `IntakeService` → `AuditEventClient`, and
read `JobApplicationOwnershipTests`. That is the spine.

## Where this is going

The React app still covers only login and the dashboard (application and
contact editing remain Thymeleaf), and the roadmap in `CLAUDE.md` lists
what comes next. The AuditFlow side of the story — where these audit
events become alerts and compliance reports — is documented in that repo's
own code tour.
