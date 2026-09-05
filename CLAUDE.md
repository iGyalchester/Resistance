# CLAUDE.md — Resistance

## What this is

A production-style **job application tracker**, built as a Spring Boot 4
multi-module Maven monorepo (Java 26). The domain model was originally
seeded from a Spring Boot course codebase and has since been fully
rewritten. The core product idea: forward a "we received your
application" email to your personal intake address (`track+<alias>@domain`)
and the tracker parses it, files it under your auto-provisioned account,
and follows the status over time. Login is passwordless (emailed one-time
codes). A React dashboard is growing alongside the original Thymeleaf UI.

**Read `docs/TECH-GUIDE.md` first** — a plain-language tour of every
technology in the stack, kept current by convention (see below).

## Owner context

- Owner: **Boris Gerard** (GitHub `iGyalchester`). Associate Software
  Engineer at JPMorganChase through Aug 2026; IBM ODM V8.10 Developer
  certified (Decision Center / Rule Designer / Decision Server), AWS Cloud
  Practitioner. Strong Java/Spring Boot; actively building React/TypeScript
  and cloud-architecture depth.
- This repo is one third of his portfolio ("my stack") together with
  `auditflow-platform` (event-driven compliance SaaS backbone) and
  `auditflow-infrastructure` (its Terraform/AWS).
- He reads code well, but parts of this stack are new to him — explain
  choices in plain language (the TECH-GUIDE style), not just diffs.

## Working conventions (established — follow them)

- **Branch model**: slice branches off `develop`, PR into `develop`;
  `main` is the stable branch, promoted via a develop→main PR. Never
  merge or approve PRs — Boris merges them himself.
- **One PR per slice** of work; **write tests for each slice before
  moving to the next**.
- **Update `docs/TECH-GUIDE.md` and `README.md` with every feature
  addition** — plain-language explanations of any new technology.
- Local containers usually have JDK 21 while the repo targets 26: verify
  with `mvn -B ... -Djava.version=21`. Spring-context tests need MySQL and
  run in CI only — locally exclude them
  (`-Dtest='*Tests,!MvcServiceApplicationTests'`).
- Frontend checks: `cd frontend && npm ci && npm run build && npm test -- --run`.

## Map

- `services/mvc-service` (8085) — the real app: Thymeleaf pages **and**
  the `/api/**` JSON API for React; OTP auth, session security,
  owner-scoping at the service layer, CSRF (cookie repo for the SPA).
- `frontend/` — Vite + React 19 + TypeScript SPA (login + dashboard so
  far); dev server proxies `/api` to 8085.
- `services/intake-service` (8087) — email intake via webhook, AWS
  SES→SNS (signature-verified), or IMAP; heuristic parsing with optional
  Claude fallback; the recipient **alias is the trust anchor**, never the
  From header.
- `services/rest-api-service` (8083) — deliberately **unsecured legacy
  demo**; never expose it publicly.
- `infrastructure/` — docker-compose, Kubernetes, `terraform/` (AWS by
  code: `bootstrap/` once by hand, modules, `dev` + `prod` environments,
  cost gated on `app_enabled`), and `config/db-init/` (the SQL schema
  source of truth).
- Profiles: `dev` (default) is fully local; `qa` requires real AWS
  resources via fail-fast env vars (`infrastructure/aws/README.md`),
  which the Terraform app module injects.

## Roadmap

React slices remaining: application/contact CRUD → status-history
timeline view → profile page + shipping the built app in docker-compose,
then decide on retiring Thymeleaf. Deferred backlog: CD via GitHub
OIDC→ECS, production DB choice (DynamoDB vs DocumentDB), deterministic
encryption for email lookup fields, SMS OTP delivery.
