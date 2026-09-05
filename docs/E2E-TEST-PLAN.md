# End-to-End Integration Test Plan — Resistance ↔ AuditFlow

**System under test:** the full audit pipeline across two projects:

```
                    PUSH PATH                                   AuditFlow platform
Resistance ──┐
  mvc-service    login/data/profile events ──┐
  intake-service provisioning/intake events ─┤
                                             ▼
                              POST /api/v1/events (X-Audit-Token)
                                             │
Resistance MySQL ── general_log ── collector-agent ──┘        PULL PATH
                    (redacted client-side)
                                             │
                     ingestion-service ─▶ Kafka ─▶ enrichment-service
                                                        │         │
                                                   S3 evidence  Aurora/Postgres
                                                   (immutable)  (queryable)
                                                        │
                                    alerting RuleEngine (SpEL conditions)
```

**Scope:** correctness of event flow, security controls (auth, redaction,
tenancy, rate limits), the read side (gateway API: audit logs, alerts,
rule management, reports over stored evidence), and resilience (either
system down). **Out of scope for now:** the S3/Athena lake path for very
large report windows, and the React UI (covered by its own Vitest suite).

---

## 0. What automation already proves (no manual effort)

| Layer | Suite | Proves |
|---|---|---|
| Resistance unit | `AuditEventClientTests` | JSON escaping, token header, fire-and-forget never blocks/throws, disabled mode |
| Resistance unit | `JobApplicationOwnershipTests`, `ContactOwnershipTests`, `IntakeServiceTests` | emissions fire on CREATE/UPDATE/DELETE/provisioning — and **not** on refused foreign-owner operations; applications and contacts are both scoped to the acting account |
| Resistance unit | `ContactControllerTests` | the owner id comes from the session only; another user's contact is a redirect, not a form |
| AuditFlow unit | `IngestTokenFilterTest` | open/dev mode, constant-time match, 401 on wrong/missing token |
| AuditFlow unit | `QueryRedactorTest`, `MySqlGeneralLogCollectorTest` | PII literals stripped, noise filtered, deterministic ids |
| AuditFlow IT (CI) | `EventIngestionIntegrationTest` | HTTP → Kafka against a real broker |
| AuditFlow IT (CI) | `MySqlGeneralLogCollectorIntegrationTest` | a PII-bearing query round-trips **redacted** from a real MySQL general log; checkpoint holds until committed |
| AuditFlow IT (CI) | `AuroraWriterAdapterIntegrationTest` | idempotent insert against real Postgres, controls persisted |
| AuditFlow unit | `CognitoJwtAuthTest` | gateway accepts a valid Cognito ID token for the app client and rejects expired / wrong-issuer / wrong-audience / access / unsigned / no-tenant tokens |
| AuditFlow IT (CI) | `RepositoriesIntegrationTest` | audit logs, alerts and rules are scoped per customer against real Postgres; a cross-tenant upsert cannot hijack a row |
| AuditFlow IT (CI) | `AlertingEndToEndTest` | seed rules → Postgres → Kafka → real Slack webhook call → `alert_history` row |
| AuditFlow unit | `AlertRuleControllerTest`, `ConditionEvaluatorTest` | a rule with `T(java.lang.Runtime)` or a non-boolean condition is a 400 at write time |
| AuditFlow unit | `RateLimitFilterTest` ×2, `TokenBucketLimiterTest` | burst then 429 + Retry-After per client on the gateway and on ingestion |

The manual scenarios below cover only what crosses the repo boundary.

---

## Demo in five commands

The shortest path from "a person mistypes a login code in Resistance" to
"a compliance platform shows the alert". Two terminals, both repos.

```bash
# auditflow-platform: the whole platform in containers (jars first)
mvn -DskipTests clean package && AUDIT_INGESTION_TOKEN=e2e-secret docker compose --profile app up --build -d

# Resistance: MySQL + the tracker, pointed at the platform
docker compose -f infrastructure/docker-compose.yml up -d mysql
TRACKER_AUDIT_URL=http://localhost:8081 TRACKER_AUDIT_TOKEN=e2e-secret mvn -pl services/mvc-service -am spring-boot:run
```

Now fail a login at `http://localhost:8085/login` (wrong code), then:

```bash
curl -s -H 'X-Customer-Id: resistance' 'localhost:8080/api/v1/audit-logs?type=AUTH_EVENT&limit=3'   # the LOGIN_FAILURE, with SOC 2 controls
curl -s -H 'X-Customer-Id: resistance' localhost:8080/api/v1/alerts                                   # "Failed login attempt" fired
curl -s -H 'X-Customer-Id: resistance' localhost:8080/api/v1/reports/soc2                             # it is on the evidence report
```

`X-Customer-Id` stands in for the verified Cognito claim while the
gateway runs with auth open; in the cloud the same calls carry a bearer
token and the header is ignored.

---

## 1. Environment (local, one machine)

Port plan — start **only** what's listed (Resistance's api-gateway/core
would collide with AuditFlow's 8080/8081):

| Component | Port | How to start |
|---|---|---|
| Resistance MySQL | 3306 | `docker compose -f infrastructure/docker-compose.yml up -d mysql` (Resistance repo) |
| AuditFlow (all of it) | 9092 / 5432 / 4566 + 8080–8084 | `mvn -DskipTests clean package && AUDIT_INGESTION_TOKEN=e2e-secret docker compose --profile app up --build -d` (auditflow-platform repo; the evidence bucket is created by an init hook). Or `docker compose up -d` for infrastructure only and `mvn -pl services/<name> spring-boot:run` per service with the same env var. |
| AuditFlow api-gateway | 8080 | part of the profile above; auth open, `X-Customer-Id: resistance` on every call |
| Resistance mvc-service | 8085 | `TRACKER_AUDIT_URL=http://localhost:8081 TRACKER_AUDIT_TOKEN=e2e-secret mvn -pl services/mvc-service -am spring-boot:run` |
| Resistance intake-service | 8087 | same two env vars, `-pl services/intake-service` |
| collector-agent | – | E2E-6 only: `AGENT_INGESTION_TOKEN=e2e-secret mvn -pl agent/collector-agent spring-boot:run` (auditflow-platform repo) |

Verification helpers (used by several scenarios):

```bash
# The read API (what a customer would see)
API="curl -s -H 'X-Customer-Id: resistance' http://localhost:8080/api/v1"

# Aurora-side evidence (queryable metadata)
PSQL="docker exec -it auditflow-postgres psql -U auditflow -d auditflow"

# S3-side evidence (immutable store)
S3LS="aws --endpoint-url=http://localhost:4566 s3 ls s3://auditflow-events/resistance/"

# Raw Kafka view when debugging a gap between ingestion and enrichment
docker exec -it auditflow-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic audit-events --from-beginning
```

---

## 2. Scenarios

### E2E-1 — Push path happy flow (auth events)

1. Visit `http://localhost:8085/login`, request a code for a seeded user
   (`demo@resistance.com` works with the seed data), read the code from the
   mvc-service log, log in.
2. Log in once more with a deliberately wrong code first.

**Expect** within ~5s, in Aurora (`$PSQL`):
```sql
SELECT event_type, action, user_id, anomalous FROM audit_events
 WHERE customer_id = 'resistance' ORDER BY occurred_at DESC LIMIT 5;
```
rows for `AUTH_EVENT/OTP_REQUESTED`, `AUTH_EVENT/LOGIN_FAILURE`,
`AUTH_EVENT/LOGIN_SUCCESS` with the login email as `user_id`; matching
JSON objects under `$S3LS`; and the same three through the read API
(`$API/audit-logs?type=AUTH_EVENT&limit=3`, each with `controls`
`SOC2:AC-2,SOC2:IA-2`). **Fail if** any event is missing, lands under a
different `customer_id`, Aurora and S3 disagree on count, or
`X-Customer-Id: someone-else` returns any of them.

### E2E-2 — Data + intake events, tenancy intact

1. In the UI (or React app on 5173): create an application, edit its
   status, delete another, open `/profile`.
2. `curl` the intake webhook (README's smoke-test payload) twice — second
   forward must be a no-op.
3. Contact tenancy: as user A add a contact and note its id from the edit
   link. Sign in as user B (forward from a second address to get an
   account) and open `/contacts/list` — A's contact must not be listed.
   Then `GET /contacts/showFormForUpdate?contactId=<A's id>` as B: expect a
   redirect to `/contacts/list`, not A's data. Finally post an application
   as B with `contact=<A's id>` in the form body: expect the save to be
   refused rather than the application linking to A's recruiter.

**Expect:** `DATABASE_QUERY/CREATE|UPDATE|DELETE` with
`resource = job_application:<id>`, `FILE_ACCESS/PROFILE_VIEW`,
`AUTH_EVENT/ACCOUNT_PROVISIONED` + `DATABASE_QUERY/INTAKE_CREATE` from
intake — and **exactly one** INTAKE_CREATE despite the duplicate forward.
Every row's `user_id` is the acting account, never another user's.

### E2E-3 — Ingestion auth (negative)

```bash
curl -si -X POST localhost:8081/api/v1/events -H 'Content-Type: application/json' \
  -d '{"eventId":"forged-1","customerId":"resistance","type":"AUTH_EVENT"}'                    # no token
curl -si ... -H 'X-Audit-Token: wrong' -d '{...}'                                              # bad token
```
**Expect:** both 401, nothing in Kafka/Aurora; the same payload **with**
`X-Audit-Token: e2e-secret` returns 202. **Fail if** any unauthenticated
write lands — that would mean forgeable audit evidence.

### E2E-4 — Resilience: auditing down, product up

1. Stop ingestion-service. Log in to Resistance; create/edit applications.
2. **Expect:** every user action succeeds at normal speed; mvc log shows
   `Audit event not delivered` warnings, no stack traces, no 500s.
3. Restart ingestion-service, act again → new events flow. Events from the
   outage window are **absent — that is the documented at-most-once
   tradeoff of the push path**, not a failure (contrast with E2E-6 step 3).

### E2E-5 — Alert rule fires on real traffic, managed through the API

1. The seeded rule is already there (`$API/alert-rules` lists
   `resistance-login-failures`). Add one of your own:
```bash
curl -s -X POST localhost:8080/api/v1/alert-rules -H 'X-Customer-Id: resistance' \
  -H 'Content-Type: application/json' \
  -d '{"name":"E2E login failures","eventType":"AUTH_EVENT","conditionExpression":"action == '"'"'LOGIN_FAILURE'"'"'","notificationChannels":["slack"]}'
```
   **Expect** 201 with a server-generated `ruleId` and `customerId`
   `resistance` regardless of what the body said.
2. Fail a login 3×. **Expect** within ~40 s (alerting reloads rules every
   30 s): `$API/alerts` lists one row per matching event **per rule** with
   `ruleName` and `notifiedChannels` (`slack` if
   `ALERT_SLACK_WEBHOOK_URL` is set, otherwise empty and alerting-service
   logs `[slack:not configured]`). `psql`: `SELECT count(*) FROM
   alert_history` agrees.
3. Injection probe, at the door:
```bash
curl -s -o /dev/null -w '%{http_code}\n' -X POST localhost:8080/api/v1/alert-rules -H 'X-Customer-Id: resistance' \
  -H 'Content-Type: application/json' \
  -d '{"name":"evil","conditionExpression":"T(java.lang.Runtime).getRuntime() != null"}'
```
   **Expect** `400` — refused at write time by the same sandbox alerting
   runs. Then bypass the API and insert that condition straight into
   `alert_rules` with `$PSQL`; fail a login. **Expect** zero matches and a
   WARN in alerting-service (defense in depth: the sandbox holds even
   against a rule that never went through the gateway).
4. Tenancy: `curl -H 'X-Customer-Id: other-co' localhost:8080/api/v1/alerts`
   → `[]`; `DELETE` of your rule's id with `other-co` → `404`; with
   `resistance` → `204` and alerting stops matching it within 30 s.
5. Rate limit: hit `$API/alerts` 60× in a tight loop. **Expect** some
   `429` with `Retry-After: 1` and `X-RateLimit-Remaining: 0`, then
   normal service a second later.

### E2E-6 — Pull path: agent, redaction, at-least-once

1. On Resistance MySQL (root creds from docker-compose):
   `SET GLOBAL log_output='TABLE'; SET GLOBAL general_log='ON';`
2. Start collector-agent (env per §1), then use the Resistance UI so real
   owner-scoped SQL runs.
3. **Expect in Aurora:** `DATABASE_QUERY` rows with
   `resource='resistance-mysql'`, queries shaped like
   `SELECT ... WHERE owner_account_id = ?` — grep the evidence for any
   seeded email/phone: **must find nothing** (redaction gate).
4. Stop ingestion-service for one poll, restart: **expect** the held batch
   redelivered, and **no duplicate** `event_id` rows in Aurora (deterministic
   ids + idempotent insert absorb the replay).
5. Turn `general_log` **off** when done — it grows fast.

### E2E-7 — Cloud variant (optional, costs money while up)

Same assertions as E2E-1/E2E-3 against the deployed stack: ECS services up
(`ecs_enabled=true`), Resistance's qa profile pointed at the ALB-fronted
ingestion via `TRACKER_AUDIT_URL`, evidence checked in the real Object-Locked
bucket and Aurora. Adds one cloud-only check: the API Gateway endpoint
rejects unauthenticated calls with 401 from the **Cognito authorizer**
(before our token filter is even reached).

---

## 3. Exit criteria

- The read API answers every question the scenarios ask (audit logs,
  alerts, rules, a SOC 2 report) scoped to `resistance`, and answers
  nothing for another `X-Customer-Id`.

**Pass:** every scenario's expectations hold; zero PII in stored evidence
(E2E-6.3 grep is the hard gate); no user-facing Resistance failure in E2E-4;
no forged or cross-tenant event anywhere.

**Known gaps this plan accepts** (tracked in both CLAUDE.mds): push-path
events during an AuditFlow outage are lost (outbox = future work); evidence
is verified by SQL/S3 inspection because the read API is stubbed; report
generators run against unit fixtures, not live evidence.

## 4. Automation roadmap

The highest-value follow-up slice: a **cross-repo E2E job in
auditflow-platform's CI** — docker-compose both stacks (compose already
exists on each side), run E2E-1/2/3 as a script of curl + psql assertions,
E2E-6's redaction grep included. Everything above was designed so that job
is a transcription of this document, not new thinking.
