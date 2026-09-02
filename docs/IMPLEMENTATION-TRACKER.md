---
Document ID: IMPLEMENTATION-TRACKER
Title: Hippocampus v1 Detailed Implementation Tracker
Version: 1.0.0
Status: Active
Owner: Project Hippocampus Team
Created: 2026-08-24
Last Updated: 2026-09-01
Purpose: Operational tracker for implementing the frozen Hippocampus v1 Source of Truth phase by phase with concrete build requirements, tests, expected behavior, definition of done, and evidence.
Authority: Document 26 defines implementation order; Documents 00–25 define product/technical requirements; Document 27 governs deviations.
---

# Hippocampus v1 Detailed Implementation Tracker

> **A task is not Done because code exists. It is Done only when the required build exists, the listed validation passes, expected behavior is demonstrated, the Definition of Done is satisfied, and evidence is recorded.**

## Operational Progress Source

This Markdown file is the single live source for implementation task status, blockers, phase outcomes, and completion evidence. Pull requests, commits, CI runs, reviews, and ADRs are evidence referenced here; they are not competing progress sources. `docs/Hippocampus-v1-Implementation-Tracker.xlsx` is a frozen planning/export/reference artifact and is not operational for current status. It is not synchronized with this tracker.

## Status Rules and Task Lifecycle

- **Not Started** — accepted implementation work has not begun. Planning alone does not require a persisted status change.
- **In Progress** — implementation or required validation has begun and remains incomplete across a durable repository state or handoff. Do not create an `In Progress` commit only for ceremony; an atomic Cloud task may move directly from `Not Started` to `Ready for Review` when implementation and all validation available to that environment complete together.
- **Blocked** — a real unresolved dependency, required decision, required external capability, or condition prevents responsible progress. Record what is blocked, the affected validation or Definition of Done item, what is needed, and whether reviewer or ADR action is required. After resolution, clear the current blocker, retain concise resolution history, and return to the justified state: `Not Started`, `In Progress`, or `Ready for Review`. Do not move directly from `Blocked` to `Done` without satisfying completion requirements.
- **Ready for Review** — the intended implementation and tracker build requirements form a complete review candidate; available required validation has actually run; applicable behavior, Definition of Done, authority, architecture, and security assessments are recorded; limitations are explicit; and no known undocumented architecture deviation remains. An implementation agent may declare this state, but it is neither approval nor completion. Review may return the task to `In Progress` or `Blocked` when justified.
- **Done** — applicable final facts establish that the implementation is merged into the reviewed target branch, task-required validation and GitHub Actions checks are green, external implementation review is accepted, expected behavior and Definition of Done are satisfied, final evidence is recorded, current blockers are cleared, limitations are accurate, and any required ADR/document approval is accepted and referenced. An implementation agent must not mark its own work `Done` before required external completion facts exist.

An unavailable tool or service is an **environment limitation**, not automatically a blocker, when responsible implementation can continue and the task can be prepared for authoritative external validation. It becomes a blocker only when it prevents responsible progress or the criteria for the proposed task state. Never turn an environment failure into a pass.

## Evidence Rules

Evidence must be factual and applicable to the owning task. It may include exact local or Cloud validation commands and results, manual behavior verification, PR numbers/links, implementation or merge commit SHAs, workflow run numbers/IDs/links, relevant stable job names and actual conclusions, security/architecture review, accepted ADRs, environment limitations, blockers, and later resolution evidence.

At `Ready for Review`, record implementation completion, available validation results, applicable behavior evidence, factual limitations, scope/authority and security/architecture/ADR assessments, unresolved blockers, and a Definition of Done assessment. Add PR or commit information only once factual; merge evidence is not required merely to enter `Ready for Review`.

At `Done`, additionally record applicable accepted-PR and merge evidence, required GitHub Actions results, external review acceptance, final Definition of Done satisfaction, blocker resolution, and required accepted ADR/document updates. Prefer relevant stable job names such as `backend-quality`, `frontend-quality`, and `security` over a vague "CI passed"; `security-monitoring` is task-specific, not universal.

Do not record speculative "will pass" claims, claim CI passed before it completes, fabricate PRs, URLs, SHAs, or workflow/run IDs, backfill unsupported history, call skipped jobs passed, or suppress required failed/unavailable validation to reach `Done`.

## PR, Review, and Completion Flow

Each implementation PR identifies its tracker task and proposed state, summarizes scope and authorities, reports actual validation and Definition of Done assessment, links factual evidence, assesses security/architecture/ADR impact, and records current blockers. The PR is an evidence and review surface; this file remains the progress source.

Final completion evidence must be committed here after it becomes factual. A small tracker-only completion PR is useful when review acceptance, merge, or required post-merge evidence did not exist during implementation, but it is not mandatory when another already-required, properly scoped update records all final evidence.

A Cloud/environment limitation requires the missing validation in an authoritative environment before `Done`; it does not automatically require post-merge CI. PR CI is sufficient when it validates the complete merge candidate, the task does not require integrated-main evidence, and no merge-only, deployment, or scheduled behavior remains. Post-merge CI is required only for a concrete task, workflow, environment, or phase/release-gate reason.

Begin authority review with the task's `Authority` field and add only sources or accepted ADRs actually implicated. If implementation would change an approved significant architecture, product, or security decision, stop and follow Document 27. A task cannot be `Done` with an undocumented significant deviation.

## Phase Gate Rule

A phase may be marked **PASS** only when:
1. All **Must** tasks in the phase are Done.
2. Where the phase defines an explicit **Gate** task, that task is Done.
3. Required automated suites pass.
4. No undocumented architecture deviation exists.
5. Applicable security/observability requirements introduced in that phase are working.
6. Document 26 phase-completion criteria are satisfied and factual phase evidence is recorded.

Phase outcome is separate from task status. Record it under the phase as **Phase Gate State: Not Evaluated** or **Phase Gate State: PASS**, with **Phase Gate Evidence** supporting `PASS`. Never infer `PASS` merely because code exists. Phase 0 has no explicit Gate task; P0-15 is a Documentation task, not a Phase 0 Gate, and Phase 0 cannot be evaluated as `PASS` until P0-15 is `Done` and the full Phase 0 criteria have been externally verified.


# Phase 0 — Engineering Foundation

**Primary goal:** Reproducible Java/Spring + React foundation with DB, CI, security and architecture tests.

**Milestone:** M0 — Engineering Skeleton

**Implementation items:** 15

**Phase Gate State:** PASS

**Phase Gate Evidence:** External Phase 0 review verdict: **APPROVED FOR PASS**. P0-01 through P0-15 are all Done, and Phase 0 defines no explicit Gate task. The Document 26 Phase 0 exit criteria were externally reviewed and satisfied: backend build, tests, ArchUnit architecture checks, and Flyway migration validation are green; frontend lint, typecheck, tests, and build are green; PostgreSQL 18, pgvector 0.8.6, and pg_trgm 1.6 are verified; Flyway migration from zero and idempotent restart are verified; reusable PostgreSQL/pgvector Testcontainers support has Docker-backed GitHub Actions validation; baseline secret scanning and dependency-review controls are active; and the health, liveness/readiness, structured logging, correlation-ID, and safe-exposure observability foundation is working. Post-merge main quality run #33 (ID 33046936522) succeeded at `8cd9c919bfcbb19345ed8ac56f98b42fd7561229`, with `backend-quality`, `frontend-quality`, and `security` all successful. No undocumented architecture deviation is known. **M0 — Engineering Skeleton is achieved.**


## P0-01 — Create backend repository structure

- **Workstream:** Repository
- **Priority:** Must
- **Status:** Done
- **Goal:** Establish a clean Spring Boot codebase aligned with the modular-monolith architecture.
- **Build:** Create Maven Spring Boot project, Java 25 toolchain, base packages: identity, learning, progress, review, materials, rag, ai, shared, bootstrap.
- **How it works:** Only bootstrap wiring may depend broadly; feature modules must start with explicit boundaries.
- **Dependencies:** None
- **Tests / validation:** mvn test; application context starts; Java 25 verified.
- **Expected result:** Backend starts locally with no architecture violations.
- **Definition of Done:** Build passes, health endpoint responds, repository structure matches Doc 19.
- **Authority:** Documents 17,19,26
- **Evidence / link:** Java 25.0.4; Maven Wrapper 3.9.16; `mvnw.cmd test` and `mvnw.cmd clean verify` passed (1 test, 0 failures); executable JAR `backend/target/hippocampus-backend-0.0.1-SNAPSHOT.jar`; packaged runtime `GET /health` returned 200, `application/json`, and `{"status":"UP"}`; dependency tree and Doc 19 source structure reviewed.
- **Notes / blockers:** _None_

## P0-02 — Create frontend repository structure

- **Workstream:** Frontend
- **Priority:** Must
- **Status:** Done
- **Goal:** Establish React application baseline.
- **Build:** Create React 19 + TypeScript + Vite project with app, features, components, api, hooks, state, schemas, types, test folders.
- **How it works:** App shell owns providers/router; feature folders own feature UI.
- **Dependencies:** Frontend project
- **Tests / validation:** npm test/build/typecheck/lint.
- **Expected result:** Frontend loads base route without runtime/type errors.
- **Definition of Done:** Build/test/typecheck pass; folders match Doc 20.
- **Authority:** Documents 17,20,26
- **Evidence / link:** Node 24.16.0; npm 11.13.0; React/React DOM 19.2.8, TypeScript 6.0.3, Vite 8.1.5; `npm.cmd test` passed (1 test, 0 failures); typecheck, lint, and production build passed; built preview `GET /` returned 200 HTML with the application root/module entry; direct dependencies and Doc 20 source structure reviewed.
- **Notes / blockers:** _None_

## P0-03 — Configure Spring profiles

- **Workstream:** Backend
- **Priority:** Must
- **Status:** Done
- **Goal:** Separate local/test/pilot configuration safely.
- **Build:** Create application.yml plus local/test/pilot profiles; externalize secrets and resource limits.
- **How it works:** Runtime selects profile; secrets are injected via environment.
- **Dependencies:** Backend project
- **Tests / validation:** Profile-specific smoke tests; no secrets in Git.
- **Expected result:** App boots in local and test profiles with correct overrides.
- **Definition of Done:** Profiles documented and no secret values committed.
- **Authority:** Documents 17,19,22,23
- **Evidence / link:** Java 25.0.4; Maven Wrapper 3.9.16 running on Java 25.0.4; base/local/test/pilot profile configuration and concise backend documentation added; `mvnw.cmd test` and `mvnw.cmd clean verify` passed (4 tests, 0 failures); packaged JAR local `SERVER_PORT` and pilot `PORT` smoke tests returned the exact `/health` contract over HTTP 200; archive contains base/local/pilot configuration and excludes test configuration; secret/later-task configuration scans, dependency/scope audit, and `git diff --check` passed.
- **Notes / blockers:** _None_

## P0-04 — Provision local PostgreSQL + pgvector

- **Workstream:** Database
- **Priority:** Must
- **Status:** Done
- **Goal:** Provide reproducible local relational/vector infrastructure.
- **Build:** Create Docker Compose PostgreSQL 18 with vector and pg_trgm extensions.
- **How it works:** Developers start DB locally; Spring connects through env config.
- **Dependencies:** Backend project
- **Tests / validation:** Container starts; SELECT extversion for vector; pg_trgm enabled.
- **Expected result:** Local DB is reproducible from one command.
- **Definition of Done:** Compose file works and extension tests pass.
- **Authority:** Documents 17,18,23
- **Evidence / link:** Docker 29.5.2 / Docker Desktop 4.75.0 and Docker Compose v5.1.3 verified; `docker compose config` passed; `postgres` service using pinned `pgvector/pgvector:0.8.6-pg18` started healthy; PostgreSQL `18.6` verified; `vector` extversion `0.8.6` and `pg_trgm` extversion `1.6` verified; named persistent volume `hippocampus-postgres-data` exists; stop/start restart kept both extensions enabled; `backend\mvnw.cmd test` passed (4 tests, 0 failures); scope/security audit confirmed no application schema, Flyway, datasource integration, Java persistence code, Maven dependency, pilot/Neon provisioning, or real credentials; `git diff --check` passed.
- **Notes / blockers:** _None_

## P0-05 — Initialize Flyway

- **Workstream:** Database
- **Priority:** Must
- **Status:** Done
- **Goal:** Make schema evolution version-controlled from day one.
- **Build:** Configure Flyway and create baseline migration for extension/bootstrap objects only.
- **How it works:** App startup validates/applies migrations; Hibernate uses validate.
- **Dependencies:** Local PostgreSQL
- **Tests / validation:** Empty DB migration test; second startup is idempotent.
- **Expected result:** Schema builds from zero consistently.
- **Definition of Done:** CI migration-from-zero test passes.
- **Authority:** Documents 17,18,25
- **Evidence / link:** Java 25.0.4; Maven Wrapper 3.9.16 on Java 25.0.4; PostgreSQL 18.6 via local Docker `pgvector/pgvector:0.8.6-pg18`; Docker PostgreSQL healthy on host port 5432 with host/JDBC auth verified for `hippocampus` / `hippocampus`; resolved Flyway/JDBC dependencies: `spring-boot-starter-flyway` 4.1.1, `flyway-database-postgresql` 12.4.0, `postgresql` 42.7.13; fresh temporary DB migration from zero passed; V1 `bootstrap extensions` history successful; second startup idempotency passed; existing P0-04 `hippocampus` DB onboarding passed; `vector` 0.8.6 and `pg_trgm` 1.6 verified; no application/domain tables found; `backend\mvnw.cmd test` passed; `backend\mvnw.cmd clean verify` passed; `backend\mvnw.cmd dependency:tree` reviewed; dependency/scope audit found no JPA, Hibernate config, Spring Data JPA, Testcontainers, Flyway Maven plugin, pilot datasource config, domain schema, indexes, or seed data.
- **Notes / blockers:** P0-05 intentionally does not add Hibernate/JPA configuration. When Hibernate/JPA is introduced by its owning task, schema mutation must remain disabled and Hibernate must validate rather than create/update.

## P0-06 — Add architecture enforcement tests

- **Workstream:** Architecture
- **Priority:** Must
- **Status:** Done
- **Goal:** Prevent module drift early.
- **Build:** Configure ArchUnit rules for API→application→domain/ports and infrastructure isolation.
- **How it works:** CI fails if forbidden dependencies appear.
- **Dependencies:** Backend structure
- **Tests / validation:** Introduce deliberate violation test locally, then remove; CI green.
- **Expected result:** Forbidden layer dependencies are automatically detected.
- **Definition of Done:** Architecture tests run on every PR.
- **Authority:** Documents 19,25
- **Evidence / link:** Java 25.0.4; Maven Wrapper 3.9.16 on Java 25.0.4; ArchUnit 1.5.0 test-scoped; eight permanent production-bytecode architecture-rule categories enforce approved module roots, domain independence, application inward dependencies, API-to-application boundaries, feature infrastructure encapsulation, shared independence, bootstrap direction, and top-level module cycle freedom; targeted architecture suite passed (8 tests); temporary negative proofs for domain-to-infrastructure, shared-to-feature, and unapproved root-package placement each failed the targeted Maven test with the expected offending dependency/class, then all fixtures/probes were removed; `backend\\mvnw.cmd test` passed (14 tests); `backend\\mvnw.cmd clean verify` passed (14 tests and executable JAR); `backend\\mvnw.cmd dependency:tree` reviewed; final source/status audit found no temporary fixtures, production placeholders, CI workflow, Spring Modulith, Testcontainers, persistence work, or P0-07 implementation.
- **Notes / blockers:** Architecture tests run in the ordinary Maven test/verify lifecycle. P0-12 owns GitHub Actions workflow integration required to execute them on every PR.

## P0-07 — Create error contract foundation

- **Workstream:** Backend
- **Priority:** Must
- **Status:** Done
- **Goal:** Standardize API failures before features expand.
- **Build:** Implement ProblemDetail/error-code mapper, correlation ID filter, common domain/application exception hierarchy.
- **How it works:** Controllers throw typed errors; central handler maps stable code/message/correlationId.
- **Dependencies:** Backend project
- **Tests / validation:** Controller test for validation, not-found, conflict, internal error.
- **Expected result:** Errors are consistent and do not leak internals.
- **Definition of Done:** Stable error JSON verified.
- **Authority:** Documents 19,22,24
- **Evidence / link:** Java 25.0.4; Maven Wrapper 3.9.16 on Java 25.0.4; Spring Boot-managed `spring-boot-starter-validation` 4.1.1 and `spring-boot-starter-webmvc-test` 4.1.1 resolved; centralized `ProblemDetail` mappings verified for validation 400, application not-found 404, domain conflict 409, typed semantic failure 422, and sanitized internal failure 500; correlation filter verified for caller UUID canonicalization/reuse and generated UUIDs for missing, blank, or invalid values, with matching `X-Correlation-ID` response header and error-body property; validation details expose only field names and generic messages, not rejected values/payloads/binding internals; malformed JSON returns sanitized `MALFORMED_REQUEST` 400 without parser details or content reflection; targeted error-contract suite passed (8 tests); architecture suite passed (8 tests); `backend\\mvnw.cmd test` passed (22 tests); `backend\\mvnw.cmd clean verify` passed (22 tests and executable JAR); dependency/scope audit found no Security, Actuator, MDC/structured logging, JPA, migration, Testcontainers, frontend, or P0-08 implementation.
- **Notes / blockers:** P0-08 owns Actuator, Micrometer, structured logging, and correlation propagation into logs. No unresolved P0-07 blockers.

## P0-08 — Add Actuator and structured logging

- **Workstream:** Observability
- **Priority:** Must
- **Status:** Done
- **Goal:** Make foundation observable immediately.
- **Build:** Enable safe health endpoints, Micrometer baseline, JSON/structured log fields, correlation IDs.
- **How it works:** Every request receives opaque correlation ID propagated to logs.
- **Dependencies:** Backend project
- **Tests / validation:** Health test; log smoke test; sensitive-value grep.
- **Expected result:** Health works and logs contain no secrets.
- **Definition of Done:** Liveness/readiness and correlation verified.
- **Authority:** Documents 17,24
- **Evidence / link:** Java 25.0.4; Maven Wrapper 3.9.16; Spring Boot-managed `spring-boot-starter-actuator` 4.1.1 with built-in Micrometer 1.17.1 meters; temporary `/health` controller removed; general `/actuator/health` and dedicated liveness/readiness groups verified with safe status-limited responses and correct 200/503 availability transitions; Actuator discovery, metrics, sensitive HTTP endpoints, and JMX exposure verified inaccessible; ECS console JSON verified with service name/environment, fluent structured request-completion and unexpected-error events, propagated `correlationId`, safe request path without query, and guaranteed MDC cleanup; synthetic authorization, cookie, query, and body secret markers absent from captured logs and Surefire reports; targeted database-free observability suite passed (10 tests) without an application datasource; existing PostgreSQL-backed Flyway regressions passed with local PostgreSQL healthy; P0-07 error contract and P0-06 architecture rules passed; `backend\mvnw.cmd test` passed (32 tests); `backend\mvnw.cmd clean verify` passed (32 tests and executable JAR); packaged pilot-profile JAR smoke verified expected 200/404 health and exposure behavior with valid ECS JSON; `backend\mvnw.cmd dependency:tree` reviewed with no exporter, APM, tracing, Security, or new persistence dependency; `git diff --check` and final scope audit passed.
- **Notes / blockers:** `/actuator/health` is general application health, not readiness. Deployment probes must use `/actuator/health/liveness` and `/actuator/health/readiness`. Database readiness remains deferred until the owning task introduces the application datasource. No unresolved P0-08 blockers.

## P0-09 — Create application shell and routing

- **Workstream:** Frontend
- **Priority:** Must
- **Status:** Done
- **Goal:** Provide consistent app frame without feature sprawl.
- **Build:** Implement router, auth-placeholder layout, compact navigation shell, route-level error boundary.
- **How it works:** Feature routes render inside app shell.
- **Dependencies:** Frontend project
- **Tests / validation:** Route smoke tests at desktop/mobile widths.
- **Expected result:** Core routes render and unknown routes fail gracefully.
- **Definition of Done:** Navigation shell works responsively.
- **Authority:** Documents 20
- **Evidence / link:** Node 24.16.0; npm 11.13.0; `react-router` 7.18.2 is the only new direct dependency and `npm.cmd audit --audit-level=high` reported 0 vulnerabilities; app-owned data-router tree implements `/` redirecting to `/home`, all documented static/dynamic route contracts, compact primary navigation, secondary Settings navigation, persistent semantic shell landmarks, sanitized route-level fallback, and safe not-found handling using one generic app-owned placeholder; Vitest/React Testing Library route/navigation suite passed (18 tests) including direct entries, active navigation, shell persistence, route failure, unknown route, and desktop/mobile semantic smoke; headless production-preview visual smoke passed at 1440x900 and 390x844; production preview returned 200 SPA entry HTML for `/`, `/home`, `/subjects/example-subject`, `/missions/example-mission`, and `/unknown-route`; `npm.cmd run typecheck`, `npm.cmd run lint`, and `npm.cmd run build` passed; `npm.cmd ls --depth=0` and source/diff audit confirmed no additional test dependency, P0-10 API client, P0-11 UI system, auth/provider/state/form/Tailwind/Playwright dependency or behavior, speculative feature subtree, backend change, or undocumented architectural deviation; `git diff --check` passed.
- **Notes / blockers:** _None_

## P0-10 — Create centralized API client

- **Workstream:** Frontend
- **Priority:** Must
- **Status:** Done
- **Goal:** Prevent ad hoc fetch usage.
- **Build:** Implement base URL, credentials, JSON/problem parsing, AbortSignal and multipart helpers.
- **How it works:** All feature API modules depend on this client.
- **Dependencies:** Frontend project
- **Tests / validation:** Unit tests for 2xx, 4xx ProblemDetail, network abort.
- **Expected result:** Errors normalize consistently.
- **Definition of Done:** No direct fetch calls outside approved client/streaming abstraction.
- **Authority:** Documents 20,22
- **Evidence / link:** Node 24.16.0; npm 11.13.0; dependency-free native-fetch client under `frontend/src/api` owns `VITE_API_BASE_URL` validation with same-origin fallback, fixed `credentials: include`, relative backend paths, client-controlled `Accept`/JSON `Content-Type`, blocked caller `Authorization`, JSON/204/multipart handling, caller `AbortSignal`, strict P0-07 ProblemDetail normalization, safe correlation-ID extraction, and normalized request/network/abort/invalid-response failures without raw response/body/parser/network exception or `Error.cause` retention; `.env.example` contains only the public loopback API origin; targeted Vitest API/config suite passed (48 tests) including controlled-header rejection, safe custom headers, browser-owned multipart boundary, invalid deployment configuration, required ProblemDetail fields, correlation precedence, safe fallbacks, aborts, and raw-error non-retention; full Vitest/React Testing Library suite passed (66 tests); `npm.cmd run typecheck`, `npm.cmd run lint`, and `npm.cmd run build` passed; `npm.cmd audit --audit-level=high` reported 0 vulnerabilities; `npm.cmd ls --depth=0` confirmed zero new direct dependencies; production source audit found the sole `fetch` call in `frontend/src/api/apiClient.ts`; secret/browser-storage, package, backend, route, auth, state, retry/cache/timeout, UI, P0-11+, scope, and diff audits passed; required read-only review confirmed header ownership, ApiError sanitization, and scope compliance, and its two minor configuration/test-coverage findings were corrected before final validation; `git diff --check` passed.
- **Notes / blockers:** _None_

## P0-11 — Create core UI states/components

- **Workstream:** Frontend
- **Priority:** Must
- **Status:** Done
- **Goal:** Standardize basic UX.
- **Build:** Implement Button, Input, Textarea, Select, Card, Dialog/Drawer, Badge, Progress, Skeleton, EmptyState, ErrorState.
- **How it works:** Features compose these without creating a huge UI library.
- **Dependencies:** Frontend project
- **Tests / validation:** Component tests + keyboard/focus smoke tests.
- **Expected result:** Reusable states render consistently.
- **Definition of Done:** Core component suite passes accessibility smoke tests.
- **Authority:** Documents 20,25
- **Evidence / link:** Core UI primitives added under `frontend/src/components/ui` with centralized Contemporary Clinical tokens in `frontend/src/styles/tokens.css`; native semantic Button, Input, Textarea, Select, Card, Dialog, Drawer, Badge, quantitative Progress, decorative Skeleton, EmptyState and safe ErrorState implemented; Dialog/Drawer share the native `<dialog>` lifecycle with test-only JSDOM `showModal`/`close` mocks, native `cancel` handling, accessible title/description associations, initial-focus support, controlled close, backdrop behavior, focus restoration, and a shared explicit sequential Tab/Shift+Tab boundary that wraps eligible descendants and safely focuses the dialog container when none are eligible; the initial Chrome smoke found focus escaping to `body`, the correction was added without replacing native `<dialog>`/`showModal()` behavior, and the focused regression suite plus full Vitest/React Testing Library suite passed (85 tests across 4 files); `npm.cmd run typecheck`, `npm.cmd run lint`, `npm.cmd run build`, `npm.cmd audit --audit-level=high` (0 vulnerabilities), `npm.cmd ls --depth=0`, and `git diff --check` passed; fresh Chrome smoke rerun passed the three former failures: Dialog desktop Tab/Shift+Tab containment, Drawer desktop Tab/Shift+Tab containment, and Drawer mobile containment at 390x844 with full-width bottom-sheet layout; `@fontsource/hanken-grotesk@5.3.0` remains the only new direct production dependency; source/scope audit confirmed no forbidden UI framework, state/form/schema dependency, feature screen, backend/API change, P0-12 work, or later-task leakage.
- **Notes / blockers:** _None.

## P0-12 — Create GitHub Actions quality pipeline

- **Workstream:** CI/CD
- **Priority:** Must
- **Status:** Done
- **Goal:** Make quality gates automatic.
- **Build:** Add backend tests, frontend lint/typecheck/test, architecture tests, migration integration tests, secret/dependency scans, builds.
- **How it works:** PR must pass deterministic checks before merge.
- **Dependencies:** Repositories
- **Tests / validation:** Open sample PR; intentionally fail one check.
- **Expected result:** CI blocks broken code and reports failures clearly.
- **Definition of Done:** Required jobs green on main.
- **Authority:** Documents 23,25

- **Evidence / link:**
  - GitHub Actions quality workflow implemented in PR #13.
  - Required checks: `backend-quality`, `frontend-quality`, `security`.
  - Initial CI configuration issues corrected:
    - Maven Wrapper executable permission.
    - Dependency Graph enablement.
  - Normal PR run passed all required checks.
  - Intentional CI-gate validation introduced a deterministic failing frontend test; `frontend-quality` failed while `backend-quality` and `security` remained green.
  - Active `main` ruleset prevented merge while the required check failed.
  - Temporary failure was removed and PR returned to green.
  - PR #13 merged to `main`.
  - Post-merge quality run #5 on `main` completed successfully.
  - `backend-quality` passed backend tests, ArchUnit architecture tests, Flyway/PostgreSQL migration integration, and backend build.
  - `frontend-quality` passed lint, typecheck, 85 tests, build, and `npm audit`.
  - `security` passed Gitleaks scanning; Dependency Review remains PR-only and is intentionally skipped on push-to-main runs.
  - No deployment behavior or later-task scope was introduced.

- **Notes / blockers:** _None_

## P0-13 — Add secret scanning and dependency monitoring

- **Workstream:** Security
- **Priority:** Must
- **Status:** Done
- **Goal:** Prevent credential leaks and known vulnerable dependencies.
- **Build:** Configure Gitleaks and Dependabot/security alerts where available; add ignore process documentation.
- **How it works:** Scans run on PR/schedule; findings are triaged.
- **Dependencies:** CI pipeline
- **Tests / validation:** Synthetic fake-secret fixture in isolated test; dependency alert workflow review.
- **Expected result:** Secrets and vulnerable dependency signals are visible before release.
- **Definition of Done:** Security checks documented and active.
- **Authority:** Documents 22,24,25
- **Evidence / link:**
  - P0-13 implementation merged through PR #15 from `feat/p0-13-security-monitoring` into `main`.
  - Added `.github/dependabot.yml` with weekly UTC version-update checks for Maven (`/backend`), npm (`/frontend`), GitHub Actions (`/`), and Docker Compose (`/`); patch/minor Maven, npm, and GitHub Actions updates are grouped per ecosystem, major updates remain individual, open version-update PRs are limited to three per ecosystem, and no dependency update is auto-merged.
  - GitHub recognized the configured Dependabot ecosystems and began performing update checks. Generated Dependabot PRs demonstrated active npm and GitHub Actions monitoring; Maven and Docker Compose recognition/update checks were also verified.
  - Added `.github/workflows/security-monitoring.yml` with Monday 04:17 UTC and manual triggers, `contents: read`, immutable checkout and Gitleaks pins, a runtime-only isolated synthetic-secret detector self-test, redacted output, cleanup, and full fetched-history Gitleaks scanning.
  - The isolated synthetic-secret self-test successfully proved that Gitleaks detects the runtime-generated fixture without committing that fixture to repository history.
  - The first full-history Gitleaks run detected one historical `generic-api-key` finding in `CorrelationIdObservabilityTests.java`.
  - The finding was investigated and confirmed to be a false positive originating from a deliberately synthetic test marker used to verify that sensitive request values are not emitted to logs; it was not an actual credential.
  - The false positive was suppressed using only its exact historical Gitleaks fingerprint in `.gitleaksignore`; no broad path, regex, rule, or commit suppression was introduced.
  - Correction PRs #22 and #23 were merged to `main`, resulting in the exact fingerprint-only suppression and explicit `--gitleaks-ignore-path` use for the full-history scan.
  - Post-remediation manual `security-monitoring` run on `main` completed successfully. Both the isolated detector self-test and full-history Gitleaks scan passed.
  - Existing `backend-quality`, `frontend-quality`, and `security` checks remained active and passed on `main`; backend CI successfully executed the PostgreSQL/Flyway migration integration that had been unavailable in the local environment.
  - Frontend validation passed lint, typecheck, 85 Vitest tests, production build, and `npm audit --audit-level=high` with 0 vulnerabilities.
  - Added `docs/security/DEPENDENCY-AND-SECRET-TRIAGE.md` covering evidence safety, real-secret rotation, false-positive and fingerprint-only suppression, historical findings, dependency severity/classification, scheduled-failure response, external settings, and completion evidence.
  - No `.gitleaks.toml`, broad Gitleaks suppression, committed secret-shaped fixture, auto-merge behavior, OWASP/NVD scanner, application dependency, service, repository secret, application-code change, database change, or later-task scope was introduced; the existing `.github/workflows/quality.yml` required jobs and behavior remain unchanged.
  - Applicable GitHub repository security settings, including Dependabot alerts/security updates were reviewed and verified active.

- **Notes / blockers:** _None_

## P0-14 — Create Testcontainers foundation

- **Workstream:** Testing
- **Priority:** Must
- **Status:** Done
- **Goal:** Provide realistic integration tests.
- **Build:** Configure PostgreSQL 18 + pgvector Testcontainer base test support.
- **How it works:** Integration tests create isolated DB and run Flyway.
- **Dependencies:** Backend + Docker
- **Tests / validation:** Repository smoke test against container.
- **Expected result:** CI can test actual PostgreSQL behavior.
- **Definition of Done:** Reusable integration test base committed.
- **Authority:** Documents 17,25
- **Evidence / link:** Added Spring Boot-managed `org.testcontainers:testcontainers-postgresql` test dependency, resolved at Testcontainers 2.0.5 with `testcontainers-jdbc`, `testcontainers-database-commons`, and core `testcontainers` transitively; created reusable test-source-only `PostgresIntegrationTestSupport` using the Testcontainers 2.x `org.testcontainers.postgresql.PostgreSQLContainer` API and pinned `pgvector/pgvector:0.8.6-pg18` image without an artificial compatible-substitute declaration or fixed host-port binding; migrated the Flyway smoke test from localhost/manual Compose database administration to an initially empty ephemeral container and container-provided JDBC URL, credentials, host, and mapped port; assertions cover the dynamic endpoint, queried PostgreSQL major version 18, successful V1 history, `vector` 0.8.6, `pg_trgm` 1.6, zero failed migrations, no domain tables, and second-startup idempotency; removed the redundant `backend-quality` PostgreSQL service and `HIPPOCAMPUS_POSTGRES_PORT` while preserving `./mvnw -B -ntp clean verify`. Codex Cloud validation: `mvn -B -ntp dependency:tree -Dincludes=org.testcontainers` passed; `mvn -B -ntp -DskipTests test-compile` passed; `mvn -B -ntp -Dtest='!FlywayMigrationApplicationTests' test` passed all 30 non-container backend tests, including 8 ArchUnit tests; `docker version` could not run because the Docker CLI/daemon is unavailable; `mvn -B -ntp -Dtest=FlywayMigrationApplicationTests test`, `mvn -B -ntp test`, and `mvn -B -ntp clean verify` each failed at the unskipped Testcontainers test because no Docker environment or `/var/run/docker.sock` was available. GitHub Actions has not been executed for this implementation.
  - PR #25 merged with implementation head `df972fcd3f3aad6ef8d602cffad327bb43c76886` and merge commit `b637e1c6755916196a79906ac5afe80f205e9393`.
  - PR quality run #26 (run ID `33021748944`) succeeded; `backend-quality`, `frontend-quality`, and `security` all succeeded. The backend job supplied the required Docker/Testcontainers-backed validation.
  - Post-merge main quality run #27 (run ID `33022026985`) succeeded at `b637e1c6755916196a79906ac5afe80f205e9393`; all required jobs succeeded on `main`.
  - P0-14 Definition of Done is satisfied.
- **Notes / blockers:** _None_

## P0-15 — Create implementation tracker process

- **Workstream:** Documentation
- **Priority:** Must
- **Status:** Done
- **Goal:** Make completion evidence mandatory.
- **Build:** Add tracker workflow, statuses, phase-gate rules, authority references, evidence links.
- **How it works:** Task may only move to Done when DoD and tests pass.
- **Dependencies:** Docs frozen
- **Tests / validation:** Review tracker with Phase 0 tasks.
- **Expected result:** Implementation work has a single progress source.
- **Definition of Done:** Tracker committed and used in PR workflow.
- **Authority:** Documents 26,27
- **Evidence / link:** Added the canonical Markdown tracker lifecycle, factual evidence policy, blocker/environment-limitation distinction, PR/review/completion flow, authority/ADR rule, separate phase-level PASS convention, and Phase 0 Gate clarification; declared the XLSX a frozen non-operational planning/export/reference artifact; created a lightweight PR template; updated the documentation handoff; audited P0-01 through P0-14 and removed only the stale `commit/PR pending` suffixes from P0-01 through P0-04. Documentation validation and scope audit passed; `git diff --check` passed. ADR assessment: no ADR required because this implements Documents 26 and 27 without changing approved architecture, product, or security decisions.
  - External implementation review approved P0-15 and PR #27, with implementation head `5f09eb44ad211db177e281df60e9fbfc8916bc7e`.
  - PR quality run #30 (run ID `33038116024`) succeeded; `backend-quality`, `frontend-quality`, and `security` all succeeded.
  - PR #27 merged successfully; merge commit `3e4408e8ab89f5772896dd732e781e4149275c1d` is the reviewed `main` commit.
  - `.github/PULL_REQUEST_TEMPLATE.md` is present on `main`. PR #27 used the tracker-oriented fields, and this follow-up completion PR uses the same tracker-oriented workflow after the template reached `main`; the tracker-oriented PR workflow is operational.
  - The P0-15 Expected Result is satisfied: implementation work has one operational progress source. The Definition of Done is satisfied: the tracker is committed and used in PR workflow. ADR not required. Current blockers: none.
- **Notes / blockers:** _None_

# Phase 1 — Identity + Core Student Workspace

**Primary goal:** Secure private student identity, session lifecycle and ownership boundary.

**Implementation items:** 11

**Phase Gate State:** PASS

**Phase Gate Evidence:** External Phase 1 implementation/security review accepted P1-11 and found no blocking Critical, High, or Medium finding in the changed gate surface. P1-01 through P1-11 are Done. PR [#63](https://github.com/KennethVier/hippocampus/pull/63) implemented the Phase 1 end-to-end gate and was merged into `main` as `e2ca5da0a290c8a8299a257b01b86dd5e3d98acc`. PR quality run #120 (ID `33301280849`) succeeded with `backend-quality`, `frontend-quality`, `auth-e2e`, `security`, and `phase1-gate` all successful. Its non-skipped real Playwright journey provisioned two isolated PostgreSQL students and passed the shared-browser User A → logout → User B authoritative-session isolation flow. The PR `security` job completed explicit commit-range secret scanning and dependency review successfully. Post-merge `main` quality run #121 (ID `33301405280`) also succeeded at the merge commit with `backend-quality`, `frontend-quality`, `auth-e2e`, `security`, and `phase1-gate` successful; the dependency-review step was skipped for that `push` run and is not counted as passed. Authentication, server-side session lifecycle, current-user authority, CSRF, CORS, frontend protected-route/session recovery, private client-state clearing, and reusable ownership/IDOR isolation have authoritative Phase 1 validation. Document 26 Phase 1 exit criteria are satisfied: a student can authenticate and securely enter a private Hippocampus workspace, and cross-user access tests pass. No undocumented architecture deviation or current Phase 1 blocker is known.


## P1-01 — Create user schema

- **Workstream:** Database
- **Priority:** Must
- **Status:** Done
- **Goal:** Persist internal user identity.
- **Build:** Create users table/entity/repository with UUID, email, display_name, status, timestamps.
- **How it works:** Internal user ID is ownership root; provider/session details remain separate.
- **Dependencies:** P0 Flyway
- **Tests / validation:** Migration/repository CRUD/unique-email tests.
- **Expected result:** Users persist and unique constraints hold.
- **Definition of Done:** Migration + entity mapping + repository tests pass.
- **Authority:** Documents 18
- **Evidence / link:** Implementation PR [#32](https://github.com/KennethVier/hippocampus/pull/32) merged into `main` at merge commit `e620f9e28ac54e3f6bdb7f8661014179af81de74`. PR quality run #42 (`33095461285`) succeeded, including backend `./mvnw -B -ntp clean verify`, PostgreSQL/Testcontainers migration and repository validation, and 35 backend tests with zero failures, errors, or skips. Post-merge `main` quality run #43 (`33095906351`) succeeded at the merge commit.
- **Notes / blockers:** None.

## P1-02 — Implement authentication flow

- **Workstream:** Security
- **Priority:** Must
- **Status:** Done
- **Goal:** Allow a student to establish an authenticated session.
- **Build:** Implement chosen v1 login credential flow using Spring Security; map identity to users.id.
- **How it works:** Successful authentication creates/uses server-side session.
- **Dependencies:** User schema
- **Tests / validation:** Auth integration tests: valid/invalid/disabled user.
- **Expected result:** Authenticated user can enter app; invalid credentials reveal no sensitive details.
- **Definition of Done:** Auth tests pass and flow documented.
- **Authority:** Documents 22
- **Evidence / link:** PR #37 merged at f38da0394896b500b7d2dbc595ea165c559c2fb6; post-merge `quality` workflow run #55 (run ID 33144274234) on `main` succeeded with `backend-quality`, `frontend-quality`, and `security`; external implementation review accepted; V3 credential migration and Hibernate mapping; Spring Security JSON login with exact-email database authentication, generic credential failures, server-side context persistence/session rotation, and bounded fixed-window throttling; provider unit, PostgreSQL credential-repository, end-to-end authentication integration, deterministic limiter, migration-contract, and authentication-flow documentation coverage.
- **Notes / blockers:** _None_

## P1-03 — Configure Spring Session JDBC

- **Workstream:** Security
- **Priority:** Must
- **Status:** Done
- **Goal:** Persist sessions server-side.
- **Build:** Enable Spring Session JDBC and required schema; configure idle timeout.
- **How it works:** Browser stores only secure session cookie.
- **Dependencies:** Authentication flow
- **Tests / validation:** Session persistence/expiry/restart tests.
- **Expected result:** Session survives app restart if DB remains; expired sessions fail.
- **Definition of Done:** Session tests pass.
- **Authority:** Documents 17,22
- **Evidence / link:** Boot-managed `spring-boot-starter-session-jdbc` (Spring Session 4.1.1) is configured with Flyway V4's standard PostgreSQL Spring Session schema, a configurable 30-minute idle timeout, and profile-scoped cookie policy (local/test HttpOnly + SameSite=Lax + Path=/api; pilot Secure + SameSite=None). Testcontainers-backed real-server integration tests prove authenticated session persistence, session-ID rotation, database-backed restart survival, and deterministic expiry rejection; migration tests assert tables, indexes, and cascade foreign key; profile tests assert cookie settings. Focused validation (`SpringSessionJdbcIntegrationTests,FlywayMigrationApplicationTests,AuthenticationFlowIntegrationTests,SpringProfilesApplicationTests`) passed with 12 tests and zero failures/errors/skips; `clean verify` passed with 54 tests and zero failures/errors/skips. Authentication-flow documentation updated in `docs/security/authentication-flow.md`.
- **Notes / blockers:** _None_

## P1-04 — Implement CSRF protection

- **Workstream:** Security
- **Priority:** Must
- **Status:** Done
- **Goal:** Protect cookie-authenticated mutations.
- **Build:** Configure Spring Security CSRF and frontend token acquisition/submission.
- **How it works:** GETs read; state-changing requests require valid CSRF token.
- **Dependencies:** Auth/session
- **Tests / validation:** Missing-token rejection and valid-token success tests.
- **Expected result:** Cross-site forged state change is rejected.
- **Definition of Done:** CSRF tests pass.
- **Authority:** Documents 22,25
- **Evidence / link:** Spring Security uses an explicit `HttpSessionCsrfTokenRepository` with the `X-CSRF-TOKEN` header, session-bound tokens, XOR masking, and CSRF-aware authentication-session strategy; `GET /api/auth/csrf` acquires the token for the frontend, while the frontend acquires and submits it immediately before every non-safe request and uses `credentials: include`. Integration coverage proves login/session-ID rotation, pre-login token invalidation, fresh post-login token success, missing/invalid token rejection, session binding, and preservation of the generic `ACCESS_DENIED` contract for non-CSRF denials. Focused backend validation (`AuthenticationFlowIntegrationTests,SpringSessionJdbcIntegrationTests,CsrfProtectionIntegrationTests`) passed with 17 tests and zero failures/errors/skips; full backend `clean verify` passed with 63 tests and zero failures/errors/skips. Existing frontend validation remains 46 focused tests and 103 full-suite tests, with typecheck, lint, and build passing. No CORS, JWT, Redis, migration, or later-phase changes were introduced.
- **Notes / blockers:** _None_

## P1-05 — Restrict CORS

- **Workstream:** Security
- **Priority:** Must
- **Status:** Done
- **Goal:** Allow only approved origins.
- **Build:** Configure local and pilot frontend origins with credentials support.
- **How it works:** Backend rejects credentialed requests from unapproved origins.
- **Dependencies:** Profiles
- **Tests / validation:** CORS integration tests.
- **Expected result:** Approved frontend works; wildcard credentialed CORS absent.
- **Definition of Done:** CORS tests pass.
- **Authority:** Documents 22
- **Evidence / link:** PR [#47](https://github.com/KennethVier/hippocampus/pull/47) was externally approved and merged into `main` at merge commit `6b1b7b860d46f0aac30c8fb3ec9ae4cbed417ad8`. PR quality run #77 (`33231440671`) completed successfully with `backend-quality`, `frontend-quality`, and `security` all successful. CORS integration coverage proves exact approved credentialed origins succeed; unapproved origins and disallowed methods receive no credentialed CORS access; wildcard/pattern origins are rejected by typed configuration; same-origin requests remain unaffected; local/test/pilot profiles bind the intended origin policy. Spring Security authentication, Spring Session JDBC, CSRF, backend authorization/ownership, and secure pilot cookie requirements remain unchanged. No dependency, migration, JWT, Redis, P1-06+, or architecture deviation was introduced. Independent OWASP-oriented review found no blocking issue; no ADR was required; the expected result and Definition of Done are satisfied.
- **Notes / blockers:** _None_

## P1-06 — Create current-user access abstraction

- **Workstream:** Domain
- **Priority:** Must
- **Status:** Done
- **Goal:** Remove client-controlled ownership identity.
- **Build:** Implement CurrentUser/AuthenticatedUser port resolved from Spring Security principal.
- **How it works:** Application use cases obtain user ID server-side.
- **Dependencies:** Authentication
- **Tests / validation:** Unit/integration tests; attempts to inject userId ignored/rejected.
- **Expected result:** Resource ownership always derives from session.
- **Definition of Done:** No use case accepts client userId as authority.
- **Authority:** Documents 19,22
- **Evidence / link:** PR [#49](https://github.com/KennethVier/hippocampus/pull/49) was externally approved and merged into `main` at merge commit `ed690f925623e679e6460f0f66d630a45f2c1e86`. PR quality run #81 (`33233085385`) completed successfully with `backend-quality`, `frontend-quality`, and `security` all successful. The implementation adds a privacy-minimal immutable `AuthenticatedUser` record, a framework-independent `CurrentUser` port, and a `SpringSecurityCurrentUser` infrastructure adapter that derives the persisted `users.id` exclusively from the trusted authenticated `HippocampusPrincipal`; missing, unauthenticated, or unexpected principals fail closed. Focused unit and Spring Security integration coverage proves trusted-principal resolution, unauthenticated denial, unexpected-principal rejection, and that a client-supplied `userId` cannot override the session-derived ownership identity. No `/me`, logout UX, roles, JWT, dependency, schema, configuration, or P1-07+ behavior was introduced. General implementation review verdict was **APPROVED** and the independent OWASP-oriented review verdict was **SECURITY PASS** for the P1-06 changed surface; no ADR was required; the expected result and Definition of Done are satisfied.
- **Notes / blockers:** _None_

## P1-07 — Implement /me session endpoint

- **Workstream:** Backend
- **Priority:** Should
- **Status:** Done
- **Goal:** Give frontend safe session/user context.
- **Build:** Return minimal user profile/session state.
- **How it works:** Frontend queries /me on authenticated shell bootstrap.
- **Dependencies:** Current-user abstraction
- **Tests / validation:** API tests authenticated/unauthenticated.
- **Expected result:** Frontend can determine logged-in user without tokens.
- **Definition of Done:** Endpoint stable and privacy-minimal.
- **Authority:** Documents 20,22
- **Evidence / link:** PR [#51](https://github.com/KennethVier/hippocampus/pull/51) was accepted and merged into `main`. `GET /api/auth/me` is implemented using the existing `CurrentUser` abstraction, which derives identity from the authenticated server principal, and returns only `{ "userId": "<uuid>" }`. The authenticated API test passes; an unauthenticated request returns `AUTHENTICATION_REQUIRED`. The response exposes no email, token, session ID, credentials, roles, or unrelated profile data. No frontend P1-08 work, security configuration, schema, dependency, JWT, or bearer-token changes were introduced. GitHub Actions quality validation passed for the P1-07 PR head; the expected result and Definition of Done are satisfied.
- **Notes / blockers:** _None_

## P1-08 — Implement login/session recovery UX

- **Workstream:** Frontend
- **Priority:** Must
- **Status:** Done
- **Goal:** Handle auth lifecycle cleanly.
- **Build:** Create login screen, protected routes, session bootstrap, expired-session redirect, return-path handling.
- **How it works:** TanStack Query tracks /me; no tokens in localStorage.
- **Dependencies:** Auth API
- **Tests / validation:** RTL/Playwright login/logout/expiry tests.
- **Expected result:** User logs in/out; expired session returns safely to login.
- **Definition of Done:** Critical E2E auth journey passes.
- **Authority:** Documents 20,25
- **Evidence / link:** PR [#56](https://github.com/KennethVier/hippocampus/pull/56) was externally reviewed and merged into `main` as `8e1f27def3ddd43d158c11421eafd26badcd5732`. The reviewed head `20dd40cbdc2908e4cb6bad9cf62b895eb78b615e` implements the browser session lifecycle with a public accessible login page, TanStack Query-owned `GET /api/auth/me` bootstrap, protected routes that withhold private content until authentication is known, strict same-application return-path validation, expired-session recovery, and framework-native CSRF-protected `POST /api/auth/logout`; no authentication token or session identifier is stored in browser storage. Final PR quality run #99 (ID `33259301437`) completed successfully with `backend-quality`, `frontend-quality`, `auth-e2e`, and `security` all successful. The non-skipped `auth-e2e` job ran the real PostgreSQL-backed Spring backend and browser flow using an isolated runtime-provisioned account and proved invalid-login handling, successful login with protected-route restoration, server-session invalidation and safe reauthentication, real logout followed by `401 AUTHENTICATION_REQUIRED` from `/api/auth/me`, and absence of auth state in local/session storage. General implementation review: **APPROVED** after accessibility, logout-contract, and E2E evidence findings were remediated. Independent adversarial review: **SECURITY PASS** for the P1-08 changed surface after CI Playwright trace retention was disabled to avoid retaining authentication trace data. No ADR was required because the approved opaque server-session architecture remains unchanged. P1-09 generalized private-state clearing remains deferred and was not implemented. The expected result and critical E2E Definition of Done are satisfied.
- **Notes / blockers:** _None_

## P1-09 — Clear private state on logout

- **Workstream:** Frontend
- **Priority:** Must
- **Status:** Done
- **Goal:** Prevent shared-device leakage.
- **Build:** Clear query cache, user-scoped Zustand, active streams and drafts on logout.
- **How it works:** Logout response invalidates backend session then frontend state.
- **Dependencies:** Frontend auth
- **Tests / validation:** Component/E2E shared-device test.
- **Expected result:** Next user cannot see previous user's cached data.
- **Definition of Done:** Logout privacy test passes.
- **Authority:** Documents 20,22
- **Evidence / link:** Added a narrow frontend private-state lifecycle that awaits cancellation of every active TanStack query and clears the complete shared QueryClient, including QueryCache and MutationCache, only after the existing CSRF-protected backend logout succeeds; authoritative `401 AUTHENTICATION_REQUIRED` session expiry uses a single-shot transition that withholds private content, performs the same cleanup exactly once, preserves the safe structured return state, and replace-navigates to login. Focused Vitest/RTL validation passed with 12 tests covering multi-key User A query removal, mutation-state removal, AbortSignal cancellation, late-result non-repopulation, backend-success-before-cleanup ordering, network/5xx/401/403 logout failure preservation, exact-once expiry cleanup, and same-QueryClient/runtime User A-to-User B observable isolation; the full frontend suite passed with 129 tests, and typecheck, lint, build, and `git diff --check` passed. General implementation review verdict: **APPROVED** after two test-evidence gaps were remediated. Independent adversarial review verdict: **SECURITY PASS** for the P1-09 changed surface, with the scoped caveat that TanStack Query has no generic active-mutation cancellation API and no current private active product mutation exists. Repository audits confirmed no current Zustand store/dependency, SSE/EventSource owner, global or persisted draft owner, or application-owned private browser persistence, so no fake owners or speculative cleanup infrastructure were added. No dependency, backend, schema, migration, API, CI, ADR, browser-storage, or P1-10+ change was introduced. Implementation PR [#59](https://github.com/KennethVier/hippocampus/pull/59) was externally reviewed and merged into `main` as `a6dd08e0dc781359d33349d4f161c6e9773b159a`. Post-merge main quality workflow run #110 (ID `33261934158`) succeeded at that merge commit, with `frontend-quality`, `backend-quality`, `security`, and `auth-e2e` all successful. The real authentication E2E journey passed in GitHub Actions, resolving the implementation-time local limitation caused by unavailable runtime-provisioned E2E credentials. The expected result and Definition of Done are satisfied, with no current blockers.
- **Notes / blockers:** _None_

## P1-10 — Add ownership authorization test harness

- **Workstream:** Security
- **Priority:** Must
- **Status:** Done
- **Goal:** Make IDOR checks reusable for every future resource.
- **Build:** Create test helpers with User A/User B and assertion patterns for 403/404/no data.
- **How it works:** Every new user-owned API reuses harness.
- **Dependencies:** Auth
- **Tests / validation:** Harness self-test.
- **Expected result:** Cross-user tests become cheap and mandatory.
- **Definition of Done:** Harness self-test passes and reuse contract is established for subsequent user-owned resource tests.
- **Authority:** Documents 22,25
- **Evidence / link:** Added the reusable P1-10 ownership/IDOR authorization test harness under "com.hippocampus.testing.security", including "OwnershipTestUser", "OwnershipTestUsers", "OwnershipTestRequests", "OwnershipAssertions", and "OwnershipAuthorizationHarnessTests". The harness establishes deterministic persisted User A/User B identities, authenticated request helpers, exact 403/404 ownership-policy assertions, foreign protected-data leakage checks across response bodies and headers, collection isolation checks, and denied-mutation state preservation with subsequent owner mutation success. The testing/security skill now directs future user-owned backend API tests to reuse "OwnershipTestUsers", "OwnershipTestRequests", and "OwnershipAssertions" rather than recreate ad-hoc ownership fixtures. PR #61 ("3fc94e674dfc57652a75e7059b6f050f284af274") passed GitHub Actions quality run #115 (ID "33298484212"): "backend-quality", "frontend-quality", "security", and "auth-e2e" all succeeded. "backend-quality" completed the backend tests, architecture tests, migrations, and build successfully. External implementation and security-harness review accepted P1-10 after the ownership harness and no-data-leak contract were verified. Implementation PR [#61](https://github.com/KennethVier/hippocampus/pull/61) was merged into `main` as `82bc5398bb0c2bf74569a3b0250fbe8e4b500e32`. Post-merge main quality run #117 (ID `33298854808`) succeeded at the merge commit, with `backend-quality`, `frontend-quality`, `security`, and `auth-e2e` all successful. The reusable ownership harness self-test and reuse contract, expected result, and Definition of Done are satisfied, with no current blockers. No production authorization behavior, P1-11 implementation, or Phase 2 scope was introduced.
- **Notes / blockers:** _None_

## P1-11 — Phase 1 end-to-end gate

- **Workstream:** Gate
- **Priority:** Must
- **Status:** Done
- **Goal:** Prove private student workspace exists.
- **Build:** Run auth, session, CSRF, CORS, ownership and frontend journey as one release gate.
- **How it works:** All Phase 1 pieces operate together.
- **Dependencies:** All P1 tasks
- **Tests / validation:** Playwright + backend security suite.
- **Expected result:** Student securely enters and exits private workspace.
- **Definition of Done:** All Phase 1 acceptance tests green; no cross-user leak.
- **Authority:** Documents 26
- **Evidence / link:** P1-11 strengthens the existing real Playwright authentication journey into one shared-browser User A to User B acceptance flow. GitHub Actions provisions two distinct runtime-only PostgreSQL students with independently generated passwords and accepted password hashes; the browser verifies each server-authoritative `/api/auth/me` identity, logout invalidation, distinct user IDs, empty browser authentication storage, and safe expiry behavior. The existing backend Phase 1 security suite and P1-10 `OwnershipAuthorizationHarnessTests` remain unchanged and authoritative, while a lightweight fail-closed `phase1-gate` aggregates `backend-quality`, `frontend-quality`, `auth-e2e`, and `security` without duplicating their workloads. Local frontend validation passed with 129 tests, typecheck, lint, and production build; local backend and real-browser execution were unavailable in the implementation workspace and were subsequently resolved by authoritative GitHub Actions. External implementation/security review accepted P1-11 with no blocking Critical, High, or Medium finding, no architecture deviation, no production security weakening, and no Phase 2 scope leakage; ADR not required. Implementation PR [#63](https://github.com/KennethVier/hippocampus/pull/63), head `c122efb1021e488359fe72cb37e7b1e7c0a76a8e`, merged into `main` as `e2ca5da0a290c8a8299a257b01b86dd5e3d98acc`. PR quality run #120 (ID `33301280849`) and post-merge quality run #121 (ID `33301405280`) both succeeded with `backend-quality`, `frontend-quality`, `auth-e2e`, `security`, and `phase1-gate` successful. The real two-user Playwright journey executed without being skipped and passed (`1 passed`), proving the shared-browser User A → logout → User B authoritative-session isolation flow. PR run #120 completed explicit commit-range secret scanning and dependency review successfully; the post-merge `push` run's skipped dependency-review step is not counted as passed. The Expected Result and Definition of Done are satisfied, with no current blockers.
- **Notes / blockers:** _None_

# Phase 2 — Subjects + Topics + Learning Materials

**Primary goal:** Students can organize subjects/topics and privately upload/manage learning materials.

**Implementation items:** 13


## P2-01 — Create subject/topic/subtopic schema

- **Workstream:** Database
- **Priority:** Must
- **Status:** Done
- **Goal:** Persist learner organization.
- **Build:** Create Subject, Topic, and Subtopic tables/entities/repos with the Subject → User, Topic → Subject → User, and Subtopic → Topic → Subject → User ownership hierarchy and an `ACTIVE` / `ARCHIVED` lifecycle state on all three entities.
- **How it works:** Subject belongs to user; Topic belongs to Subject; one explicit Subtopic level.
- **Dependencies:** P1 identity
- **Tests / validation:** Flyway, FK, uniqueness, ownership repository tests.
- **Expected result:** Organization persists with correct hierarchy.
- **Definition of Done:** Schema and CRUD tests pass.
- **Authority:** Documents 18
- **Evidence / link:** Added Flyway `V5__create_learning_organization.sql` with normalized `users → subjects → topics → subtopics` ownership, non-cascading `ON DELETE RESTRICT` foreign keys, constrained `ACTIVE` / `ARCHIVED` status on all three tables, the per-user case-insensitive Subject-name functional unique index, and Topic/Subtopic parent indexes; the Subject unique index also serves the owner-prefix lookup without a redundant `subjects(user_id)` index. Added the shared `LearningOrganizationStatus` persistence enum, UUID/`Instant` JPA entities with lazy unidirectional intra-aggregate parent relationships and no cascades/orphan removal, minimal Spring Data repositories with owner-scoped traversal, and PostgreSQL integration coverage for Flyway metadata/idempotence, status checks, CRUD, timestamps, FKs, uniqueness, restrictive deletion, and User A/User B ownership lookup. Local compilation passed with `mvn -B -ntp -DskipTests package`; the implementation workspace could not execute Docker/Testcontainers, so PostgreSQL-backed validation was deferred without being treated as a pass. External implementation and security review subsequently accepted P2-01 with no blocking Critical, High, or Medium finding; schema, JPA mapping, ownership normalization, restrictive deletion, scope, and ADR-0003 alignment were approved, with no additional ADR required. Implementation PR [#66](https://github.com/KennethVier/hippocampus/pull/66), head `192749d0b7f1559afdf3b9ac9d05d4c99d55540e`, passed PR quality run #129 (ID `33310849639`): `backend-quality`, `frontend-quality`, `security`, `auth-e2e`, and `phase1-gate` all succeeded; `backend-quality` executed the backend tests, architecture tests, PostgreSQL/Testcontainers Flyway migrations, repository CRUD/integrity/ownership tests, and build successfully, resolving the implementation-time Docker limitation. PR #66 merged into `main` as `dbf3ac55c653e0bbb82d4393665ff4a56413d2a2`. Post-merge `main` quality run #130 (ID `33311057989`) succeeded at the merge commit with `backend-quality`, `frontend-quality`, `security`, `auth-e2e`, and `phase1-gate` successful; the push-run dependency-review step was skipped and is not counted as passed. The expected hierarchy persists correctly, the schema/repository CRUD and integrity tests pass in the authoritative PostgreSQL environment, the Definition of Done is satisfied, and no current blocker remains. No API/controller, frontend, authentication/session/configuration, dependency, Material, recursive hierarchy, denormalized child ownership, or P2-02/P2-03 implementation was introduced.
- **Notes / blockers:** _None_

## P2-02 — Implement Subject CRUD use cases/API

- **Workstream:** Backend
- **Priority:** Must
- **Status:** Done
- **Goal:** Allow user-owned subject management.
- **Build:** Create create/list/get/update/archive subject use cases and REST endpoints.
- **How it works:** All queries derive authenticated owner.
- **Dependencies:** Subject schema
- **Tests / validation:** API validation and User A/B authorization tests.
- **Expected result:** Student only sees own subjects.
- **Definition of Done:** CRUD and IDOR suite pass.
- **Authority:** Documents 19,22
- **Evidence / link:** PR #68 implements the five owner-scoped Subject operations under `/api/subjects`: create, bounded ACTIVE-only list, get, update, and idempotent archive. Every use case derives `users.id` from `CurrentUser`; request DTOs contain no authoritative ownership field, repository lookups bind Subject ID to authenticated owner, and foreign/nonexistent IDs share the sanitized `404 SUBJECT_NOT_FOUND` contract. The P1-10 `OwnershipTestUsers`, `OwnershipTestRequests`, and `OwnershipAssertions` harness proves User A/User B collection isolation, foreign get/update/archive denial without protected-data leakage, denied mutation state preservation, and client `userId` non-authority. PostgreSQL remains authoritative for per-owner case-insensitive name uniqueness; the adapter recognizes only `uq_subjects_user_lower_name` through structured Hibernate constraint metadata and maps it to safe `409 SUBJECT_NAME_CONFLICT`. The list query applies owner and ACTIVE predicates before ordering by `sort_order ASC NULLS LAST`, `lower(name)`, and UUID, with page size bounded to 100. Archived Subjects remain owner-readable and owner-updatable without unarchiving, archive is idempotent, and no hard delete or descendant cascade is introduced. Quality run #133 (ID `33313937787`) exercised the PostgreSQL/Testcontainers suite but failed when class-level `@Validated` routed invalid pagination through AOP as `ConstraintViolationException`, producing `500` instead of the asserted `400`; the corrective commit removes that class-level proxy validation so Spring MVC raises `HandlerMethodValidationException`, and adds a focused MVC regression covering negative page, zero size, and size above 100. Corrective commit `bc33ffbc139f53df8dae35b61bbee4723220640c` fixed the pagination issue, after which quality run #134 (ID `33315296099`) exposed an independent Spring CGLIB failure: the lazy `JpaSubjectRepository` bean was final and therefore could not be subclass-proxied, causing repository creation failure and downstream Subject API `500` responses. The next corrective commit makes that Spring-managed adapter non-final while retaining lazy activation because datasource-free application contexts otherwise fail eagerly when no `SpringDataSubjectRepository` exists; removing all lazy wiring was explicitly tested and produced 18 additional non-Docker context errors, with no dependency cycle present. The focused post-fix suite passed 23 Subject pagination/domain/application, CurrentUser/CORS regression, and architecture tests with zero failures/errors/skips; all eight architecture tests passed. Local Maven-wrapper download and Docker/Testcontainers execution remain unavailable; system-Maven `clean verify` compiled and ran 124 tests but reported 53 environment errors from the missing Docker/Testcontainers runtime after proxy-safe wiring was restored, with zero assertion failures and zero skips. General implementation review approved API thinness, use-case/transaction responsibilities, port direction, UUID ownership, pagination, constraint translation, and scope with no unresolved blocker. Independent adversarial security review found no blocking Critical, High, or Medium issue in authentication, BOLA/IDOR, collection isolation, client ownership, denial preservation, CSRF/CORS, overposting, or exception privacy. Authoritative quality run #135 (ID `33316016291`) completed successfully on PR #68 head `0cbedb7dc80af09804510a847b12cefd20605f4e`: `backend-quality`, `frontend-quality`, `auth-e2e`, `security`, and `phase1-gate` all succeeded. The successful Docker/PostgreSQL backend job resolves the local Testcontainers limitation and provides authoritative verification for the backend tests, architecture rules, migrations, and build. Final implementation head `d5fb37fc897f5a8c229e39f74b7809b125924eb1` passed PR quality run #136 (ID `33348549071`) with `backend-quality`, `frontend-quality`, `auth-e2e`, `security`, and `phase1-gate` all successful. Implementation PR [#68](https://github.com/KennethVier/hippocampus/pull/68) was merged into `main` as `dc888fcadfe081aea281f8563570ba0a0d772c50`. Post-merge `main` quality run #137 (ID `33348770634`) succeeded at that merge commit with `backend-quality`, `frontend-quality`, `auth-e2e`, `security`, and `phase1-gate` successful; the push-run dependency-review step was skipped and is not counted as passed. External general implementation review and independent security review were accepted, the expected owner-scoped Subject CRUD behavior and IDOR protections are satisfied, the Definition of Done is met, and no current blocker remains.
- **Notes / blockers:** _None_

## P2-03 — Implement Topic/Subtopic CRUD use cases/API

- **Workstream:** Backend
- **Priority:** Must
- **Status:** Done
- **Goal:** Allow organization within subjects.
- **Build:** Create topic/subtopic create/list/update/archive APIs with parent ownership validation.
- **How it works:** Topic cannot be attached to another user's subject.
- **Dependencies:** Subject API
- **Tests / validation:** CRUD, invalid parent, archive tests.
- **Expected result:** Hierarchy remains ownership-safe.
- **Definition of Done:** API tests pass.
- **Authority:** Documents 18,19
- **Evidence / link:** Implemented the eight bounded Topic/Subtopic create, list, update, and idempotent archive endpoints with immutable parents, exact submitted-name preservation, duplicate sibling names, and no get/delete/restore/reparent behavior. Every use case derives the owner from `CurrentUser`; owner-aware child ports and JPA adapters qualify Topic ownership through Subject and Subtopic ownership through Topic → Subject, qualify active ancestors for create/list/update, and deliberately ignore ancestor lifecycle only for owner-authorized archive. ACTIVE-only collection SQL and count queries share identical owner/parent/ancestor predicates and deterministic name/sort-order/UUID ordering. API tests use the existing User A/B ownership harness to cover foreign parent attachment, collection isolation, denied mutation preservation, sanitized not-found behavior, ancestor archive policy, archived-child updates, duplicate names, and ownership/parent/status overposting. The existing P2-01 repositories were renamed to infrastructure-explicit Spring Data names and their PostgreSQL coverage was preserved; application/domain/API layers remain isolated by passing architecture tests. Local focused domain and architecture validation passed 10 tests with zero failures/errors/skips, and system-Maven compilation/package passed. Maven Wrapper download was unavailable because the implementation environment had no network route. System-Maven `clean verify` compiled all production and test sources and ran 130 tests with zero assertion failures and zero skips, but reported 57 environment errors because Docker/Testcontainers could not find `/var/run/docker.sock`. General implementation review approved task scope, layering, lifecycle/query semantics, proxy-safe Spring wiring, and test design with no blocking finding. Independent adversarial security review found no blocking Critical, High, or Medium issue in BOLA/IDOR design, qualified parent attachment, SQL collection isolation, ancestor eligibility, owner-aware mutation, overposting, archive-through-ancestor behavior, CSRF enforcement, or error privacy within the available source/compiled-test evidence. Authoritative PR quality run #142 (ID `33364484889`) completed successfully at reviewed head `ec3e0f8194db493c9c90750c78881734645e2a86`: `backend-quality`, `frontend-quality`, `security`, `auth-e2e`, and `phase1-gate` all succeeded. The successful PostgreSQL/Testcontainers `backend-quality` job resolves the local Docker limitation and verifies the backend suite in the authoritative CI environment. Final security-coverage head `0b785c071b6f688c73b2001919ad18bff30a5aa5` added explicit unauthenticated `401`, missing-CSRF `403`, and foreign-versus-nonexistent concealment regressions for Topic and Subtopic APIs and passed PR quality run #143 (ID `33366559766`) with `backend-quality`, `frontend-quality`, `security`, `auth-e2e`, and `phase1-gate` all successful. Implementation PR [#71](https://github.com/KennethVier/hippocampus/pull/71) merged into `main` as `a880360a442c37262b5526ae65711771c5d9b5fb`. Post-merge `main` quality run #144 (ID `33367006237`) succeeded at that merge commit with `backend-quality`, `frontend-quality`, `auth-e2e`, `security`, and `phase1-gate` successful; the push-run dependency-review step was skipped and is not counted as passed. External general implementation review and independent security review were accepted, the expected ownership-safe Topic/Subtopic hierarchy and API Definition of Done are satisfied, and no current blocker remains. No migration, dependency, frontend, authentication, Material, uniqueness, recursive hierarchy, denormalized ownership, cascade, or hard-delete change was introduced.
- **Notes / blockers:** _None_

## P2-04 — Create Material and MaterialVersion foundation

- **Workstream:** Database
- **Priority:** Must
- **Status:** Done
- **Goal:** Persist upload lifecycle separately from learning topics.
- **Build:** Create materials/material_versions schema with storage key, statuses, active_version_id, hashes/metadata.
- **How it works:** Material belongs to user; versions are immutable source revisions.
- **Dependencies:** P1 + Flyway
- **Tests / validation:** FK/version uniqueness/status tests.
- **Expected result:** Materials can have versioned lifecycle.
- **Definition of Done:** Schema/repository tests pass.
- **Authority:** Documents 18
- **Evidence / link:** Added Flyway `V6__create_material_foundation.sql` with the authoritative `materials` and `material_versions` columns, UUID keys, timezone-aware timestamps, restrictive User → Material → MaterialVersion ownership FKs, per-Material version-number uniqueness, intrinsic nonnegative source-metadata checks, and the approved owner/status and Material/processing-status indexes. The nullable active-version pointer is protected by a composite same-Material FK from `(materials.id, materials.active_version_id)` to the candidate key `(material_versions.material_id, material_versions.id)`, so dangling and cross-Material active-version assignments fail closed without a trigger. Added scalar-UUID JPA persistence entities and two minimal Spring Data repositories; structural revision identity is non-updatable while source, processing, and activation metadata remains mutable for later ingestion. Status/type/extraction fields remain `VARCHAR`/`String` without speculative closed enums or checks, `processing_progress` remains `NUMERIC(5,2)` without an invented 0–100 constraint, and no binary column or object-storage behavior was introduced. PostgreSQL/Testcontainers coverage verifies migration metadata/idempotence, exact columns/types/nullability, named PK/FK/unique/check constraints, restrictive deletion, indexes, CRUD/timestamps, ownership/parent integrity, version uniqueness/numeric invariants, nullable metadata, representative status/type round trips, metadata mutation, and active-version same-parent integrity. Local system-Maven compilation/package passed, and the focused mapping plus full ArchUnit suite passed 10 tests with zero failures, errors, or skips. The Maven Wrapper could not download because the implementation environment had no network route, and local PostgreSQL execution was unavailable because Testcontainers could not find `/var/run/docker.sock`. General implementation review accepted P2-04 scope, exact schema, persistence boundaries, constraint/index discipline, test design, and the no-ADR assessment. Independent adversarial security review found no blocking Critical, High, or Medium issue in structural ownership, cross-Material active-version prevention, restrictive deletion, private metadata boundaries, denormalization, or scope. External implementation review found no production or test blocker and requested tracker-evidence cleanup only. Authoritative quality run #147 (ID `33378122244`) succeeded on reviewed head `3b99d08e69f5d3562ce42ec54a2a82a18dc12d88`, with `backend-quality`, `frontend-quality`, `security`, `auth-e2e`, and `phase1-gate` all successful. The successful `backend-quality` PostgreSQL/Testcontainers execution resolves the local Docker limitation and authoritatively validates the Flyway migrations, persistence/integrity tests, architecture tests, and backend build. Final reviewed implementation head `210892a4d6dfc6029a70d1e5f88a59e9031daa68` passed PR quality run #148 (ID `33380058302`) with `backend-quality`, `frontend-quality`, `security`, `auth-e2e`, and `phase1-gate` all successful. Implementation PR [#73](https://github.com/KennethVier/hippocampus/pull/73) merged into `main` as `d987c0fc19bd6c278a2fbd379dbae975e802d264`. Post-merge `main` quality run #149 (ID `33380923435`) succeeded at that merge commit with `backend-quality`, `frontend-quality`, `security`, `auth-e2e`, and `phase1-gate` successful; the push-run dependency-review step was skipped and is not counted as passed. External general implementation review and independent security review were accepted, the expected versioned Material lifecycle and schema/repository Definition of Done are satisfied, no ADR or undocumented architecture deviation is required, and no current blocker remains. No dependency, API, authentication, frontend, object storage, upload, MaterialTopicLink, ProcessingJob, ingestion, RAG, AI, public URL, filesystem, or database-blob change was introduced. P2-05 remains Not Started and no Phase 2 gate was changed.
- **Notes / blockers:** _None_

## P2-05 — Implement object-storage port

- **Workstream:** Storage
- **Priority:** Must
- **Status:** Done
- **Goal:** Separate binary storage from DB.
- **Build:** Create BinaryObjectStore port + local filesystem/local S3-compatible implementation for development.
- **How it works:** Backend stores opaque storage key; user filename is display metadata only.
- **Dependencies:** Material schema
- **Tests / validation:** Contract tests: put/get/delete, traversal-safe keys.
- **Expected result:** Original file can be stored/retrieved locally without DB blob.
- **Definition of Done:** Storage contract tests pass.
- **Authority:** Documents 17,21,22
- **Evidence / link:** Added the provider-neutral `BinaryObjectStore` materials port with streaming put/get and idempotent delete, exact declared-length enforcement, caller-owned stream lifecycle, replacement semantics, typed missing-object behavior, and safe infrastructure-failure translation. Added an immutable `BinaryObjectKey` that preserves accepted hierarchical ASCII keys exactly while rejecting empty/dot/traversal segments, absolute and Windows/UNC-style paths, backslashes, controls, percent-encoded-looking text, invalid characters, and excessive segment/key lengths without decoding or mutation. Added one JDK-only development `FileSystemBinaryObjectStore`; it verifies a configured real root, confines logical segments beneath it, rejects symbolic-link roots/ancestors/targets, creates parents one segment at a time, stages bounded writes in the verified target directory, consumes at most declared length plus one verification byte, cleans failed staging files, preserves an existing final object when streaming fails, attempts atomic replacement with a documented same-filesystem fallback, and never recursively deletes. Typed filesystem configuration activates only for explicit `hippocampus.storage.backend=filesystem` under `local & !pilot`; focused configuration tests prove local enablement, pilot disablement, both local/pilot profile orders disablement, missing-backend disablement, safe invalid/symlink-root failure, and datasource-free isolation. `application-local.yml` supplies the explicit backend and overridable user-home development root; base and pilot configuration are unchanged. No Maven/Docker/dependency, database/migration/entity/repository, API/upload, authorization, key-generation policy, frontend, public URL, telemetry, or R2/S3 provider implementation was introduced; the private PILOT R2 adapter remains deferred and no ADR is required. System-Maven focused validation passed 55 tests with zero failures, errors, or skips, including the existing eight ArchUnit tests plus key, binary contract, length-bound, staging, traversal, root/symlink, stream-lifecycle, delete, and configuration coverage. Maven Wrapper validation was unavailable because its distribution download had no network route. System-Maven `clean verify` compiled all sources and ran 192 tests with zero assertion failures and zero skips, but ended with 70 PostgreSQL/Testcontainers environment errors because Docker and `/var/run/docker.sock` were unavailable; all P2-05 tests passed in that run. General implementation review approved P2-05 scope, module/port ownership, provider-neutral streaming contract, exact-length bound, confined staged filesystem behavior, honest atomic-move fallback, fail-closed pilot wiring, dependency discipline, and tests with no unresolved blocker. Independent adversarial security review found no blocking Critical, High, or Medium finding within the P2-05 source and executed evidence for CWE-22/CWE-73 traversal and path control, CWE-59 link following, unsafe delete/root escape, separator confusion, bounded writes, staging cleanup, safe errors, pilot defaults, public exposure, and credentials; portable filesystem TOCTOU/reparse-point races remain a documented residual limitation of this controlled development adapter and are not represented as eliminated. Authoritative quality run #152 (ID `33386739533`) succeeded at reviewed implementation head `378329205fc250dfcad7251b79faf367d97911f9`, with `backend-quality`, `frontend-quality`, `security`, `auth-e2e`, and `phase1-gate` all successful. The successful `backend-quality` job resolves the implementation environment's missing-Docker/Testcontainers limitation and authoritatively validates the PostgreSQL/Testcontainers/backend suite. External review found no production-code behavioral blocker and no test blocker; its only requested changes were the `BinaryObjectStore` declared-length Javadoc precision and this tracker-evidence cleanup. Final reviewed implementation head `30afe32f6d942b89669c5a1534b4a75a4cccade6` passed PR quality run #153 (ID `33387623240`) with `backend-quality`, `frontend-quality`, `security`, `auth-e2e`, and `phase1-gate` all successful. Implementation PR [#75](https://github.com/KennethVier/hippocampus/pull/75) merged into `main` as `6740f6f5f044cee2ff790d2dac3c2860a207fc64`. Post-merge `main` quality run #154 (ID `33388013810`) succeeded at that merge commit with `backend-quality`, `frontend-quality`, `security`, `auth-e2e`, and `phase1-gate` all successful; the push-run dependency-review step was skipped and is not counted as passed. External general implementation review and independent security review were accepted, the expected local original-file storage behavior and storage-contract Definition of Done are satisfied, no ADR or undocumented architecture deviation is required, and no current blocker remains. The production/PILOT R2 adapter remains intentionally deferred and does not block P2-05.
- **Notes / blockers:** _None_

## P2-06 — Implement upload initialization endpoint

- **Workstream:** Backend
- **Priority:** Must
- **Status:** Done
- **Goal:** Accept supported material and create processing placeholder.
- **Build:** Multipart endpoint validates basic limits/type, stores original, creates Material + v1 + initial state/job placeholder.
- **How it works:** HTTP returns quickly; does not parse whole document.
- **Dependencies:** Object store + material schema
- **Tests / validation:** Multipart API tests: valid, empty, unsupported, oversized synthetic.
- **Expected result:** Upload returns PROCESSING/UPLOADED metadata promptly.
- **Definition of Done:** API and storage tests pass.
- **Authority:** Documents 19,21
- **Evidence / link:** Implementation PR [#77](https://github.com/KennethVier/hippocampus/pull/77) implements authenticated, CSRF-protected `POST /api/materials` multipart intake for exactly one `file` part. Preliminary declared-MIME routing accepts `application/pdf`, `image/jpeg`, `image/png`, and `text/plain` as provisional untrusted transport metadata and returns `201 Created` without exposing a storage key or premature `Location` header. Ownership derives exclusively from `CurrentUser`; no client-controlled user ID, owner ID, material/version ID, storage key, or storage path is authoritative. The upload path streams `MultipartFile.getInputStream()` through the existing `BinaryObjectStore` without whole-file `getBytes()`/`readAllBytes()` buffering. Storage uses an independent opaque server-generated UUID key under `materials/{uuid}/original`; the original filename does not participate in the authoritative storage key, and existing Hibernate-generated Material and MaterialVersion UUID semantics remain unchanged. One Material and one MaterialVersion at version 1 are persisted through a short transactional relational adapter after original-object storage succeeds. `Material.storage_key` remains null, `MaterialVersion.storage_key` stores the original-object key, `Material.status` and `MaterialVersion.processing_status` are `UPLOADED`, and `processing_progress`, `active_version_id`, and `activated_at` remain null. No `ProcessingJob` schema/entity/queue is introduced; the version-1 `UPLOADED` state is the P2-06 durable processing placeholder until Phase 3 processing begins. Storage failure produces no relational creation; relational failure after successful storage triggers best-effort idempotent object deletion, and compensation failure cannot produce false success. Upload composition requires `CurrentUser`, `BinaryObjectStore`, and `MaterialUploadPersistence`; datasource-free/storage-only/persistence-only contexts remain bootable without the upload route, and pilot remains fail-closed with no local filesystem upload fallback. Review remediation added deterministic pre-controller `MaxUploadSizeExceededException` handling with sanitized `413 UPLOAD_TOO_LARGE`, validates Spring Boot multipart transport limits against the application upload limit, and adds PostgreSQL/Testcontainers evidence that forced MaterialVersion persistence failure rolls back both Material and MaterialVersion rows. Tests cover valid PDF/JPEG/PNG/text uploads, missing/repeated/empty/unsupported/oversized requests, authentication and CSRF rejection, multi-user same-filename ownership/key isolation, real filesystem object round-trip, relational persistence and rollback, storage/database compensation, configuration composition, and architecture boundaries. External implementation review approved the P2-06 runtime implementation after remediation and found no remaining production-code blocker; security-focused review found no blocking Critical, High, or Medium issue in the reviewed authentication, CSRF, ownership, storage-key, upload-size/resource-bound, compensation, error-privacy, and pilot fail-closed surface. Authoritative PR quality run #158 (ID `33400021044`) succeeded on reviewed implementation head `ba9f9f5a747afb0ee2d513b610f05d304628e933`, with `backend-quality`, `frontend-quality`, `security`, `auth-e2e`, and `phase1-gate` all successful; `backend-quality` executed the backend tests, architecture tests, PostgreSQL/Testcontainers migrations/integration tests, and backend build. Final PR head `fe6024cd85e61ad696878ce9495da39be8cdaa44` passed quality run #171 (ID `33407386439`) with `backend-quality`, `frontend-quality`, `security`, `auth-e2e`, and `phase1-gate` all successful; the PR `security` job completed both commit-range secret scanning and introduced-dependency review successfully. Implementation PR #77 was externally approved and merged into `main` as `39a4842f442984312c256e7e82ec1f83b7a769ec`. Post-merge `main` quality run #172 (ID `33407932193`) succeeded at that merge commit with `backend-quality`, `frontend-quality`, `security`, `auth-e2e`, and `phase1-gate` all successful; `backend-quality` ran backend tests, architecture tests, migrations, and build, and the push-run dependency-review step was skipped and is not counted as passed. No migration, new Maven dependency, frontend, Tika/PDFBox, ProcessingJob/P3-01, R2, P2-07, or P2-11 implementation was introduced; no ADR or undocumented architecture deviation is required. External review is accepted, the expected upload behavior and API/storage Definition of Done are satisfied, final merge and authoritative CI evidence are recorded, and no current blocker remains.
- **Notes / blockers:** _None_

## P2-07 — Implement material list/detail/delete

- **Workstream:** Backend
- **Priority:** Must
- **Status:** Done
- **Goal:** Let student manage uploaded resources.
- **Build:** Create list/detail/delete APIs including status and metadata.
- **How it works:** Delete immediately removes future retrieval eligibility when later indexes exist.
- **Dependencies:** Material foundation
- **Tests / validation:** Authorization, pagination, delete-state tests.
- **Expected result:** Student sees only own materials and can remove them.
- **Definition of Done:** CRUD + ownership tests pass.
- **Authority:** Documents 18,22
- **Evidence / link:** Implementation PR [#79](https://github.com/KennethVier/hippocampus/pull/79) delivered authenticated owner-scoped material list/detail/delete APIs with bounded SQL pagination, privacy-minimal responses, indistinguishable `MATERIAL_NOT_FOUND` handling for foreign/deleted/missing resources, and CSRF-protected idempotent logical deletion. The reviewed final head `72074726d64c69c02b53e37ad3fe49dab6c19e64` uses an atomic owner-qualified lifecycle update that sets `status = DELETED`, clears `active_version_id`, and advances `updated_at` exactly once; repeated DELETE returns success without rewriting the lifecycle timestamp. PostgreSQL/Testcontainers integration coverage verifies ownership isolation, pagination, response privacy, authentication, CSRF, foreign-mutation preservation, delete state, MaterialVersion preservation, immediate list/detail concealment, lifecycle timestamp advancement, and repeated-delete timestamp stability. External general implementation review verdict: **APPROVED**. Independent adversarial security review verdict: **SECURITY PASS** for the P2-07 changed surface, with no unresolved blocking Critical, High, or Medium finding. Authoritative PR quality run #185 (ID `33465960000`) succeeded on the reviewed head with `backend-quality`, `frontend-quality`, `security`, `auth-e2e`, and `phase1-gate` all successful; the PR `security` job completed explicit commit-range secret scanning and introduced-dependency review successfully. PR #79 merged into `main` as `8e3892bc9c9d22aa2bd54e5685318181a7c19afe`. Post-merge `main` quality run #186 (ID `33466272574`) succeeded at that merge commit with `backend-quality`, `frontend-quality`, `security`, `auth-e2e`, and `phase1-gate` all successful; the push-run dependency-review step was skipped and is not counted as passed. No migration, dependency, frontend, object-store deletion, MaterialVersion mutation, MaterialTopicLink/P2-08, upload hardening/P2-11, ProcessingJob, ingestion, RAG, or AI scope was introduced. Documents 18 and 22 remain satisfied, no ADR or undocumented architecture deviation is required, the expected owner-private material management behavior and CRUD/ownership Definition of Done are satisfied, and no current blocker remains.
- **Notes / blockers:** _None_

## P2-08 — Create MaterialTopicLink

- **Workstream:** Database
- **Priority:** Must
- **Status:** Done
- **Goal:** Keep Material ≠ Topic while allowing mappings.
- **Build:** Create material_topic_links with origin/status/version/node optional references.
- **How it works:** Mapping is many-to-many and validates same-user ownership.
- **Dependencies:** Subject/topic + material
- **Tests / validation:** Cross-user mapping rejection, duplicate-active-link tests.
- **Expected result:** A material can support many topics and vice versa.
- **Definition of Done:** Schema/use-case tests pass.
- **Authority:** Documents 14,18
- **Evidence / link:** Implementation PR [#82](https://github.com/KennethVier/hippocampus/pull/82) delivered Flyway `V7__create_material_topic_links.sql`, the owner-scoped `CreateUserSelectedMaterialTopicLink` application use case, one atomic JDBC persistence adapter, Spring auto-configuration, and focused domain/application/PostgreSQL security-negative and integrity tests. Earlier PR runs #189 and #190 exposed Spring composition/proxy-ordering and PostgreSQL JDBC typing/timestamp defects; those defects were remediated before final review. Final reviewed PR head `1951322d8ea6eb783df40f25ed90009e503eb29c` passed authoritative PR quality run [#192](https://github.com/KennethVier/hippocampus/actions/runs/33476537114) (ID `33476537114`) with `backend-quality`, `frontend-quality`, `auth-e2e`, automated `security`, and `phase1-gate` all successful; the PR `security` job completed explicit commit-range secret scanning and introduced-dependency review successfully. External general implementation review verdict: **APPROVED**. Independent adversarial security review verdict: **SECURITY PASS**, recorded in PR #82 final review comment #5489767193 after review of ownership/BOLA isolation, foreign/missing concealment, MaterialVersion integrity, deleted-Material fail-closed behavior, SQL parameterization, server-owned `USER_SELECTED`/`ACTIVE` provenance, duplicate-active/concurrency integrity, restrictive DB constraints, Phase-2 DocumentNode disablement, and the absence of new dependency/secret/API/frontend/upload/AI/RAG exposure. The implementation remains aligned with Documents 14, 18, 19, and 26; no ADR or undocumented architecture deviation is required, and P3-05 retains ownership of actual DocumentNode schema/integration. PR #82 merged into `main` as `cc58fbd40fee3e034c42e8731c7a0be4f9534ef1`. Post-merge `main` quality run [#193](https://github.com/KennethVier/hippocampus/actions/runs/33477254228) (ID `33477254228`) succeeded at that exact merge commit with `backend-quality`, `frontend-quality`, `auth-e2e`, `security`, and `phase1-gate` successful; the push-run dependency-review step was skipped and is not counted as passed. The expected many-to-many owner-safe mapping behavior is established, schema/use-case tests and PostgreSQL/Testcontainers integrity/concurrency coverage are green, the Definition of Done is satisfied, final review/security/merge/CI evidence is factual, and no current blocker remains.
- **Notes / blockers:** _None_

## P2-09 — Build Subjects and Topics UI

- **Workstream:** Frontend
- **Priority:** Must
- **Status:** Done
- **Goal:** Make organization usable without feature overload.
- **Build:** Create subject list, subject detail, topic cards/forms, archive interactions.
- **How it works:** Primary CTA from topic is future Study Mission, not tool selector.
- **Dependencies:** Subject/topic APIs
- **Tests / validation:** RTL + Playwright CRUD journey.
- **Expected result:** Student organizes study areas cleanly.
- **Definition of Done:** Desktop/mobile CRUD flow passes.
- **Authority:** Documents 20
- **Evidence / link:** Implementation PR [#84](https://github.com/KennethVier/hippocampus/pull/84) delivered the responsive authenticated `/subjects` Subject CRUD/pagination screen and `/subjects/:subjectId` Subject context with ACTIVE-parent-gated Topic CRUD/pagination, using the feature-local `learning-organization` boundary, TanStack Query server state, centralized CSRF-capable `apiClient`, runtime Zod response validation, separate RHF/Zod Subject/Topic forms, existing accessible UI primitives, and token-based responsive styling. The reviewed implementation keeps `/topics/:topicId` as a placeholder, exposes no Subject sort-order control (create sends `sortOrder: null`; update preserves the authoritative loaded value), does not query or mutate Topics for an owned archived Subject, and leaves Subtopics, Study Missions, Materials, AI/RAG, Tailwind, backend changes, global server-state, and P2-10+ outside scope. Initial PR run #196 (`33484201179`) exposed an ambiguous Playwright heading locator; later run #199 (`33486002845`) exposed a React Strict Mode dialog-composition defect during the real CRUD journey. Both and the external-review form/error/API-test findings were remediated before final approval. Final reviewed PR head `434104486b5f07095b8f2c64282e1d34ea8c8994` passed authoritative PR quality run [#201](https://github.com/KennethVier/hippocampus/actions/runs/33486517691) (ID `33486517691`) with `frontend-quality`, `backend-quality`, automated `security`, `auth-e2e`, and `phase1-gate` all successful. `frontend-quality` passed locked install, lint, typecheck, the full Vitest/RTL suite, production build, and npm audit; the PR `security` job passed explicit commit-range secret scanning and introduced-dependency review. The real browser job ran three non-skipped tests: the existing authoritative two-session isolation journey plus the full P2-09 organization CRUD journey on desktop Chromium and mobile Chromium. External general implementation re-review verdict: **APPROVED**. Independent adversarial security review verdict: **SECURITY PASS** for the P2-09 changed surface, after review of protected-route/session boundaries, centralized cookie/CSRF handling, server-authoritative ownership/BOLA assumptions, UUID/path handling, foreign/missing concealment, archived-parent gating, XSS-safe React text rendering, absence of private browser persistence, runtime response validation, safe errors, and the introduced dependencies; the final verdict is recorded in PR review ID `5076656122`. GitHub formal self-approval was unavailable because the connected account is the PR author; that platform restriction is not counted as a review failure. PR #84 merged into `main` as `c0ccba23353f80b1c3d35992f3aa060852bba47b`. Post-merge `main` quality run [#202](https://github.com/KennethVier/hippocampus/actions/runs/33495646422) (ID `33495646422`) succeeded on that exact merge commit with `backend-quality`, `frontend-quality`, `auth-e2e`, `security`, and `phase1-gate` successful. The push-run dependency-review step was skipped and is not counted as passed; introduced-dependency review already passed on PR run #201. The expected student organization behavior is established, the desktop/mobile CRUD Definition of Done is satisfied, no ADR or undocumented architecture deviation is required, and no current blocker remains.
- **Notes / blockers:** _None_

## P2-10 — Build Materials UI and upload transfer progress

- **Workstream:** Frontend
- **Priority:** Must
- **Status:** Done
- **Goal:** Provide simple upload lifecycle.
- **Build:** Create materials list, file picker/drop zone, transfer progress, metadata and delete UI.
- **How it works:** Transfer progress is distinct from backend processing progress.
- **Dependencies:** Material APIs
- **Tests / validation:** Component/E2E upload tests.
- **Expected result:** Student can upload and see accepted file immediately.
- **Definition of Done:** Upload journey passes.
- **Authority:** Documents 20,21
- **Evidence / link:** Implemented the authenticated `/materials` and `/materials/:materialId` experiences with a feature-local Materials boundary, TanStack Query list/detail ownership, one-based URL pagination backed by bounded 12-item API pages, strict Zod validation for nullable normal Material metadata and the distinct upload response, accessible native file selection plus supplementary single-file drop behavior, factual upload acceptance metadata, concealed unavailable-resource handling, and confirmation-gated logical deletion. Added a centralized progress-capable `XMLHttpRequest` multipart path without a dependency or global fetch replacement; it reuses centralized CSRF acquisition, credentials, controlled headers, safe response/ProblemDetail normalization, correlation IDs, AbortSignal behavior, exact expected `201` enforcement, browser-owned multipart boundaries, and determinate/indeterminate request-transfer events. The local discriminated upload state keeps transfer completion separate from backend acceptance (`Uploading` -> `Finishing upload` -> validated `201` acceptance), performs no automatic retry, inserts no upload DTO into normal Material caches, and refetches only authoritative Material lists. Extended private-session cleanup with an in-memory callback registration seam that aborts active non-Query work before query cancellation/cache clearing, isolates throwing callbacks, guarantees cache clearing even when cancellation fails, and prevents stale User A upload completion from restoring private UI/cache state. Focused Vitest evidence passed: API client 54 tests, Materials API 9 tests, upload reducer 5 tests, Materials RTL 8 tests, and private cleanup 6 tests. Full frontend validation passed 182 tests across 13 files, TypeScript typecheck, ESLint, and production build. Playwright adds the real upload/detail/confirmation/delete journey for desktop and mobile Chromium using a tiny deterministic text fixture and does not assert transient transfer timing. GitHub Actions quality run #208 successfully executed all five real browser tests, including the Materials upload/open/delete journey on desktop and mobile Chromium; run #208 was not an overall passing quality run because frontend-quality exposed the corrected test synchronization defect. General implementation review verdict: **APPROVED** after ensuring cancellation failure cannot strand session teardown and unavailable list deletion reconciles authoritative state. Independent adversarial security review verdict: **SECURITY PASS** for the changed frontend surface, with no unresolved Critical, High, or Medium finding across cookie/CSRF ownership, controlled headers, XHR settlement/abort behavior, error/body/secret non-disclosure, hostile filename rendering, BOLA-concealed resource behavior, MIME authority, unsafe retry, browser persistence, and shared-device cleanup; this scoped verdict does not claim absolute security. No backend, migration, workflow, package dependency/lockfile, object-storage configuration, telemetry, processing, AI/RAG, P2-11, P2-12, or P2-13 implementation was introduced, and no ADR is required. Final completion evidence: implementation PR [#88](https://github.com/KennethVier/hippocampus/pull/88) at final reviewed head `59fd38497ec4cf6d41d604a2710796b923e44a53` passed authoritative PR quality run [#209](https://github.com/KennethVier/hippocampus/actions/runs/33541679135) (ID `33541679135`) with `frontend-quality`, `backend-quality`, `security`, `auth-e2e`, and `phase1-gate` all successful; `auth-e2e` ran five non-skipped tests, including the Materials upload/open/delete journey on desktop and mobile Chromium. PR #88 merged into `main` as `53bc051098bd42351726dec9e8f24572440f335d`. Post-merge `main` quality run [#210](https://github.com/KennethVier/hippocampus/actions/runs/33542761927) (ID `33542761927`) succeeded on that exact merge commit with `backend-quality`, `frontend-quality`, `auth-e2e`, `security`, and `phase1-gate` successful. The push-run dependency-review step was skipped and is not counted as passed; introduced-dependency review already passed on PR run #209. External general implementation review remains **APPROVED**, independent adversarial security review remains **SECURITY PASS**, the upload journey Definition of Done is satisfied, no current blocker remains, and no ADR or undocumented architecture deviation is required.
- **Notes / blockers:** _None_

## P2-11 — Harden upload intake baseline

- **Workstream:** Security
- **Priority:** Must
- **Status:** Done
- **Goal:** Reject obvious malicious/invalid input before parsing.
- **Build:** Validate filename as metadata, MIME via Tika/content, size and supported types; safe generated storage keys.
- **How it works:** Input cannot select paths or bypass type checks.
- **Dependencies:** Upload API
- **Tests / validation:** Path traversal filename, disguised MIME, empty/corrupt/basic limit tests.
- **Expected result:** Unsafe intake is rejected safely.
- **Definition of Done:** Security fixture tests pass.
- **Authority:** Documents 21,22,25
- **Evidence / link:** Local implementation on branch `feat/p2-11-upload-intake-hardening` adds byte-content upload inspection before storage using `org.apache.tika:tika-core:4.0.0`, with a `MaterialContentInspector` port and Tika adapter behind the existing materials application boundary. Actual detected MIME is authoritative for accepted material type, while a specific browser-declared supported MIME that contradicts detected content fails closed with sanitized `415 UPLOAD_TYPE_MISMATCH`; missing, blank, and generic `application/octet-stream` declarations remain acceptable when actual content is supported. Intake rejects empty and oversized uploads before inspection, unsupported detected content with `415 UPLOAD_TYPE_UNSUPPORTED`, basic invalid/readability failures such as PDF content missing required intake markers with sanitized `400 UPLOAD_CONTENT_INVALID`, and stream length/read failures without leaking parser internals. Original filenames remain metadata only, including path-like names, and storage continues to use opaque server-generated `materials/{uuid}/original` keys. Added fixtures and focused tests for valid PDF/JPEG/PNG/text, generic/missing declarations, disguised MIME, corrupt PDF, unsupported ZIP-like content, empty/oversized limits, path traversal filename metadata, repeatable stream inspection/store behavior, auth/CSRF preservation, safe error bodies, configuration wiring, and dependency resolution. Local validation passed: `.\mvnw.cmd '-Dtest=UploadMaterialTests,MaterialUploadControllerWebTests,MaterialUploadExceptionHandlerTests,MaterialUploadConfigurationTests,TikaMaterialContentInspectorTests' test` (27 tests, 0 failures); `.\mvnw.cmd '-Dtest=BinaryObjectKeyTests,FileSystemBinaryObjectStoreTests,HippocampusArchitectureTests' test` (48 tests, 0 failures, 3 environment/capability skips); `.\mvnw.cmd dependency:tree '-Dincludes=org.apache.tika:tika-core,commons-io:commons-io,org.commonmark:*'` resolved Tika 4.0.0, commons-io 2.22.0, and existing CommonMark dependencies successfully. `.\mvnw.cmd '-Dtest=MaterialUploadControllerIntegrationTests' test` could not execute in this local environment because Testcontainers could not find a valid Docker environment; this is not counted as passing and must be validated in Docker-capable CI or another authoritative environment before `Done`. No Phase 3 parser, ingestion job, OCR, RAG, AI, frontend, schema, object-storage configuration, or upload-and-chat scope was introduced. No ADR or undocumented architecture deviation is required. Final completion evidence: implementation PR [#90](https://github.com/KennethVier/hippocampus/pull/90) at final reviewed head `aa0afaaec975dd8eab97dcb2e0bf4eb37416ed32` received external general implementation review **APPROVED** (review ID `5085332219`) after contradictory declared-MIME remediation, and independent adversarial review **SECURITY PASS** (review ID `5085370027`) scoped to the P2-11 changed surface. Authoritative PR quality run [#215](https://github.com/KennethVier/hippocampus/actions/runs/33587859136) (ID `33587859136`) succeeded with `backend-quality`, `frontend-quality`, `security`, `auth-e2e`, and `phase1-gate`; Docker-capable `backend-quality` resolved the local Docker/Testcontainers limitation, and the PR `security` job passed the introduced-dependency review for Tika. PR #90 merged into `main` as `4bd0fa52907c7c077305064615d429d7be7755d5`. Post-merge `main` quality run [#216](https://github.com/KennethVier/hippocampus/actions/runs/33588491769) (ID `33588491769`) succeeded on that merge commit with `backend-quality`, `frontend-quality`, `security`, `auth-e2e`, and `phase1-gate`; its push-run introduced-dependency-review step was skipped and is not counted as passing. The applicable introduced-dependency review passed on PR run #215. The security-fixture Definition of Done is satisfied, no current blocker remains, and no ADR or undocumented architecture deviation is required.
- **Notes / blockers:** _None_

## P2-12 — Add material lifecycle telemetry

- **Workstream:** Observability
- **Priority:** Should
- **Status:** Done
- **Goal:** Make uploads diagnosable.
- **Build:** Log/metric material accepted/rejected/deleted and status changes using IDs only.
- **How it works:** No source content or secrets in logs.
- **Dependencies:** Material lifecycle
- **Tests / validation:** Inspect logs; privacy grep tests.
- **Expected result:** Operations can diagnose upload lifecycle.
- **Definition of Done:** Telemetry fields documented/tested.
- **Authority:** Documents 24
- **Evidence / link:** Implementation PR [#92](https://github.com/KennethVier/hippocampus/pull/92) adds a narrow `MaterialLifecycleTelemetry` port with Micrometer/ECS structured-log infrastructure for upload accepted, upload rejected, operational upload failure, actual material deletion, and the currently factual `UPLOADED` / `DELETED` status transitions. Upload acceptance is emitted only after object storage succeeds and transactional relational Material/MaterialVersion persistence returns with authoritative IDs. Upload rejection/failure telemetry is owned once by `MaterialUploadExceptionHandler`, including transport-level oversized multipart rejection, with invalid intake separated from storage/persistence operational failures. Deletion persistence distinguishes `DELETED`, `ALREADY_DELETED`, and `NOT_FOUND`; repeated idempotent DELETE emits no second lifecycle transition, foreign/missing resources remain concealed, and committed deletion telemetry is deferred by infrastructure to transaction `afterCommit`. General-review remediation prevents an active transaction without synchronization from emitting premature pre-commit lifecycle telemetry. Structured logs use only allowlisted IDs, server-owned states/error codes, and the existing validated correlation ID; filenames, titles, source content, storage keys/paths, emails, sessions/cookies, CSRF/Authorization values, raw MIME metadata, and raw exception messages are excluded. Metrics use only finite server-owned `reason`, `scope`, and `status` tags and never resource/user/correlation IDs. No dependency, migration, frontend, ProcessingJob, Phase 3 ingestion, RAG/AI telemetry, or external observability infrastructure was introduced. Final reviewed code head `5cb0de5a686fc3ff353cedd3f354e04b72696be5` received external general implementation review **APPROVED** (review ID `5085727123`) and independent adversarial review **SECURITY PASS** (review ID `5085731130`) with no unresolved blocking Critical, High, or Medium finding. Final PR head `0e7a19b5bb8d6ead51bf6b7f05a1b6c7a81c1f65` passed authoritative PR quality run [#222](https://github.com/KennethVier/hippocampus/actions/runs/33593914104) (ID `33593914104`) with `backend-quality`, `frontend-quality`, `security`, `auth-e2e`, and `phase1-gate` all successful; the PR `security` job passed explicit commit-range secret scanning and introduced-dependency review. PR #92 merged into `main` as `5dd43b6560731a8f26b7785482dceb88aaff9de4`. Post-merge `main` quality run [#223](https://github.com/KennethVier/hippocampus/actions/runs/33594428285) (ID `33594428285`) succeeded on that exact merge commit with `backend-quality`, `frontend-quality`, `security`, `auth-e2e`, and `phase1-gate` all successful. The push-run introduced-dependency-review step was skipped and is not counted as passed; the applicable introduced-dependency review passed on PR run #222. The expected privacy-safe diagnosable lifecycle behavior and telemetry-field Definition of Done are satisfied, no current blocker remains, and no ADR or undocumented architecture deviation is required.
- **Notes / blockers:** _None_

## P2-13 — Phase 2 end-to-end gate

- **Workstream:** Gate
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Prove private learning organization and upload lifecycle.
- **Build:** Run create subject/topic, upload file, link/delete, cross-user checks.
- **How it works:** All components operate together.
- **Dependencies:** All P2 tasks
- **Tests / validation:** Playwright + backend integration.
- **Expected result:** Student can organize private materials safely.
- **Definition of Done:** Phase gate green.
- **Authority:** Documents 26
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

# Phase 3 — File Processing & Ingestion

**Primary goal:** Large/mixed medical files become structured, traceable chunks/visuals through durable processing.

**Milestone:** M1 — Material Workspace

**Implementation items:** 18


## P3-01 — Create ProcessingJob schema

- **Workstream:** Database
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Persist durable pipeline state.
- **Build:** Create processing_jobs table/entity with type,status,priority,progress,attempts,locks,next_attempt,heartbeat/error fields.
- **How it works:** Worker state survives restarts.
- **Dependencies:** P2 materials
- **Tests / validation:** Migration/constraint tests.
- **Expected result:** Jobs can be claimed/retried durably.
- **Definition of Done:** Schema tests pass.
- **Authority:** Documents 18,21
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P3-02 — Implement atomic job claiming

- **Workstream:** Processing
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Prevent duplicate worker execution.
- **Build:** Implement PostgreSQL FOR UPDATE SKIP LOCKED claim transaction.
- **How it works:** Worker marks RUNNING/lock fields then commits before work.
- **Dependencies:** ProcessingJob
- **Tests / validation:** Two-worker concurrency integration test.
- **Expected result:** Only one worker claims a job.
- **Definition of Done:** Concurrency test passes.
- **Authority:** Documents 21,25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P3-03 — Implement processing dispatcher and stage handlers

- **Workstream:** Processing
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Make pipeline explicit.
- **Build:** Create dispatcher plus VALIDATE/EXTRACT/STRUCTURE/VISUAL/NORMALIZE/CHUNK handlers.
- **How it works:** Each stage is independent, observable and retryable.
- **Dependencies:** Job claiming
- **Tests / validation:** Unit tests per dispatcher route.
- **Expected result:** Jobs execute correct handler and next stage.
- **Definition of Done:** Handler contract tests pass.
- **Authority:** Documents 21
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P3-04 — Implement PDF metadata/native text extraction

- **Workstream:** Parsing
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Read native PDFs in batches.
- **Build:** Use Tika/PDFBox to detect page count, metadata, extract ordered page text without whole-file string.
- **How it works:** Persist bounded batch output.
- **Dependencies:** Object storage + jobs
- **Tests / validation:** Native PDF fixture, 600-page synthetic/fixture memory test.
- **Expected result:** Native PDFs become page/text blocks with progress.
- **Definition of Done:** Fixture output matches expected.
- **Authority:** Documents 21,25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P3-05 — Create DocumentNode/TextBlock schema

- **Workstream:** Database
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Persist source structure before chunking.
- **Build:** Create document_nodes/text_blocks tables/entities/repos with source order/page/extraction quality.
- **How it works:** Blocks retain hierarchy and provenance.
- **Dependencies:** Extraction
- **Tests / validation:** Migration/repository tests.
- **Expected result:** Normalized source structure is queryable.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 18,21
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P3-06 — Classify pages by extraction type

- **Workstream:** Parsing
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Drive OCR decisions safely.
- **Build:** Classify NATIVE_TEXT/MIXED/IMAGE_ONLY/UNREADABLE using native extraction signals.
- **How it works:** Only pages needing OCR go to OCR port.
- **Dependencies:** PDF extraction
- **Tests / validation:** Mixed/scanned fixture classification tests.
- **Expected result:** Pages route correctly.
- **Definition of Done:** Classification tests pass.
- **Authority:** Documents 21
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P3-07 — Create OCR port and initial implementation

- **Workstream:** OCR
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Support scanned pages without coupling pipeline.
- **Build:** Define OcrPort/OcrResult and select practical initial implementation for local/pilot; persist method/quality.
- **How it works:** OCR uncertainty remains metadata.
- **Dependencies:** Page classification
- **Tests / validation:** Strong/poor/no-text fixture tests.
- **Expected result:** Scanned pages yield text or explicit limitation.
- **Definition of Done:** Adapter tests pass; engine documented.
- **Authority:** Documents 21,27
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P3-08 — Implement structure detection heuristics

- **Workstream:** Parsing
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Recover chapters/sections cheaply before AI.
- **Build:** Use bookmarks/TOC/font/layout/numbering/repeated patterns to create DocumentNodes.
- **How it works:** Native signals first; AI not required for obvious structure.
- **Dependencies:** Text blocks
- **Tests / validation:** Golden structure fixtures.
- **Expected result:** Known headings/sections detected with source order.
- **Definition of Done:** Fixture hierarchy matches expected tolerance.
- **Authority:** Documents 21
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P3-09 — Implement bounded AI-assisted structure fallback

- **Workstream:** AI/Ingestion
- **Priority:** Should
- **Status:** Not Started
- **Goal:** Use AI only for ambiguous hierarchy.
- **Build:** Create typed structure-detection AI task for unresolved segments with strict structured output/versioning.
- **How it works:** Only ambiguous sampled content is sent; output labeled AI_ASSISTED.
- **Dependencies:** P5 AI provider normally later; create interface/stub now and enable after P5
- **Tests / validation:** Mock contract tests now; live enablement deferred.
- **Expected result:** Pipeline can defer ambiguous structure without blocking deterministic extraction.
- **Definition of Done:** Stub boundary complete; no premature provider coupling.
- **Authority:** Documents 10,21
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P3-10 — Extract embedded/source visuals

- **Workstream:** Visuals
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Preserve anatomy/medical figures as first-class assets.
- **Build:** Extract images per page, store binary in object store, persist visual metadata/content hash.
- **How it works:** Original image bytes retained with page/hierarchy.
- **Dependencies:** PDF extraction + object store
- **Tests / validation:** Mixed PDF fixture expected visual count/page/hash.
- **Expected result:** Visuals survive extraction and retry without duplicates.
- **Definition of Done:** Visual fixture tests pass.
- **Authority:** Documents 18,21
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P3-11 — Associate captions and nearby text

- **Workstream:** Visuals
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Preserve visual context for later RAG.
- **Build:** Detect caption proximity/figure labels and create associations to blocks/nodes.
- **How it works:** Uncertain association remains limited rather than fabricated.
- **Dependencies:** Visual extraction + TextBlocks
- **Tests / validation:** Figure/caption golden fixtures.
- **Expected result:** Correct caption/nearby text attached.
- **Definition of Done:** Association tests pass.
- **Authority:** Documents 21
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P3-12 — Implement safe table text handling

- **Workstream:** Tables
- **Priority:** Should
- **Status:** Not Started
- **Goal:** Keep common medical tables semantically useful.
- **Build:** Detect basic table-like layouts; preserve row/column text when reliable; fallback to visual/limited text otherwise.
- **How it works:** Never invent cell relations when uncertain.
- **Dependencies:** Extraction
- **Tests / validation:** Simple/complex/failure table fixtures.
- **Expected result:** Reliable tables searchable; uncertain tables marked limited.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 21
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P3-13 — Normalize extraction safely

- **Workstream:** Normalization
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Remove noise without damaging medical notation.
- **Build:** Handle repeated headers/footers, line wraps/hyphenation conservatively; preserve Na+, Ca2+, β1, C5-T1 etc.
- **How it works:** Normalization keeps original provenance.
- **Dependencies:** TextBlocks
- **Tests / validation:** Golden medical-symbol tests.
- **Expected result:** Searchable text improves without term corruption.
- **Definition of Done:** Normalization regression suite passes.
- **Authority:** Documents 21
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P3-14 — Implement hierarchy-aware chunker

- **Workstream:** Chunking
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Create retrieval units from coherent source sections.
- **Build:** Chunk ordered blocks by semantic/document boundaries, token budget and conservative overlap; store heading path/page range/version.
- **How it works:** Chunks are not topics.
- **Dependencies:** Normalized blocks
- **Tests / validation:** Golden chunk fixtures; max-token and boundary tests.
- **Expected result:** Chunks are coherent, traceable and bounded.
- **Definition of Done:** Chunking tests pass.
- **Authority:** Documents 13,18,21
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P3-15 — Implement progress/heartbeat/retry

- **Workstream:** Processing
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Make long processing recoverable.
- **Build:** Update page/chunk progress and last heartbeat; classify transient/fatal/partial failures; bounded retries/backoff.
- **How it works:** Restart resumes from durable intermediate state.
- **Dependencies:** All handlers
- **Tests / validation:** Simulated timeout/provider/storage failures; restart test.
- **Expected result:** Jobs recover without duplicating output.
- **Definition of Done:** Recovery suite passes.
- **Authority:** Documents 21,24,25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P3-16 — Implement material READY/PARTIALLY_READY/FAILED derivation

- **Workstream:** Processing
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Expose meaningful capability state.
- **Build:** Derive overall material/version state from required stage results and limitations.
- **How it works:** READY only after required source structure/chunks/index prerequisites; partial preserves limitations.
- **Dependencies:** Pipeline state
- **Tests / validation:** State matrix tests.
- **Expected result:** Frontend status accurately represents usable capability.
- **Definition of Done:** All matrix cases pass.
- **Authority:** Documents 21
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P3-17 — Build processing status and structure tree UI

- **Workstream:** Frontend
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Make ingestion transparent to students.
- **Build:** Show user-friendly stage text, progress, partial failures, collapsible detected hierarchy; poll with backoff.
- **How it works:** Never show raw pgvector/worker jargon.
- **Dependencies:** Processing APIs
- **Tests / validation:** RTL + E2E processing states incl partial/failure.
- **Expected result:** Student understands when material is usable and limitations.
- **Definition of Done:** UX tests pass.
- **Authority:** Documents 20,21
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P3-18 — Phase 3 large-material gate

- **Workstream:** Gate
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Prove 600+ page ingestion architecture works.
- **Build:** Run end-to-end large mixed PDF through extract/structure/visual/chunk/restart path.
- **How it works:** Resource use bounded; no duplicate outputs.
- **Dependencies:** All P3
- **Tests / validation:** Large fixture, restart, memory/progress validation.
- **Expected result:** Large textbook reaches pre-index structured state reliably.
- **Definition of Done:** Gate evidence recorded.
- **Authority:** Documents 21,25,26
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

# Phase 4 — Knowledge Base + RAG Foundation

**Primary goal:** Authorized hybrid retrieval returns inspectable EvidencePackages before any generative AI.

**Implementation items:** 14


## P4-01 — Create IndexGeneration/ChunkEmbedding schema

- **Workstream:** Database
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Version embedding indexes explicitly.
- **Build:** Create index_generations and chunk_embeddings with provider/model/dimension/chunking version/status and uniqueness.
- **How it works:** Generations never mix silently.
- **Dependencies:** P3 chunks
- **Tests / validation:** Migration/uniqueness/dimension metadata tests.
- **Expected result:** Embeddings are generation-scoped and rebuildable.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 18
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P4-02 — Implement embedding port

- **Workstream:** RAG
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Decouple retrieval indexing from provider.
- **Build:** Define EmbeddingPort and batch request/result contracts.
- **How it works:** Provider/model implementation selected by configuration/evaluation.
- **Dependencies:** Index schema
- **Tests / validation:** Fake adapter tests.
- **Expected result:** Chunk indexing is provider-independent.
- **Definition of Done:** Port tests pass.
- **Authority:** Documents 13,17
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P4-03 — Implement initial embedding adapter

- **Workstream:** RAG
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Produce v1 embeddings using approved candidate/provider.
- **Build:** Implement selected Gemini embedding adapter initially; capture model/dimension/usage.
- **How it works:** No provider types escape adapter.
- **Dependencies:** P5 AI/provider secrets may be reused; can implement direct adapter here
- **Tests / validation:** Contract/live smoke test with tiny quota.
- **Expected result:** Embeddings persist correctly.
- **Definition of Done:** Adapter and dimension tests pass.
- **Authority:** Documents 17
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P4-04 — Implement batched embedding jobs

- **Workstream:** RAG
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Index chunks efficiently and idempotently.
- **Build:** Create EMBED processing stage batching active chunks and upserting unique chunk+generation.
- **How it works:** Transient failures retry without duplicate vectors.
- **Dependencies:** Embedding port + jobs
- **Tests / validation:** Partial batch failure/retry tests.
- **Expected result:** All eligible chunks get one embedding per generation.
- **Definition of Done:** Idempotency tests pass.
- **Authority:** Documents 21
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P4-05 — Implement lexical search repository

- **Workstream:** RAG
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Support exact medical terms.
- **Build:** Build PostgreSQL FTS + pg_trgm search over active authorized chunks.
- **How it works:** Metadata ownership/version filters applied in SQL.
- **Dependencies:** Chunks
- **Tests / validation:** Queries for C5-T1, β1, CN VII etc.
- **Expected result:** Exact terms retrieve expected evidence.
- **Definition of Done:** Golden lexical tests pass.
- **Authority:** Documents 13,17
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P4-06 — Implement vector search repository

- **Workstream:** RAG
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Support semantic retrieval.
- **Build:** Implement pgvector similarity query constrained by allowed user/material/version scope.
- **How it works:** Authorization filters occur before ranking.
- **Dependencies:** Embeddings
- **Tests / validation:** User A/B similarity isolation test.
- **Expected result:** Semantically relevant chunks returned only from allowed scope.
- **Definition of Done:** Zero-leakage test passes.
- **Authority:** Documents 13,22
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P4-07 — Implement hybrid candidate merge

- **Workstream:** RAG
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Combine lexical and semantic strengths.
- **Build:** Merge/dedupe candidates using configurable scoring/rank fusion strategy.
- **How it works:** No LLM needed for base retrieval.
- **Dependencies:** Lexical + vector repos
- **Tests / validation:** Golden cases: exact, semantic, mixed.
- **Expected result:** Hybrid improves or matches expected retrieval.
- **Definition of Done:** Offline metrics recorded.
- **Authority:** Documents 13
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P4-08 — Implement retrieval scope builder

- **Workstream:** RAG
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Derive authorized search scope from task/topic/material links.
- **Build:** Build RetrievalScope from authenticated user, topic, active material versions/nodes and grounding mode.
- **How it works:** Client cannot widen scope.
- **Dependencies:** MaterialTopicLink + auth
- **Tests / validation:** Cross-user and inactive-version tests.
- **Expected result:** Every retrieval is scoped centrally.
- **Definition of Done:** Scope tests pass.
- **Authority:** Documents 13,22
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P4-09 — Implement EvidencePackage builder

- **Workstream:** RAG
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Return bounded, source-traceable context.
- **Build:** Create chunks, visuals, SourceReferences, quality/limitations and diagnostics payload.
- **How it works:** Prompt layer receives normalized evidence, not SQL rows.
- **Dependencies:** Hybrid retrieval
- **Tests / validation:** Unit/integration package tests.
- **Expected result:** EvidencePackage has valid sources and quality.
- **Definition of Done:** Source resolution tests pass.
- **Authority:** Documents 13,19
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P4-10 — Implement source reference resolver

- **Workstream:** RAG
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Make citations stable and authorized.
- **Build:** Create SourceReference records/resolution to page/node/chunk/visual and learner-facing labels.
- **How it works:** Reference ID alone never bypasses authorization.
- **Dependencies:** Evidence package
- **Tests / validation:** Forgery/cross-user/inactive tests.
- **Expected result:** Valid references open correct source.
- **Definition of Done:** Security tests pass.
- **Authority:** Documents 18,22
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P4-11 — Create developer retrieval inspector

- **Workstream:** RAG
- **Priority:** Should
- **Status:** Not Started
- **Goal:** Let team inspect RAG before AI.
- **Build:** Protected local/admin diagnostic endpoint/tool shows query, candidate IDs/scores/headings/quality without exposing private text broadly.
- **How it works:** Used only for development/authorized diagnostics.
- **Dependencies:** RAG services
- **Tests / validation:** Manual/golden retrieval review.
- **Expected result:** RAG can be debugged independently of LLM.
- **Definition of Done:** Inspector available locally and access-controlled.
- **Authority:** Documents 24,26
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P4-12 — Create Golden Retrieval Dataset v1

- **Workstream:** Testing
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Establish measurable retrieval quality baseline.
- **Build:** Create versioned queries with expected/acceptable sections/chunks and irrelevant negatives.
- **How it works:** Use authorized synthetic/public fixtures.
- **Dependencies:** RAG complete
- **Tests / validation:** Calculate Recall@K/Precision@K/MRR as appropriate.
- **Expected result:** Baseline quality known before AI phase.
- **Definition of Done:** Dataset committed; thresholds recorded.
- **Authority:** Documents 15,25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P4-13 — Run RAG isolation release suite

- **Workstream:** Security
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Prove zero cross-user leakage.
- **Build:** Execute semantic-identical User A/B corpora, inactive/deleted material and forged scope tests.
- **How it works:** Scope filtering must happen before ranking.
- **Dependencies:** RAG complete
- **Tests / validation:** Automated security suite.
- **Expected result:** 0 unauthorized chunks ever returned.
- **Definition of Done:** Gate passes.
- **Authority:** Documents 22,25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P4-14 — Phase 4 retrieval gate

- **Workstream:** Gate
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Prove source-grounded knowledge layer works without AI.
- **Build:** Run representative queries across anatomy/physiology exact/semantic/visual contexts and inspect EvidencePackages.
- **How it works:** No generative model involved.
- **Dependencies:** All P4
- **Tests / validation:** Golden metrics + source resolution.
- **Expected result:** Hippocampus can reliably find correct authorized evidence.
- **Definition of Done:** Gate evidence recorded.
- **Authority:** Documents 26
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

# Phase 5 — AI Provider & Prompt Infrastructure

**Primary goal:** Gemini and Ollama execute the same typed tasks behind provider-independent routing and validation.

**Milestone:** M2 — Grounded Intelligence

**Implementation items:** 14


## P5-01 — Define canonical AiTask contracts

- **Workstream:** AI
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Standardize AI tasks before providers.
- **Build:** Create typed AiTaskRequest/ValidatedAiResult and task enums/contracts for explanation, question, evaluation, connection, application, repair.
- **How it works:** Canonical contract contains grounding/evidence/output schema.
- **Dependencies:** P4 EvidencePackage
- **Tests / validation:** Serialization/unit tests.
- **Expected result:** All AI use cases speak one internal language.
- **Definition of Done:** Contracts stable and tested.
- **Authority:** Documents 10,12,19
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P5-02 — Implement PromptTemplateRegistry

- **Workstream:** AI
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Centralize versioned prompts.
- **Build:** Create registry for system + task templates with prompt ID/version and authority hierarchy.
- **How it works:** Feature code cannot embed arbitrary production prompts.
- **Dependencies:** AI contracts
- **Tests / validation:** Registry lookup/version tests.
- **Expected result:** Prompts are traceable and centrally managed.
- **Definition of Done:** No production prompt strings outside registry.
- **Authority:** Documents 12
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P5-03 — Implement PromptContextBuilder

- **Workstream:** AI
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Control tokens and untrusted boundaries.
- **Build:** Build minimal learner/activity/evidence context with explicit SOURCE_CONTEXT and STUDENT_RESPONSE delimiters and output budget.
- **How it works:** Only task-relevant context is included.
- **Dependencies:** Prompt registry + RAG
- **Tests / validation:** Snapshot/token-budget/prompt-injection fixture tests.
- **Expected result:** Prompts stay bounded and preserve instruction hierarchy.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 12,22
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P5-04 — Implement ProviderRouter

- **Workstream:** AI
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Select provider centrally.
- **Build:** Create routing policy using task support, evaluation status, availability, quota/rate state, cost/latency config.
- **How it works:** Learning/domain code never picks Gemini/Ollama.
- **Dependencies:** AI contracts
- **Tests / validation:** Unit route matrix tests.
- **Expected result:** Correct route/fallback selected deterministically.
- **Definition of Done:** Routing tests pass.
- **Authority:** Documents 10,19
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P5-05 — Implement GeminiProviderAdapter

- **Workstream:** AI
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Connect Gemini server-side.
- **Build:** Use Spring AI Google GenAI integration; map canonical request, multimodal input where approved, structured outputs/errors/usage.
- **How it works:** Gemini SDK types remain internal.
- **Dependencies:** Provider router + secrets
- **Tests / validation:** Mock/contract + tiny live smoke test.
- **Expected result:** Gemini executes supported typed tasks.
- **Definition of Done:** Contract tests pass.
- **Authority:** Documents 17,19
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P5-06 — Implement OllamaCloudProviderAdapter

- **Workstream:** AI
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Connect remote Ollama API.
- **Build:** Use server-side HTTPS bearer auth and map canonical request/stream/errors.
- **How it works:** No local Ollama assumption; provider-specific details stay internal.
- **Dependencies:** Provider router + secrets
- **Tests / validation:** Mock HTTP/contract + tiny live smoke.
- **Expected result:** Ollama API executes supported typed tasks.
- **Definition of Done:** Contract tests pass.
- **Authority:** Documents 10,17,19
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P5-07 — Implement AI Request Manager

- **Workstream:** AI
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Bound concurrency and provider pressure.
- **Build:** Add per-provider concurrency gates, queue, timeouts, Retry-After/backoff, cancellation and circuit-breaker-like state.
- **How it works:** Interactive tasks get higher priority than background AI.
- **Dependencies:** Provider adapters
- **Tests / validation:** Concurrency/rate-limit/timeout tests.
- **Expected result:** Provider overload degrades predictably without flooding.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 10,19,24
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P5-08 — Implement output schema validation

- **Workstream:** AI
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Keep provider output untrusted.
- **Build:** Parse structured output, validate enums/business constraints and required fields before returning ValidatedAiResult.
- **How it works:** Invalid output cannot mutate learning state.
- **Dependencies:** AI contracts
- **Tests / validation:** Malformed/missing/extra-field tests.
- **Expected result:** Only valid typed results cross AI boundary.
- **Definition of Done:** Validation suite passes.
- **Authority:** Documents 10,12,22
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P5-09 — Implement source-reference validation

- **Workstream:** AI
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Prevent fabricated citations.
- **Build:** Validate provider-returned source IDs are subset of supplied EvidencePackage and still authorized/active.
- **How it works:** Forgery causes AI_GROUNDING_FAILURE or repair path.
- **Dependencies:** Output validation + RAG
- **Tests / validation:** Fabricated/cross-user/inactive reference tests.
- **Expected result:** AI cannot invent accepted citations.
- **Definition of Done:** Security tests pass.
- **Authority:** Documents 22,25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P5-10 — Implement bounded repair/retry

- **Workstream:** AI
- **Priority:** Should
- **Status:** Not Started
- **Goal:** Recover format errors without loops.
- **Build:** Allow one/few configured structured-output repair attempts using same task/evidence/policy.
- **How it works:** Do not retry safety/authorization failures as availability errors.
- **Dependencies:** Validation
- **Tests / validation:** Repair success/failure tests.
- **Expected result:** Malformed provider output either safely repairs or fails transparently.
- **Definition of Done:** Bounded behavior verified.
- **Authority:** Documents 12,19
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P5-11 — Implement fallback orchestration

- **Workstream:** AI
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Preserve contract across providers.
- **Build:** On eligible provider availability failure, route same canonical task/evidence/grounding/schema to approved fallback.
- **How it works:** Fallback cannot broaden context or weaken policy.
- **Dependencies:** Router/adapters
- **Tests / validation:** Primary timeout/rate-limit/fallback tests.
- **Expected result:** Fallback works invisibly when contract-compatible.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 10,22
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P5-12 — Persist AI request/usage diagnostics

- **Workstream:** Observability
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Measure quality/cost without content leakage.
- **Build:** Write ai_request_records/provider_usage_records with provider/model/task/prompt/tokens/latency/status/fallback.
- **How it works:** Do not persist full prompts by default.
- **Dependencies:** AI execution
- **Tests / validation:** DB/ privacy tests.
- **Expected result:** Operations can compare providers safely.
- **Definition of Done:** Telemetry verified.
- **Authority:** Documents 18,24
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P5-13 — Create provider contract and prompt regression suite

- **Workstream:** Testing
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Make AI integration stable.
- **Build:** Shared tests run against fake provider adapters; small scheduled live smoke set; prompt snapshots/golden schema cases.
- **How it works:** PR CI avoids unnecessary quota.
- **Dependencies:** All AI infra
- **Tests / validation:** CI/scheduled suite.
- **Expected result:** Provider changes do not silently break contracts.
- **Definition of Done:** Suites documented and green.
- **Authority:** Documents 25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P5-14 — Phase 5 AI infrastructure gate

- **Workstream:** Gate
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Prove provider abstraction works independently of Learning Engine.
- **Build:** Execute same supported task via Gemini and Ollama; simulate primary failure and verify fallback/validation/telemetry.
- **How it works:** RAG evidence remains identical.
- **Dependencies:** All P5
- **Tests / validation:** Contract + live smoke + fallback.
- **Expected result:** Provider can change without domain rewrite.
- **Definition of Done:** Gate evidence recorded.
- **Authority:** Documents 26
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

# Phase 6 — AI Learning Engine

**Primary goal:** Deterministic Learning Engine selects appropriate next pedagogical action without LLM authority.

**Implementation items:** 14


## P6-01 — Define LearningState model

- **Workstream:** Domain
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Represent only evidence needed for pedagogical decisions.
- **Build:** Create immutable domain view of mission state, evidence dimensions, source capability, time context and history.
- **How it works:** No provider DTOs or UI state inside.
- **Dependencies:** P1-P5 domain foundations
- **Tests / validation:** Pure unit tests.
- **Expected result:** Learning Engine receives deterministic input.
- **Definition of Done:** Model stable and provider-free.
- **Authority:** Documents 11,19
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P6-02 — Define NextLearningAction model

- **Workstream:** Domain
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Make engine outputs explicit.
- **Build:** Create typed actions: UNDERSTAND, RETRIEVE, CONNECT, APPLY, FEEDBACK/REFLECT/COMPLETE as approved with objective/difficulty/rationale/AI requirement.
- **How it works:** Application layer interprets action.
- **Dependencies:** LearningState
- **Tests / validation:** Unit serialization/equality tests.
- **Expected result:** No raw prompt text returned by engine.
- **Definition of Done:** Action model tested.
- **Authority:** Documents 11,19
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P6-03 — Implement mission state machine policy

- **Workstream:** Domain
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Protect valid progression lifecycle.
- **Build:** Implement planned/active/paused/completed/stopped transitions and validation.
- **How it works:** Completion does not imply mastery.
- **Dependencies:** Learning models
- **Tests / validation:** All valid/invalid transition unit tests.
- **Expected result:** Illegal transitions rejected deterministically.
- **Definition of Done:** State suite passes.
- **Authority:** Documents 11,18
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P6-04 — Implement understand→retrieve policy

- **Workstream:** Domain
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Move from explanation to active recall appropriately.
- **Build:** Define conditions for explanation then retrieval, respecting time/source capability and existing evidence.
- **How it works:** Engine chooses action, AI only generates content later.
- **Dependencies:** LearningState
- **Tests / validation:** Table-driven policy tests.
- **Expected result:** Weak/insufficient knowledge gets appropriate understanding/retrieval.
- **Definition of Done:** Cases pass.
- **Authority:** Documents 03,11
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P6-05 — Implement connection policy

- **Workstream:** Domain
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Require relational learning when appropriate.
- **Build:** Select CONNECT activities after basic understanding/retrieval when concept relationships matter.
- **How it works:** Uses evidence not learning-style labels.
- **Dependencies:** Evidence state
- **Tests / validation:** Scenario tests.
- **Expected result:** Connections appear at appropriate stage.
- **Definition of Done:** Policy tests pass.
- **Authority:** Documents 03,11
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P6-06 — Implement application/scenario policy

- **Workstream:** Domain
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Introduce practical medical application with scaffolding.
- **Build:** Choose APPLY when prerequisites are adequate; reduce difficulty or return to UNDERSTAND/RETRIEVE after weak performance.
- **How it works:** Early students get educational scenarios, not clinical autonomy.
- **Dependencies:** Evidence + objectives
- **Tests / validation:** Weak/strong application cases.
- **Expected result:** Appropriate application practice selected.
- **Definition of Done:** Policy suite passes.
- **Authority:** Documents 03,11
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P6-07 — Implement time-aware mission policy

- **Workstream:** Domain
- **Priority:** Should
- **Status:** Not Started
- **Goal:** Fit learning sequence to available study time.
- **Build:** Use available_time and activity estimates to select bounded next actions/complete gracefully.
- **How it works:** Timer does not equal mastery.
- **Dependencies:** Mission context
- **Tests / validation:** 5/15/30-minute cases.
- **Expected result:** Mission remains coherent within time limit.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 06,11
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P6-08 — Implement scaffolding policy

- **Workstream:** Domain
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Respond to difficulty without giving answers immediately.
- **Build:** Define retry → hint → simpler explanation/prerequisite → reattempt behavior.
- **How it works:** Scaffolding is deterministic; wording may use AI.
- **Dependencies:** Attempt history
- **Tests / validation:** Table-driven incorrect/partial cases.
- **Expected result:** Student receives progressively useful support.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 03,11
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P6-09 — Implement anti-repetition policy

- **Workstream:** Domain
- **Priority:** Should
- **Status:** Not Started
- **Goal:** Avoid mechanically repeating same question/activity.
- **Build:** Track recent activity/concept/template history and exclude unnecessary repeats while allowing deliberate spaced review.
- **How it works:** No random repetition by provider.
- **Dependencies:** History
- **Tests / validation:** Repeat-history unit tests.
- **Expected result:** Learning variety remains purposeful.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 11
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P6-10 — Implement source-capability policy

- **Workstream:** Domain
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Never request unsupported activity types.
- **Build:** Use material capabilities/quality to avoid visual/source-strict tasks when unavailable or poor.
- **How it works:** RAG/ingestion limitations feed engine.
- **Dependencies:** Material capability
- **Tests / validation:** No-visual/limited-OCR/no-evidence cases.
- **Expected result:** Engine picks safe alternative or signals limitation.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 11,21
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P6-11 — Implement AI/RAG failure policy

- **Workstream:** Domain
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Define pedagogical fallback when dependencies fail.
- **Build:** Map RAG LIMITED/INSUFFICIENT/FAILED and AI unavailable to reuse/source-only/retry/pause actions where appropriate.
- **How it works:** Failure is explicit, never hidden as empty context.
- **Dependencies:** RAG/AI result types
- **Tests / validation:** Failure matrix tests.
- **Expected result:** Mission degrades safely.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 11,19
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P6-12 — Create Learning Engine scenario suite

- **Workstream:** Testing
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Prove pedagogy deterministically.
- **Build:** Build table-driven scenarios across evidence dimensions, time, misconceptions, source quality and history.
- **How it works:** No live AI needed.
- **Dependencies:** Engine policies
- **Tests / validation:** Hundreds of deterministic cases where practical.
- **Expected result:** Changes reveal pedagogical regressions.
- **Definition of Done:** Suite green and versioned.
- **Authority:** Documents 11,25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P6-13 — Verify Learning Engine provider independence

- **Workstream:** Architecture
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Prevent AI authority drift.
- **Build:** ArchUnit rules block learning.domain dependencies on ai.infrastructure, Spring MVC/JPA/provider SDKs.
- **How it works:** Engine may depend only on domain/ports/value types.
- **Dependencies:** Architecture tests
- **Tests / validation:** Intentional violation check.
- **Expected result:** Learning Engine remains pure/testable.
- **Definition of Done:** CI rule active.
- **Authority:** Documents 19,25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P6-14 — Phase 6 Learning Engine gate

- **Workstream:** Gate
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Prove Hippocampus can decide next action without LLM.
- **Build:** Feed representative student states and review expected actions/rationales.
- **How it works:** No provider calls occur.
- **Dependencies:** All P6
- **Tests / validation:** Deterministic scenario suite.
- **Expected result:** Pedagogical sequencing is application-owned.
- **Definition of Done:** Gate evidence recorded.
- **Authority:** Documents 26
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

# Phase 7 — Study Missions

**Primary goal:** Student completes a full source-grounded Study Mission from explanation through application/feedback.

**Milestone:** M3 — First Hippocampus

**Implementation items:** 14


## P7-01 — Create StudyMission/LearningObjective/LearningActivity schema

- **Workstream:** Database
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Persist complete mission plan and current activity.
- **Build:** Implement tables/entities/repos and mission_materials/source links as Doc 18.
- **How it works:** Mission freezes material versions.
- **Dependencies:** P2-P6
- **Tests / validation:** Migration/FK/version tests.
- **Expected result:** Mission can be resumed reproducibly.
- **Definition of Done:** Schema tests pass.
- **Authority:** Documents 18
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P7-02 — Create StudentAttempt schema

- **Workstream:** Database
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Keep immutable attempt history.
- **Build:** Persist attempt_number, response payload/text, evaluation status/artifact references.
- **How it works:** Retries create new rows, never overwrite prior attempt.
- **Dependencies:** Activities
- **Tests / validation:** Uniqueness/ordering tests.
- **Expected result:** Attempt history is traceable.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 18
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P7-03 — Implement StartStudyMissionUseCase

- **Workstream:** Application
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Create bounded mission from topic, time and sources.
- **Build:** Validate ownership/readiness, freeze allowed material versions, derive objectives/initial state and first LearningEngine action.
- **How it works:** RAG/AI called only if first action needs content.
- **Dependencies:** Mission schema + engine
- **Tests / validation:** Use-case tests with ready/partial/no-source states.
- **Expected result:** Starting a mission yields first valid activity or clear limitation.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 19
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P7-04 — Implement activity materialization

- **Workstream:** Application
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Turn NextLearningAction into concrete activity.
- **Build:** For deterministic actions create directly; for AI tasks retrieve EvidencePackage, execute typed task, validate, persist artifact/activity/source refs.
- **How it works:** Engine decides kind; AI creates bounded content.
- **Dependencies:** RAG + AI + mission
- **Tests / validation:** Integration tests by activity type.
- **Expected result:** Activity content is grounded and typed.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 11,19
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P7-05 — Implement SubmitActivityResponseUseCase

- **Workstream:** Application
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Process student answer safely.
- **Build:** Persist/prepare attempt, evaluate deterministically or via AI, validate result, generate evidence event later, ask engine for next action, persist state in short transactions.
- **How it works:** No DB transaction held across long provider call.
- **Dependencies:** Activities + AI
- **Tests / validation:** Correct/partial/invalid/timeout/stale-state tests.
- **Expected result:** Answer leads to validated feedback and next action without duplicate state.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 19
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P7-06 — Implement pause/resume/stop mission

- **Workstream:** Application
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Preserve continuity.
- **Build:** Add use cases and endpoints maintaining current activity/time/material versions.
- **How it works:** Resume does not regenerate completed setup unnecessarily.
- **Dependencies:** Mission state
- **Tests / validation:** State transition + restart tests.
- **Expected result:** Student can leave and return safely.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 06,18
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P7-07 — Implement explanation task end-to-end

- **Workstream:** AI
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Support Understand activity.
- **Build:** Use EvidencePackage + objective + learner context → structured explanation/key points/source refs.
- **How it works:** Supplemental knowledge classified explicitly.
- **Dependencies:** AI infra + RAG
- **Tests / validation:** Golden explanation cases.
- **Expected result:** Explanation is source-grounded and learner-appropriate.
- **Definition of Done:** Tests/eval pass.
- **Authority:** Documents 07,12,15
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P7-08 — Implement retrieval-question task end-to-end

- **Workstream:** AI
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Support active retrieval activity.
- **Build:** Generate structured short-answer/MCQ/identification question with expected evidence, no answer leakage.
- **How it works:** Backend controls activity type/difficulty.
- **Dependencies:** AI infra
- **Tests / validation:** Question quality/golden cases.
- **Expected result:** Question tests target intended concept and sources.
- **Definition of Done:** Eval pass.
- **Authority:** Documents 03,07
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P7-09 — Implement response-evaluation task end-to-end

- **Workstream:** AI
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Provide formative feedback without false scoring.
- **Build:** Evaluate student response against expected evidence, return correctness dimensions/missing reasoning/misconception candidate/feedback contract.
- **How it works:** Learning Engine/progress later interprets; AI doesn't set mastery.
- **Dependencies:** AI infra
- **Tests / validation:** Correct/partial/alternative/wrong reasoning tests.
- **Expected result:** Evaluation is nuanced and structured.
- **Definition of Done:** Golden eval pass.
- **Authority:** Documents 11,15
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P7-10 — Implement concept-connection task

- **Workstream:** AI
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Generate bounded relationship activity.
- **Build:** Create connection explanation/question linking relevant concepts using evidence.
- **How it works:** Engine decides when connection is needed.
- **Dependencies:** AI infra
- **Tests / validation:** Golden connection cases.
- **Expected result:** Connections are medically relevant and source-grounded.
- **Definition of Done:** Eval pass.
- **Authority:** Documents 03,11
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P7-11 — Implement application-scenario task

- **Workstream:** AI
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Generate practical scaffolded medical scenario.
- **Build:** Create age-appropriate scenario focused on target concept with expected reasoning and safety/educational framing.
- **How it works:** Not patient-specific advice.
- **Dependencies:** AI infra
- **Tests / validation:** Scenario plausibility/difficulty/support tests.
- **Expected result:** Student can apply foundational knowledge meaningfully.
- **Definition of Done:** Eval pass.
- **Authority:** Documents 03,07,15
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P7-12 — Build Study Mission route and ActivityRenderer

- **Workstream:** Frontend
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Create core product experience.
- **Build:** Implement /missions/:id, typed union renderer for explanation/retrieval/connection/application/feedback/reflection, source panel and actions.
- **How it works:** Frontend renders backend contract; never parses raw provider semantics.
- **Dependencies:** Mission APIs
- **Tests / validation:** RTL for each activity + route E2E.
- **Expected result:** One coherent guided flow replaces feature menu.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 20
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P7-13 — Implement mission submit/resume/conflict UX

- **Workstream:** Frontend
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Make mission reliable in real browser use.
- **Build:** Build useStudyMission hook, submit states, stale 409 refetch, pause/resume, timer display and source drawer.
- **How it works:** Critical state changes wait for backend confirmation.
- **Dependencies:** Mission APIs
- **Tests / validation:** Two-tab conflict E2E; refresh/resume tests.
- **Expected result:** Mission survives refresh and rejects double submission.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 20
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P7-14 — Phase 7 First Hippocampus gate

- **Workstream:** Gate
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Prove full source-grounded Study Mission.
- **Build:** From topic + processed material: start mission → understand → retrieve → connect → apply → feedback → complete/pause.
- **How it works:** Sources visible and provider abstracted.
- **Dependencies:** All P7
- **Tests / validation:** Critical Playwright E2E + source validation + AI golden checks.
- **Expected result:** M3 First Hippocampus works end-to-end.
- **Definition of Done:** Milestone evidence recorded.
- **Authority:** Documents 26
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

# Phase 8 — Learning Evidence + Review System

**Primary goal:** Attempts become traceable evidence and drive future review/learning across sessions.

**Milestone:** M4 — Adaptive Hippocampus

**Implementation items:** 12


## P8-01 — Create EvidenceEvent/LearningEvidence schema

- **Workstream:** Database
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Persist traceable longitudinal learning evidence.
- **Build:** Implement evidence_events and learning_evidence summary tables by user/topic/concept/dimension/state.
- **How it works:** Summary is recomputable from events.
- **Dependencies:** P7 attempts
- **Tests / validation:** Migration/FK tests.
- **Expected result:** Evidence has event provenance.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 18
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P8-02 — Implement EvidenceProjector

- **Workstream:** Domain
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Convert validated attempts to broad evidence states.
- **Build:** Deterministically aggregate events into STRONG/DEVELOPING/WEAK/INSUFFICIENT per dimension using approved rules.
- **How it works:** No LLM sets final state.
- **Dependencies:** Evidence schema
- **Tests / validation:** Table-driven projection tests.
- **Expected result:** Same events always yield same evidence.
- **Definition of Done:** Suite passes.
- **Authority:** Documents 11,18
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P8-03 — Integrate attempt→evidence transaction

- **Workstream:** Application
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Make validated activity affect future learning.
- **Build:** After validated evaluation, create EvidenceEvent and update projection in short transaction with mission state.
- **How it works:** Invalid AI evaluation produces no evidence.
- **Dependencies:** Submit use case + projector
- **Tests / validation:** Rollback/invalid-eval tests.
- **Expected result:** Evidence updates only from legitimate activity.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 18,19
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P8-04 — Create misconception_evidence/reflection_evidence

- **Workstream:** Database
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Track weak conceptual patterns without permanent labels.
- **Build:** Implement schemas and links to supporting events/mission.
- **How it works:** Single ambiguous response defaults POSSIBLE.
- **Dependencies:** Evidence events
- **Tests / validation:** Promotion/resolution FK tests.
- **Expected result:** Misconceptions remain traceable/contextual.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 18
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P8-05 — Implement misconception policy

- **Workstream:** Domain
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Promote/resolve misconception based on repeated evidence.
- **Build:** Define POSSIBLE→ACTIVE→RESOLVED logic and evidence thresholds/rationale codes.
- **How it works:** AI may suggest candidate description, app owns status.
- **Dependencies:** Evidence projector
- **Tests / validation:** Repeated-error/corrective cases.
- **Expected result:** No permanent label from one mistake.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 11,18
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P8-06 — Create ReviewRecord and evidence links

- **Workstream:** Database
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Persist explainable review queue.
- **Build:** Implement review_records/review_evidence_links and optional mission linkage.
- **How it works:** Review reason always traceable to evidence.
- **Dependencies:** Evidence
- **Tests / validation:** Migration/query tests.
- **Expected result:** Due review items have evidence basis.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 18
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P8-07 — Implement ReviewPolicy

- **Workstream:** Domain
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Determine what/why/when to revisit.
- **Build:** Use LearningEvidence + events + review history + time rules to create/update eligible_at/priority/reason.
- **How it works:** No LLM chooses date.
- **Dependencies:** Review schema
- **Tests / validation:** Fixed-clock policy tests.
- **Expected result:** Weak/decaying evidence creates appropriate review.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 03,11,18
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P8-08 — Implement review queue/use cases

- **Workstream:** Application
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Expose due reviews and start review mission.
- **Build:** List available reviews by priority/reason; start a normal StudyMission linked to review record; complete updates evidence/review state.
- **How it works:** Review uses same engine and source grounding.
- **Dependencies:** Review policy + missions
- **Tests / validation:** Use-case/integration tests.
- **Expected result:** Review becomes part of learning loop, not separate quiz tool.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 06,19
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P8-09 — Build Review route

- **Workstream:** Frontend
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Show what to revisit and why.
- **Build:** Display topic/concept, reason, priority/availability and CTA to start review mission.
- **How it works:** Frontend never calculates schedule.
- **Dependencies:** Review API
- **Tests / validation:** RTL/E2E due/no-due/completed.
- **Expected result:** Student understands reason for review.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 20
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P8-10 — Build Progress evidence UI

- **Workstream:** Frontend
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Show multidimensional evidence without fake percentages.
- **Build:** Render Retrieval/Understanding/Connection/Application/Review Retention as broad states with recent context.
- **How it works:** No mastery percentage unless future evidence supports it.
- **Dependencies:** Progress API
- **Tests / validation:** Component/accessibility tests.
- **Expected result:** Progress is clear and non-misleading.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 20
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P8-11 — Create longitudinal learning regression scenarios

- **Workstream:** Testing
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Prove previous evidence affects future missions.
- **Build:** Simulate mission attempts → evidence → review → later mission and assert engine action changes.
- **How it works:** Use fixed clock and fake AI.
- **Dependencies:** All P8
- **Tests / validation:** End-to-end domain/integration scenarios.
- **Expected result:** Hippocampus adapts across sessions.
- **Definition of Done:** Suite green.
- **Authority:** Documents 25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P8-12 — Phase 8 Adaptive Hippocampus gate

- **Workstream:** Gate
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Prove longitudinal loop.
- **Build:** Complete weak application attempt, observe evidence/review, run corrective review, verify later LearningEngine context/action changes.
- **How it works:** All evidence traceable.
- **Dependencies:** All P8
- **Tests / validation:** Integrated scenario + UI E2E.
- **Expected result:** M4 Adaptive Hippocampus works.
- **Definition of Done:** Milestone evidence recorded.
- **Authority:** Documents 26
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

# Phase 9 — Visual & Multimodal Learning

**Primary goal:** Source visuals actively participate in retrieval, Study Missions and evidence.

**Implementation items:** 10


## P9-01 — Add visual-aware retrieval

- **Workstream:** RAG
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Allow source images to participate in EvidencePackage.
- **Build:** Retrieve linked VisualAssets using chunk/node/caption/nearby-text relevance and task need.
- **How it works:** Text grounding still controls source scope.
- **Dependencies:** P3 visuals + P4 RAG
- **Tests / validation:** Golden anatomy visual queries.
- **Expected result:** Relevant source figure accompanies evidence.
- **Definition of Done:** Visual retrieval tests pass.
- **Authority:** Documents 13,14
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P9-02 — Enable validated multimodal provider route

- **Workstream:** AI
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Use image understanding only where evaluation proves capability.
- **Build:** Add visual task support matrix to ProviderRouter; pass approved source image + bounded context to capable model.
- **How it works:** Fallback only to provider supporting same multimodal contract.
- **Dependencies:** P5 providers
- **Tests / validation:** Provider capability/unsupported fallback tests.
- **Expected result:** Visual task routes safely.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 10,15
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P9-03 — Implement visual activity task contract

- **Workstream:** AI
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Generate/assess image-based learning activity.
- **Build:** Create typed visual-identification/interpretation request/result with source visual reference and expected concept.
- **How it works:** Original image remains authoritative asset.
- **Dependencies:** Visual RAG + multimodal provider
- **Tests / validation:** Golden anatomy/image cases.
- **Expected result:** Activity refers to actual source visual without fabricated labels.
- **Definition of Done:** Eval pass.
- **Authority:** Documents 07,12
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P9-04 — Build medical image viewer

- **Workstream:** Frontend
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Make source visuals usable for study.
- **Build:** Add zoom, pan, accessible lightbox/drawer, caption/source controls, responsive behavior.
- **How it works:** No AI-generated replacement needed for source display.
- **Dependencies:** Visual assets
- **Tests / validation:** Keyboard/touch/accessibility tests.
- **Expected result:** Student can inspect figures on desktop/mobile.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 20
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P9-05 — Build VisualActivity component

- **Workstream:** Frontend
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Integrate visuals into Study Missions.
- **Build:** Render original source visual, prompt, response controls, feedback and citation.
- **How it works:** Activity remains within mission flow, not separate tool.
- **Dependencies:** Visual task API
- **Tests / validation:** RTL/E2E visual mission.
- **Expected result:** Image becomes active learning evidence.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 20
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P9-06 — Add VISUAL_IDENTIFICATION evidence dimension path

- **Workstream:** Progress
- **Priority:** Should
- **Status:** Not Started
- **Goal:** Track visual task performance appropriately.
- **Build:** Generate EvidenceEvents for validated visual activities and project dimension separately.
- **How it works:** No visual-learner profiling.
- **Dependencies:** Visual activity + evidence
- **Tests / validation:** Projection tests.
- **Expected result:** Visual evidence influences relevant future activity.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 18
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P9-07 — Handle visual-unavailable/limited capability

- **Workstream:** Ingestion
- **Priority:** 21
- **Status:** Not Started
- **Goal:** Prevent invalid visual activities.
- **Build:** Expose material_version capability metadata for visuals and failed extraction; engine respects it.
- **How it works:** P3 states + P6 source policy
- **Dependencies:** No-visual/failed-image cases.
- **Tests / validation:** Mission avoids visual task when source cannot support it.
- **Expected result:** Tests pass.
- **Definition of Done:** Must
- **Authority:** Documents 
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P9-08 — Create anatomy visual golden fixture set

- **Workstream:** Testing
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Establish reliable multimodal validation.
- **Build:** Use licensed/synthetic anatomy diagrams with known source labels/relationships and expected retrieval/task outputs.
- **How it works:** No real patient data.
- **Dependencies:** Visual pipeline
- **Tests / validation:** Retrieval + AI + frontend visual tests.
- **Expected result:** Visual quality is measurable.
- **Definition of Done:** Fixtures committed and passing.
- **Authority:** Documents 25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P9-09 — Test visual/source authorization

- **Workstream:** Security
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Prevent image leakage.
- **Build:** Attempt direct visual/source-reference access across users and expired signed URLs if used.
- **How it works:** Authorization rechecked on every source access.
- **Dependencies:** Visual endpoints
- **Tests / validation:** IDOR/signed URL tests.
- **Expected result:** 0 cross-user image access.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 22,25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P9-10 — Phase 9 multimodal gate

- **Workstream:** Gate
- **Priority:** 26
- **Status:** Not Started
- **Goal:** Prove anatomy-style visual learning end-to-end.
- **Build:** Upload mixed PDF → extract image → retrieve visual → Study Mission identification → feedback/evidence.
- **How it works:** All P9
- **Dependencies:** Full E2E and golden evaluation.
- **Tests / validation:** Visuals function as learning evidence, not decoration.
- **Expected result:** Gate evidence recorded.
- **Definition of Done:** Must
- **Authority:** Documents 
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

# Phase 10 — Complete Student Learning Experience

**Primary goal:** All core capabilities form one accessible, responsive, guided student experience.

**Implementation items:** 10


## P10-01 — Complete Home dashboard

- **Workstream:** Frontend
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Answer what should I study now.
- **Build:** Implement continue mission, due reviews, recent topics/materials, quick start with compact hierarchy.
- **How it works:** Avoid analytics overload.
- **Dependencies:** P7-P8 APIs
- **Tests / validation:** E2E empty/active/review states.
- **Expected result:** Home leads student into next useful action.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 20
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P10-02 — Complete Topic workspace

- **Workstream:** Frontend
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Unify material/evidence/mission entry.
- **Build:** Show topic, relevant material sections, recent evidence, review state, Start/Resume Mission.
- **How it works:** No feature-tool menu.
- **Dependencies:** P2/P7/P8
- **Tests / validation:** Component/E2E.
- **Expected result:** Topic screen is clear learning launch point.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 20
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P10-03 — Complete Material detail/source browser

- **Workstream:** Frontend
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Let student inspect detected structure and source limitations.
- **Build:** Add hierarchy tree, processing capabilities, page/source opening, topic mapping/selection.
- **How it works:** Large hierarchy lazy-expands.
- **Dependencies:** P3/P4
- **Tests / validation:** Large tree performance + E2E.
- **Expected result:** Student can understand what material was processed.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 20
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P10-04 — Complete mission completion summary

- **Workstream:** Frontend
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Close loop without fake mastery.
- **Build:** Show practiced objectives, broad evidence, weak areas, review expectation and next actions.
- **How it works:** Completion is not mastery.
- **Dependencies:** P8
- **Tests / validation:** Component/content tests.
- **Expected result:** Student gets meaningful recap.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 20
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P10-05 — Implement responsive/tablet/mobile polish

- **Workstream:** Frontend
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Ensure med-student usability beyond desktop.
- **Build:** Reflow nav/source panel, readable widths, touch targets, image viewer and forms at 320px+.
- **How it works:** No page-level horizontal overflow.
- **Dependencies:** All UI
- **Tests / validation:** Viewport matrix Playwright tests.
- **Expected result:** Core flows usable on laptop/tablet/mobile.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 20
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P10-06 — Run WCAG 2.2 AA core-flow remediation

- **Workstream:** Accessibility
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Make core experience keyboard/screen-reader usable.
- **Build:** Fix focus, labels, contrast, live streaming announcements, dialog/drawer behavior, image controls.
- **How it works:** Streaming must not announce every token.
- **Dependencies:** All frontend
- **Tests / validation:** Automated axe-like checks + manual keyboard/screen-reader checklist.
- **Expected result:** Core journeys are accessibility-aligned.
- **Definition of Done:** Critical issues resolved.
- **Authority:** Documents 20,25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P10-07 — Implement cold-start/connectivity UX

- **Workstream:** Frontend
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Handle free backend reality gracefully.
- **Build:** Detect initial backend delay/network loss; show Connecting; safe GET retries; preserve unsent draft locally where appropriate.
- **How it works:** Do not auto-retry non-idempotent submissions without protection.
- **Dependencies:** P23 deployment
- **Tests / validation:** Render cold-start simulation/E2E.
- **Expected result:** Free-tier sleep doesn't look like data loss.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 20,23
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P10-08 — Standardize error/empty/loading states

- **Workstream:** Frontend
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Finish coherent product behavior.
- **Build:** Map stable backend error codes to actionable messages for material, AI, retrieval, conflicts, auth.
- **How it works:** Unknown errors show correlation ID.
- **Dependencies:** Backend error catalog
- **Tests / validation:** Component/E2E state matrix.
- **Expected result:** Failures are understandable and recoverable.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 19,20,24
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P10-09 — Frontend performance pass

- **Workstream:** Performance
- **Priority:** Should
- **Status:** Not Started
- **Goal:** Keep core experience responsive.
- **Build:** Route code-splitting, lazy PDF/image viewer, avoid over-fetching, virtualize large structure trees where needed.
- **How it works:** Measure realistic devices.
- **Dependencies:** Complete UI
- **Tests / validation:** Build analysis + performance smoke tests.
- **Expected result:** No obvious large-document UI freeze.
- **Definition of Done:** Performance notes recorded.
- **Authority:** Documents 20
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P10-10 — Phase 10 complete UX gate

- **Workstream:** Gate
- **Priority:** 26
- **Status:** Not Started
- **Goal:** Prove full user journey feels like one product.
- **Build:** Login → home → subject/topic → material → mission → review/progress on desktop/mobile.
- **How it works:** All P10
- **Dependencies:** Full Playwright journey/accessibility review.
- **Tests / validation:** Hippocampus feels coherent, not a feature collection.
- **Expected result:** Gate evidence recorded.
- **Definition of Done:** Must
- **Authority:** Documents 
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

# Phase 11 — Security + Reliability Hardening

**Primary goal:** System withstands security/adversarial/dependency/provider/infrastructure failure tests.

**Implementation items:** 12


## P11-01 — Run full SAST baseline

- **Workstream:** Security
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Detect code-level security issues.
- **Build:** Run chosen CodeQL/FindSecBugs/Semgrep combination on backend/frontend and triage findings.
- **How it works:** False positives documented narrowly.
- **Dependencies:** Code complete
- **Tests / validation:** Security report review.
- **Expected result:** No unresolved exploitable Critical findings.
- **Definition of Done:** Report attached.
- **Authority:** Documents 25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P11-02 — Run SCA/dependency audit

- **Workstream:** Security
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Detect vulnerable Maven/npm dependencies.
- **Build:** Run Dependabot/OWASP dependency checks/npm audit supplemental review; patch or document findings.
- **How it works:** Versions follow Doc 17 policy.
- **Dependencies:** Dependencies
- **Tests / validation:** Report + upgrade regression.
- **Expected result:** No release-blocking dependency vulnerabilities.
- **Definition of Done:** Gate satisfied.
- **Authority:** Documents 25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P11-03 — Run secret exposure audit

- **Workstream:** Security
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Prove keys are absent from repo/build/logs.
- **Build:** Run Gitleaks/GitHub scanning and inspect frontend build artifacts/log samples.
- **How it works:** Any exposed key is revoked/rotated.
- **Dependencies:** Build artifacts
- **Tests / validation:** Synthetic pattern and real scan.
- **Expected result:** No active secrets exposed.
- **Definition of Done:** Scan passes.
- **Authority:** Documents 22,25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P11-04 — Run container image scan

- **Workstream:** Security
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Check backend runtime/base image.
- **Build:** Use Trivy or approved scanner; review OS/JRE/dependency findings and Docker config.
- **How it works:** Run non-root where practical.
- **Dependencies:** Docker image
- **Tests / validation:** Scan report + runtime user check.
- **Expected result:** No release-blocking image vulnerability.
- **Definition of Done:** Report attached.
- **Authority:** Documents 25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P11-05 — Run DAST/API security suite

- **Workstream:** Security
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Challenge deployed controlled environment.
- **Build:** Use OWASP ZAP/manual API cases for auth, headers, methods, common web weaknesses.
- **How it works:** Never run destructive test against real pilot data.
- **Dependencies:** Test environment
- **Tests / validation:** DAST report.
- **Expected result:** No critical web/API vulnerability.
- **Definition of Done:** Findings resolved/accepted.
- **Authority:** Documents 25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P11-06 — Run explicit IDOR/CSRF/CORS/XSS suite

- **Workstream:** Security
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Validate core web trust boundaries.
- **Build:** Execute User A/B resources, missing CSRF, bad origins, malicious topic/source/AI markdown payloads.
- **How it works:** 0 cross-user access; scripts inert.
- **Dependencies:** Complete app
- **Tests / validation:** Automated security integration/E2E.
- **Expected result:** Trust boundaries hold.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 22,25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P11-07 — Run upload/parser abuse suite

- **Workstream:** Security
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Protect ingestion availability.
- **Build:** Test disguised MIME, traversal filename, corrupt/encrypted/extreme pages, oversized image, resource amplification and stage timeouts.
- **How it works:** One file cannot crash whole service.
- **Dependencies:** P3 pipeline
- **Tests / validation:** Security fixtures/load.
- **Expected result:** Unsafe files fail safely.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 21,25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P11-08 — Run prompt injection/RAG forgery suite

- **Workstream:** Security
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Prove AI cannot escalate authority.
- **Build:** Inject source/student instructions, request system prompt/cross-user data, forged source refs.
- **How it works:** Scope/grounding/validation remain unchanged.
- **Dependencies:** AI/RAG
- **Tests / validation:** Golden adversarial cases.
- **Expected result:** No authority or data-scope escalation.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 22,25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P11-09 — Run provider outage/fallback drills

- **Workstream:** Reliability
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Validate graceful AI degradation.
- **Build:** Simulate Gemini down, Ollama down, both down, auth failure, rate limit, timeout.
- **How it works:** Student work preserved; fallback only when compatible.
- **Dependencies:** AI layer
- **Tests / validation:** Resilience integration tests.
- **Expected result:** System remains consistent and transparent.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 24,25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P11-10 — Run Neon/R2/backend restart drills

- **Workstream:** Reliability
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Validate persistence/recovery.
- **Build:** Simulate DB unavailable, R2 timeout, Render restart mid-job/mission.
- **How it works:** No fake persistence; jobs resume; mission state remains consistent.
- **Dependencies:** Infrastructure abstractions
- **Tests / validation:** Controlled failure tests.
- **Expected result:** Recovery matches runbooks.
- **Definition of Done:** Tests pass.
- **Authority:** Documents 24,25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P11-11 — Verify kill switches and incident runbooks

- **Workstream:** Operations
- **Priority:** Should
- **Status:** Not Started
- **Goal:** Ensure team can contain failures.
- **Build:** Test config disable for providers/uploads/OCR/large processing; verify provider key rotation and incident steps.
- **How it works:** Core data remains available where safe.
- **Dependencies:** Feature flags/config
- **Tests / validation:** Manual drill checklist.
- **Expected result:** High-risk capability can be disabled without code rewrite.
- **Definition of Done:** Runbook evidence recorded.
- **Authority:** Documents 22,24
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P11-12 — Phase 11 security/reliability gate

- **Workstream:** Gate
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Decide whether release is safe enough for pilot.
- **Build:** Aggregate all security scans, adversarial tests and resilience results; classify remaining findings.
- **How it works:** Critical exploitable issues block.
- **Dependencies:** All P11
- **Tests / validation:** Formal checklist review.
- **Expected result:** No release-blocking security/reliability issue.
- **Definition of Done:** Gate signed/recorded.
- **Authority:** Documents 25,26
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

# Phase 12 — Evaluation + Performance + Pilot Readiness

**Primary goal:** RAG/AI/ingestion/load/backup/E2E evidence supports controlled ~40-student pilot decision.

**Milestone:** M5 — Pilot Candidate

**Implementation items:** 12


## P12-01 — Run final Golden RAG evaluation

- **Workstream:** Evaluation
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Prove retrieval quality on representative medical sources.
- **Build:** Execute versioned dataset across exact, semantic, mixed, visual queries; capture Recall@K/Precision@K/MRR and failure cases.
- **How it works:** Use final active index generation.
- **Dependencies:** P4/P9
- **Tests / validation:** Automated evaluation run.
- **Expected result:** Metrics meet documented pilot threshold; failures understood.
- **Definition of Done:** Report/version stored.
- **Authority:** Documents 15,25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P12-02 — Run final AI task evaluation

- **Workstream:** Evaluation
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Validate Gemini/Ollama task quality and routing.
- **Build:** Evaluate explanation, questions, response evaluation, connections, scenarios, visual tasks where supported using shared rubrics.
- **How it works:** Record provider/model/prompt versions.
- **Dependencies:** P5-P9
- **Tests / validation:** Golden AI evaluation.
- **Expected result:** Routing choices have evidence and core tasks pass quality gates.
- **Definition of Done:** Report stored.
- **Authority:** Documents 15,25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P12-03 — Review medical correctness sample

- **Workstream:** Evaluation
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Add qualified human review for high-impact task samples.
- **Build:** Review representative generated explanations/evaluations/scenarios for correctness, overreach and clarity.
- **How it works:** Student feedback does not replace correctness review.
- **Dependencies:** AI evaluation
- **Tests / validation:** Reviewer rubric.
- **Expected result:** No systematic dangerous medical error.
- **Definition of Done:** Findings addressed.
- **Authority:** Documents 15,25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P12-04 — Benchmark ingestion capacity

- **Workstream:** Performance
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Measure real large-file resource use.
- **Build:** Process native/scanned/mixed 600+ page materials; record time, peak memory, chunks, storage growth, retries.
- **How it works:** Use pilot-like limits.
- **Dependencies:** P3
- **Tests / validation:** Benchmark suite.
- **Expected result:** Configured limits are evidence-based.
- **Definition of Done:** Results documented.
- **Authority:** Documents 21,25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P12-05 — Run 10-user load profile

- **Workstream:** Performance
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Establish baseline pilot behavior.
- **Build:** Simulate realistic mix of browsing, missions, AI, one/few uploads.
- **How it works:** Not 10 infinite simultaneous generations.
- **Dependencies:** Complete system
- **Tests / validation:** Load test.
- **Expected result:** Latency/queues/resources acceptable.
- **Definition of Done:** Metrics stored.
- **Authority:** Documents 25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P12-06 — Run 20-user load profile

- **Workstream:** Performance
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Validate scaling trend.
- **Build:** Repeat realistic mixed workload and observe DB/provider/backend pressure.
- **How it works:** Compare to 10-user baseline.
- **Dependencies:** 10-user test
- **Tests / validation:** Load test.
- **Expected result:** No unstable queue/storage growth.
- **Definition of Done:** Metrics stored.
- **Authority:** Documents 25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P12-07 — Run 40-user load profile

- **Workstream:** Performance
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Validate target pilot capacity.
- **Build:** Simulate approximately 40 invited-user behavior with bounded uploads/AI and concurrent missions.
- **How it works:** Respect provider free-tier test budget; mocks may supplement live quota tests.
- **Dependencies:** 20-user test
- **Tests / validation:** Load simulation + targeted live provider checks.
- **Expected result:** System remains usable or explicit quotas/limits are adjusted before pilot.
- **Definition of Done:** Capacity decision recorded.
- **Authority:** Documents 23,25,26
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P12-08 — Validate free-tier capacity and quotas

- **Workstream:** Infrastructure
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Ensure PILOT-FREE limits are realistic.
- **Build:** Measure Neon DB growth, R2 objects/storage, Render behavior/cold starts, Gemini/Ollama usage and projected exhaustion.
- **How it works:** Set warning/critical thresholds.
- **Dependencies:** Load/ingestion data
- **Tests / validation:** Capacity spreadsheet/report.
- **Expected result:** Pilot quotas prevent surprise exhaustion.
- **Definition of Done:** Thresholds configured.
- **Authority:** Documents 23,24
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P12-09 — Verify backup and restore

- **Workstream:** Operations
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Prove recoverability.
- **Build:** Create pg_dump/approved backup, restore to isolated DB, run Flyway/schema/integrity smoke tests; verify source metadata consistency.
- **How it works:** Never overwrite pilot during test.
- **Dependencies:** P23 ops
- **Tests / validation:** Restore checklist.
- **Expected result:** Backup is actually usable.
- **Definition of Done:** Restore evidence recorded.
- **Authority:** Documents 24,25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P12-10 — Run full release-candidate journey

- **Workstream:** E2E
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Validate complete student flow after all hardening.
- **Build:** Login → organize → upload/process → study mission → visual task → evidence → review → progress → logout; include error recovery.
- **How it works:** Use RC deployment.
- **Dependencies:** All phases
- **Tests / validation:** Playwright/manual exploratory/accessibility.
- **Expected result:** Core product works end-to-end.
- **Definition of Done:** RC E2E green.
- **Authority:** Documents 25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P12-11 — Complete release gate checklist

- **Workstream:** Release
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Make pilot decision explicit.
- **Build:** Review all Document 25 blocking criteria, vulnerabilities, known limitations, privacy disclosure, runbooks and capacity.
- **How it works:** No unresolved blocker can be hand-waved.
- **Dependencies:** All P12
- **Tests / validation:** Formal checklist.
- **Expected result:** Hippocampus v1.0.0-RC approved or rejected with reasons.
- **Definition of Done:** Decision recorded.
- **Authority:** Documents 25,26
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P12-12 — Prepare controlled pilot configuration

- **Workstream:** Pilot
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Deploy only after gate passes.
- **Build:** Configure invited users, quotas, monitoring thresholds, support/contact path, privacy/educational disclaimer and feedback/evaluation plan.
- **How it works:** PILOT-FREE remains controlled, non-commercial validation.
- **Dependencies:** Release gate
- **Tests / validation:** Pilot smoke test.
- **Expected result:** Environment is ready for real med-student use within known limits.
- **Definition of Done:** M5 evidence complete.
- **Authority:** Documents 22,23,26
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

# Completion Evidence Standard

Useful evidence includes:
- pull request/commit;
- automated test result;
- CI run;
- architecture/security scan;
- migration number;
- API contract/example;
- screenshot/video for UX behavior;
- benchmark/evaluation report;
- load-test report;
- runbook result.

Do not mark a task Done with only a textual claim that it “works.”

# Change Governance

If implementation reveals a significant decision not already resolved by the Source of Truth:
1. Keep the task Blocked.
2. Identify the conflicting/ambiguous authoritative document.
3. Follow Document 27.
4. Create an ADR if required.
5. Patch affected Source-of-Truth documents if the accepted decision changes them.
6. Resume the task only after the decision is resolved.
