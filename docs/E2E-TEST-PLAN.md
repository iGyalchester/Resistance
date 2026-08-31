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
tenancy), and resilience (either system down). **Out of scope for now:**
the AuditFlow read API (gateway controllers are placeholders), report
generation against live evidence (unit-tested only), and the React UI
(covered by its own Vitest suite).

---

## 0. What automation already proves (no manual effort)

| Layer | Suite | Proves |
|---|---|---|
| Resistance unit | `AuditEventClientTests` | JSON escaping, token header, fire-and-forget never blocks/throws, disabled mode |
| Resistance unit | `JobApplicationOwnershipTests`, `IntakeServiceTests` | emissions fire on CREATE/UPDATE/DELETE/provisioning — and **not** on refused foreign-owner operations |
| AuditFlow unit | `IngestTokenFilterTest` | open/dev mode, constant-time match, 401 on wrong/missing token |
| AuditFlow unit | `QueryRedactorTest`, `MySqlGeneralLogCollectorTest` | PII literals stripped, noise filtered, deterministic ids |
| AuditFlow IT (CI) | `EventIngestionIntegrationTest` | HTTP → Kafka against a real broker |
| AuditFlow IT (CI) | `MySqlGeneralLogCollectorIntegrationTest` | a PII-bearing query round-trips **redacted** from a real MySQL general log; checkpoint holds until committed |
| AuditFlow IT (CI) | `AuroraWriterAdapterIntegrationTest` | idempotent insert against real Postgres |

The manual scenarios below cover only what crosses the repo boundary.

---

## 1. Environment (local, one machine)

Port plan — start **only** what's listed (Resistance's api-gateway/core
would collide with AuditFlow's 8080/8081):

| Component | Port | How to start |
|---|---|---|
| Resistance MySQL | 3306 | `docker compose -f infrastructure/docker-compose.yml up -d mysql` (Resistance repo) |
| AuditFlow Kafka / Postgres / LocalStack | 9092 / 5432 / 4566 | `docker compose up -d` (auditflow-platform repo) + create the `auditflow-events` bucket (README step 2) |
| AuditFlow ingestion-service | 8081 | `AUDIT_INGESTION_TOKEN=e2e-secret mvn -pl services/ingestion-service spring-boot:run` |
| AuditFlow enrichment-service | 8082 | `mvn -pl services/enrichment-service spring-boot:run` |
| AuditFlow alerting-service | 8083 | only for E2E-5 |
| Resistance mvc-service | 8085 | `TRACKER_AUDIT_URL=http://localhost:8081 TRACKER_AUDIT_TOKEN=e2e-secret mvn -pl services/mvc-service -am spring-boot:run` |
| Resistance intake-service | 8087 | same two env vars, `-pl services/intake-service` |
| collector-agent | – | E2E-6 only: `AGENT_INGESTION_TOKEN=e2e-secret mvn -pl agent/collector-agent spring-boot:run` (auditflow-platform repo) |

Verification helpers (used by several scenarios):

```bash
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
JSON objects under `$S3LS`. **Fail if** any event is missing, lands under a
different `customer_id`, or Aurora and S3 disagree on count.

### E2E-2 — Data + intake events, tenancy intact

1. In the UI (or React app on 5173): create an application, edit its
   status, delete another, open `/profile`.
2. `curl` the intake webhook (README's smoke-test payload) twice — second
   forward must be a no-op.

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

### E2E-5 — Alert rule fires on real traffic

1. Seed a rule (`$PSQL`):
```sql
INSERT INTO alert_rules (rule_id, customer_id, name, event_type, condition_expression, enabled)
VALUES ('r-e2e-1', 'resistance', 'Login failures', 'AUTH_EVENT', 'query == null', true);
```
2. Fail a login 3×. **Expect:** alerting-service logs `[slack] rule=r-e2e-1 ...`
   per matching event. Then set `condition_expression` to
   `T(java.lang.Runtime).getRuntime() != null` → **expect zero matches and a
   WARN** (sandbox holds against a live injection payload).

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
