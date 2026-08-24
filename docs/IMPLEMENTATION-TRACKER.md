---
Document ID: IMPLEMENTATION-TRACKER
Title: Hippocampus v1 Detailed Implementation Tracker
Version: 1.0.0
Status: Active
Owner: Project Hippocampus Team
Created: 2026-08-24
Last Updated: 2026-08-24
Purpose: Operational tracker for implementing the frozen Hippocampus v1 Source of Truth phase by phase with concrete build requirements, tests, expected behavior, definition of done, and evidence.
Authority: Document 26 defines implementation order; Documents 00–25 define product/technical requirements; Document 27 governs deviations.
---

# Hippocampus v1 Detailed Implementation Tracker

> **A task is not Done because code exists. It is Done only when the required build exists, the listed validation passes, expected behavior is demonstrated, the Definition of Done is satisfied, and evidence is recorded.**

## Status Rules

- **Not Started** — no implementation work accepted yet.
- **In Progress** — implementation or required tests are actively incomplete.
- **Blocked** — a real dependency/decision prevents progress; blocker must be recorded.
- **Ready for Review** — implementation and tests are complete, awaiting review/gate verification.
- **Done** — build + tests + Definition of Done + evidence are all complete.

## Phase Gate Rule

A phase may be marked **PASS** only when:
1. All **Must** tasks in the phase are Done.
2. The explicit phase **Gate** task is Done.
3. Required automated suites pass.
4. No undocumented architecture deviation exists.
5. Security/observability requirements introduced in that phase are working.
6. Evidence links/notes are recorded.


# Phase 0 — Engineering Foundation

**Primary goal:** Reproducible Java/Spring + React foundation with DB, CI, security and architecture tests.

**Milestone:** M0 — Engineering Skeleton

**Implementation items:** 15


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
- **Evidence / link:** Java 25.0.4; Maven Wrapper 3.9.16; `mvnw.cmd test` and `mvnw.cmd clean verify` passed (1 test, 0 failures); executable JAR `backend/target/hippocampus-backend-0.0.1-SNAPSHOT.jar`; packaged runtime `GET /health` returned 200, `application/json`, and `{"status":"UP"}`; dependency tree and Doc 19 source structure reviewed; commit/PR pending.
- **Notes / blockers:** _None_

## P0-02 — Create frontend repository structure

- **Workstream:** Repository
- **Priority:** Must
- **Status:** Done
- **Goal:** Establish React application baseline.
- **Build:** Create React 19 + TypeScript + Vite project with app, features, components, api, hooks, state, schemas, types, test folders.
- **How it works:** App shell owns providers/router; feature folders own feature UI.
- **Dependencies:** None
- **Tests / validation:** npm test/build/typecheck/lint.
- **Expected result:** Frontend loads base route without runtime/type errors.
- **Definition of Done:** Build/test/typecheck pass; folders match Doc 20.
- **Authority:** Documents 17,20,26
- **Evidence / link:** Node 24.16.0; npm 11.13.0; React/React DOM 19.2.8, TypeScript 6.0.3, Vite 8.1.5; `npm.cmd test` passed (1 test, 0 failures); typecheck, lint, and production build passed; built preview `GET /` returned 200 HTML with the application root/module entry; direct dependencies and Doc 20 source structure reviewed; commit/PR pending.
- **Notes / blockers:** _None_

## P0-03 — Configure Spring profiles

- **Workstream:** Backend
- **Priority:** Must
- **Status:** Ready for Review
- **Goal:** Separate local/test/pilot configuration safely.
- **Build:** Create application.yml plus local/test/pilot profiles; externalize secrets and resource limits.
- **How it works:** Runtime selects profile; secrets are injected via environment.
- **Dependencies:** Backend project
- **Tests / validation:** Profile-specific smoke tests; no secrets in Git.
- **Expected result:** App boots in local and test profiles with correct overrides.
- **Definition of Done:** Profiles documented and no secret values committed.
- **Authority:** Documents 17,19,22,23
- **Evidence / link:** Java 25.0.4; Maven Wrapper 3.9.16 running on Java 25.0.4; base/local/test/pilot profile configuration and concise backend documentation added; `mvnw.cmd test` and `mvnw.cmd clean verify` passed (4 tests, 0 failures); packaged JAR local `SERVER_PORT` and pilot `PORT` smoke tests returned the exact `/health` contract over HTTP 200; archive contains base/local/pilot configuration and excludes test configuration; secret/later-task configuration scans, dependency/scope audit, and `git diff --check` passed; commit/PR pending.
- **Notes / blockers:** _None_

## P0-04 — Provision local PostgreSQL + pgvector

- **Workstream:** Database
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Provide reproducible local relational/vector infrastructure.
- **Build:** Create Docker Compose PostgreSQL 18 with vector and pg_trgm extensions.
- **How it works:** Developers start DB locally; Spring connects through env config.
- **Dependencies:** Backend project
- **Tests / validation:** Container starts; SELECT extversion for vector; pg_trgm enabled.
- **Expected result:** Local DB is reproducible from one command.
- **Definition of Done:** Compose file works and extension tests pass.
- **Authority:** Documents 17,18,23
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P0-05 — Initialize Flyway

- **Workstream:** Database
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Make schema evolution version-controlled from day one.
- **Build:** Configure Flyway and create baseline migration for extension/bootstrap objects only.
- **How it works:** App startup validates/applies migrations; Hibernate uses validate.
- **Dependencies:** Local PostgreSQL
- **Tests / validation:** Empty DB migration test; second startup is idempotent.
- **Expected result:** Schema builds from zero consistently.
- **Definition of Done:** CI migration-from-zero test passes.
- **Authority:** Documents 17,18,25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P0-06 — Add architecture enforcement tests

- **Workstream:** Architecture
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Prevent module drift early.
- **Build:** Configure ArchUnit rules for API→application→domain/ports and infrastructure isolation.
- **How it works:** CI fails if forbidden dependencies appear.
- **Dependencies:** Backend structure
- **Tests / validation:** Introduce deliberate violation test locally, then remove; CI green.
- **Expected result:** Forbidden layer dependencies are automatically detected.
- **Definition of Done:** Architecture tests run on every PR.
- **Authority:** Documents 19,25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P0-07 — Create error contract foundation

- **Workstream:** Backend
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Standardize API failures before features expand.
- **Build:** Implement ProblemDetail/error-code mapper, correlation ID filter, common domain/application exception hierarchy.
- **How it works:** Controllers throw typed errors; central handler maps stable code/message/correlationId.
- **Dependencies:** Backend project
- **Tests / validation:** Controller test for validation, not-found, conflict, internal error.
- **Expected result:** Errors are consistent and do not leak internals.
- **Definition of Done:** Stable error JSON verified.
- **Authority:** Documents 19,22,24
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P0-08 — Add Actuator and structured logging

- **Workstream:** Observability
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Make foundation observable immediately.
- **Build:** Enable safe health endpoints, Micrometer baseline, JSON/structured log fields, correlation IDs.
- **How it works:** Every request receives opaque correlation ID propagated to logs.
- **Dependencies:** Backend project
- **Tests / validation:** Health test; log smoke test; sensitive-value grep.
- **Expected result:** Health works and logs contain no secrets.
- **Definition of Done:** Liveness/readiness and correlation verified.
- **Authority:** Documents 17,24
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P0-09 — Create application shell and routing

- **Workstream:** Frontend
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Provide consistent app frame without feature sprawl.
- **Build:** Implement router, auth-placeholder layout, compact navigation shell, route-level error boundary.
- **How it works:** Feature routes render inside app shell.
- **Dependencies:** Frontend project
- **Tests / validation:** Route smoke tests at desktop/mobile widths.
- **Expected result:** Core routes render and unknown routes fail gracefully.
- **Definition of Done:** Navigation shell works responsively.
- **Authority:** Documents 20
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P0-10 — Create centralized API client

- **Workstream:** Frontend
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Prevent ad hoc fetch usage.
- **Build:** Implement base URL, credentials, JSON/problem parsing, AbortSignal and multipart helpers.
- **How it works:** All feature API modules depend on this client.
- **Dependencies:** Frontend project
- **Tests / validation:** Unit tests for 2xx, 4xx ProblemDetail, network abort.
- **Expected result:** Errors normalize consistently.
- **Definition of Done:** No direct fetch calls outside approved client/streaming abstraction.
- **Authority:** Documents 20,22
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P0-11 — Create core UI states/components

- **Workstream:** Frontend
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Standardize basic UX.
- **Build:** Implement Button, Input, Textarea, Select, Card, Dialog/Drawer, Badge, Progress, Skeleton, EmptyState, ErrorState.
- **How it works:** Features compose these without creating a huge UI library.
- **Dependencies:** Frontend project
- **Tests / validation:** Component tests + keyboard/focus smoke tests.
- **Expected result:** Reusable states render consistently.
- **Definition of Done:** Core component suite passes accessibility smoke tests.
- **Authority:** Documents 20,25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P0-12 — Create GitHub Actions quality pipeline

- **Workstream:** CI/CD
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Make quality gates automatic.
- **Build:** Add backend tests, frontend lint/typecheck/test, architecture tests, migration integration tests, secret/dependency scans, builds.
- **How it works:** PR must pass deterministic checks before merge.
- **Dependencies:** Repositories
- **Tests / validation:** Open sample PR; intentionally fail one check.
- **Expected result:** CI blocks broken code and reports failures clearly.
- **Definition of Done:** Required jobs green on main.
- **Authority:** Documents 23,25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P0-13 — Add secret scanning and dependency monitoring

- **Workstream:** Security
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Prevent credential leaks and known vulnerable dependencies.
- **Build:** Configure Gitleaks and Dependabot/security alerts where available; add ignore process documentation.
- **How it works:** Scans run on PR/schedule; findings are triaged.
- **Dependencies:** CI pipeline
- **Tests / validation:** Synthetic fake-secret fixture in isolated test; dependency alert workflow review.
- **Expected result:** Secrets and vulnerable dependency signals are visible before release.
- **Definition of Done:** Security checks documented and active.
- **Authority:** Documents 22,24,25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P0-14 — Create Testcontainers foundation

- **Workstream:** Testing
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Provide realistic integration tests.
- **Build:** Configure PostgreSQL 18 + pgvector Testcontainer base test support.
- **How it works:** Integration tests create isolated DB and run Flyway.
- **Dependencies:** Backend + Docker
- **Tests / validation:** Repository smoke test against container.
- **Expected result:** CI can test actual PostgreSQL behavior.
- **Definition of Done:** Reusable integration test base committed.
- **Authority:** Documents 17,25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P0-15 — Create implementation tracker process

- **Workstream:** Documentation
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Make completion evidence mandatory.
- **Build:** Add tracker workflow, statuses, phase-gate rules, authority references, evidence links.
- **How it works:** Task may only move to Done when DoD and tests pass.
- **Dependencies:** Docs frozen
- **Tests / validation:** Review tracker with Phase 0 tasks.
- **Expected result:** Implementation work has a single progress source.
- **Definition of Done:** Tracker committed and used in PR workflow.
- **Authority:** Documents 26,27
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

# Phase 1 — Identity + Core Student Workspace

**Primary goal:** Secure private student identity, session lifecycle and ownership boundary.

**Implementation items:** 11


## P1-01 — Create user schema

- **Workstream:** Database
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Persist internal user identity.
- **Build:** Create users table/entity/repository with UUID, email, display_name, status, timestamps.
- **How it works:** Internal user ID is ownership root; provider/session details remain separate.
- **Dependencies:** P0 Flyway
- **Tests / validation:** Migration/repository CRUD/unique-email tests.
- **Expected result:** Users persist and unique constraints hold.
- **Definition of Done:** Migration + entity mapping + repository tests pass.
- **Authority:** Documents 18
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P1-02 — Implement authentication flow

- **Workstream:** Security
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Allow a student to establish an authenticated session.
- **Build:** Implement chosen v1 login credential flow using Spring Security; map identity to users.id.
- **How it works:** Successful authentication creates/uses server-side session.
- **Dependencies:** User schema
- **Tests / validation:** Auth integration tests: valid/invalid/disabled user.
- **Expected result:** Authenticated user can enter app; invalid credentials reveal no sensitive details.
- **Definition of Done:** Auth tests pass and flow documented.
- **Authority:** Documents 22
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P1-03 — Configure Spring Session JDBC

- **Workstream:** Security
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Persist sessions server-side.
- **Build:** Enable Spring Session JDBC and required schema; configure idle timeout.
- **How it works:** Browser stores only secure session cookie.
- **Dependencies:** Authentication flow
- **Tests / validation:** Session persistence/expiry/restart tests.
- **Expected result:** Session survives app restart if DB remains; expired sessions fail.
- **Definition of Done:** Session tests pass.
- **Authority:** Documents 17,22
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P1-04 — Implement CSRF protection

- **Workstream:** Security
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Protect cookie-authenticated mutations.
- **Build:** Configure Spring Security CSRF and frontend token acquisition/submission.
- **How it works:** GETs read; state-changing requests require valid CSRF token.
- **Dependencies:** Auth/session
- **Tests / validation:** Missing-token rejection and valid-token success tests.
- **Expected result:** Cross-site forged state change is rejected.
- **Definition of Done:** CSRF tests pass.
- **Authority:** Documents 22,25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P1-05 — Restrict CORS

- **Workstream:** Security
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Allow only approved origins.
- **Build:** Configure local and pilot frontend origins with credentials support.
- **How it works:** Backend rejects credentialed requests from unapproved origins.
- **Dependencies:** Profiles
- **Tests / validation:** CORS integration tests.
- **Expected result:** Approved frontend works; wildcard credentialed CORS absent.
- **Definition of Done:** CORS tests pass.
- **Authority:** Documents 22
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P1-06 — Create current-user access abstraction

- **Workstream:** Domain
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Remove client-controlled ownership identity.
- **Build:** Implement CurrentUser/AuthenticatedUser port resolved from Spring Security principal.
- **How it works:** Application use cases obtain user ID server-side.
- **Dependencies:** Authentication
- **Tests / validation:** Unit/integration tests; attempts to inject userId ignored/rejected.
- **Expected result:** Resource ownership always derives from session.
- **Definition of Done:** No use case accepts client userId as authority.
- **Authority:** Documents 19,22
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P1-07 — Implement /me session endpoint

- **Workstream:** Backend
- **Priority:** Should
- **Status:** Not Started
- **Goal:** Give frontend safe session/user context.
- **Build:** Return minimal user profile/session state.
- **How it works:** Frontend queries /me on authenticated shell bootstrap.
- **Dependencies:** Current-user abstraction
- **Tests / validation:** API tests authenticated/unauthenticated.
- **Expected result:** Frontend can determine logged-in user without tokens.
- **Definition of Done:** Endpoint stable and privacy-minimal.
- **Authority:** Documents 20,22
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P1-08 — Implement login/session recovery UX

- **Workstream:** Frontend
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Handle auth lifecycle cleanly.
- **Build:** Create login screen, protected routes, session bootstrap, expired-session redirect, return-path handling.
- **How it works:** TanStack Query tracks /me; no tokens in localStorage.
- **Dependencies:** Auth API
- **Tests / validation:** RTL/Playwright login/logout/expiry tests.
- **Expected result:** User logs in/out; expired session returns safely to login.
- **Definition of Done:** Critical E2E auth journey passes.
- **Authority:** Documents 20,25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P1-09 — Clear private state on logout

- **Workstream:** Frontend
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Prevent shared-device leakage.
- **Build:** Clear query cache, user-scoped Zustand, active streams and drafts on logout.
- **How it works:** Logout response invalidates backend session then frontend state.
- **Dependencies:** Frontend auth
- **Tests / validation:** Component/E2E shared-device test.
- **Expected result:** Next user cannot see previous user's cached data.
- **Definition of Done:** Logout privacy test passes.
- **Authority:** Documents 20,22
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P1-10 — Add ownership authorization test harness

- **Workstream:** Security
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Make IDOR checks reusable for every future resource.
- **Build:** Create test helpers with User A/User B and assertion patterns for 403/404/no data.
- **How it works:** Every new user-owned API reuses harness.
- **Dependencies:** Auth
- **Tests / validation:** Harness self-test.
- **Expected result:** Cross-user tests become cheap and mandatory.
- **Definition of Done:** Harness used by subsequent phases.
- **Authority:** Documents 22,25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P1-11 — Phase 1 end-to-end gate

- **Workstream:** Gate
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Prove private student workspace exists.
- **Build:** Run auth, session, CSRF, CORS, ownership and frontend journey as one release gate.
- **How it works:** All Phase 1 pieces operate together.
- **Dependencies:** All P1 tasks
- **Tests / validation:** Playwright + backend security suite.
- **Expected result:** Student securely enters and exits private workspace.
- **Definition of Done:** All Phase 1 acceptance tests green; no cross-user leak.
- **Authority:** Documents 26
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

# Phase 2 — Subjects + Topics + Learning Materials

**Primary goal:** Students can organize subjects/topics and privately upload/manage learning materials.

**Implementation items:** 13


## P2-01 — Create subject/topic/subtopic schema

- **Workstream:** Database
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Persist learner organization.
- **Build:** Create subjects, topics, subtopics tables/entities/repos with ownership path and archive status.
- **How it works:** Subject belongs to user; Topic belongs to Subject; one explicit Subtopic level.
- **Dependencies:** P1 identity
- **Tests / validation:** Flyway, FK, uniqueness, ownership repository tests.
- **Expected result:** Organization persists with correct hierarchy.
- **Definition of Done:** Schema and CRUD tests pass.
- **Authority:** Documents 18
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P2-02 — Implement Subject CRUD use cases/API

- **Workstream:** Backend
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Allow user-owned subject management.
- **Build:** Create create/list/get/update/archive subject use cases and REST endpoints.
- **How it works:** All queries derive authenticated owner.
- **Dependencies:** Subject schema
- **Tests / validation:** API validation and User A/B authorization tests.
- **Expected result:** Student only sees own subjects.
- **Definition of Done:** CRUD and IDOR suite pass.
- **Authority:** Documents 19,22
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P2-03 — Implement Topic/Subtopic CRUD use cases/API

- **Workstream:** Backend
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Allow organization within subjects.
- **Build:** Create topic/subtopic create/list/update/archive APIs with parent ownership validation.
- **How it works:** Topic cannot be attached to another user's subject.
- **Dependencies:** Subject API
- **Tests / validation:** CRUD, invalid parent, archive tests.
- **Expected result:** Hierarchy remains ownership-safe.
- **Definition of Done:** API tests pass.
- **Authority:** Documents 18,19
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P2-04 — Create Material and MaterialVersion foundation

- **Workstream:** Database
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Persist upload lifecycle separately from learning topics.
- **Build:** Create materials/material_versions schema with storage key, statuses, active_version_id, hashes/metadata.
- **How it works:** Material belongs to user; versions are immutable source revisions.
- **Dependencies:** P1 + Flyway
- **Tests / validation:** FK/version uniqueness/status tests.
- **Expected result:** Materials can have versioned lifecycle.
- **Definition of Done:** Schema/repository tests pass.
- **Authority:** Documents 18
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P2-05 — Implement object-storage port

- **Workstream:** Storage
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Separate binary storage from DB.
- **Build:** Create BinaryObjectStore port + local filesystem/local S3-compatible implementation for development.
- **How it works:** Backend stores opaque storage key; user filename is display metadata only.
- **Dependencies:** Material schema
- **Tests / validation:** Contract tests: put/get/delete, traversal-safe keys.
- **Expected result:** Original file can be stored/retrieved locally without DB blob.
- **Definition of Done:** Storage contract tests pass.
- **Authority:** Documents 17,21,22
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P2-06 — Implement upload initialization endpoint

- **Workstream:** Backend
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Accept supported material and create processing placeholder.
- **Build:** Multipart endpoint validates basic limits/type, stores original, creates Material + v1 + initial state/job placeholder.
- **How it works:** HTTP returns quickly; does not parse whole document.
- **Dependencies:** Object store + material schema
- **Tests / validation:** Multipart API tests: valid, empty, unsupported, oversized synthetic.
- **Expected result:** Upload returns PROCESSING/UPLOADED metadata promptly.
- **Definition of Done:** API and storage tests pass.
- **Authority:** Documents 19,21
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P2-07 — Implement material list/detail/delete

- **Workstream:** Backend
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Let student manage uploaded resources.
- **Build:** Create list/detail/delete APIs including status and metadata.
- **How it works:** Delete immediately removes future retrieval eligibility when later indexes exist.
- **Dependencies:** Material foundation
- **Tests / validation:** Authorization, pagination, delete-state tests.
- **Expected result:** Student sees only own materials and can remove them.
- **Definition of Done:** CRUD + ownership tests pass.
- **Authority:** Documents 18,22
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P2-08 — Create MaterialTopicLink

- **Workstream:** Database
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Keep Material ≠ Topic while allowing mappings.
- **Build:** Create material_topic_links with origin/status/version/node optional references.
- **How it works:** Mapping is many-to-many and validates same-user ownership.
- **Dependencies:** Subject/topic + material
- **Tests / validation:** Cross-user mapping rejection, duplicate-active-link tests.
- **Expected result:** A material can support many topics and vice versa.
- **Definition of Done:** Schema/use-case tests pass.
- **Authority:** Documents 14,18
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P2-09 — Build Subjects and Topics UI

- **Workstream:** Frontend
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Make organization usable without feature overload.
- **Build:** Create subject list, subject detail, topic cards/forms, archive interactions.
- **How it works:** Primary CTA from topic is future Study Mission, not tool selector.
- **Dependencies:** Subject/topic APIs
- **Tests / validation:** RTL + Playwright CRUD journey.
- **Expected result:** Student organizes study areas cleanly.
- **Definition of Done:** Desktop/mobile CRUD flow passes.
- **Authority:** Documents 20
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P2-10 — Build Materials UI and upload transfer progress

- **Workstream:** Frontend
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Provide simple upload lifecycle.
- **Build:** Create materials list, file picker/drop zone, transfer progress, metadata and delete UI.
- **How it works:** Transfer progress is distinct from backend processing progress.
- **Dependencies:** Material APIs
- **Tests / validation:** Component/E2E upload tests.
- **Expected result:** Student can upload and see accepted file immediately.
- **Definition of Done:** Upload journey passes.
- **Authority:** Documents 20,21
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P2-11 — Harden upload intake baseline

- **Workstream:** Security
- **Priority:** Must
- **Status:** Not Started
- **Goal:** Reject obvious malicious/invalid input before parsing.
- **Build:** Validate filename as metadata, MIME via Tika/content, size and supported types; safe generated storage keys.
- **How it works:** Input cannot select paths or bypass type checks.
- **Dependencies:** Upload API
- **Tests / validation:** Path traversal filename, disguised MIME, empty/corrupt/basic limit tests.
- **Expected result:** Unsafe intake is rejected safely.
- **Definition of Done:** Security fixture tests pass.
- **Authority:** Documents 21,22,25
- **Evidence / link:** _To be recorded during implementation_
- **Notes / blockers:** _None_

## P2-12 — Add material lifecycle telemetry

- **Workstream:** Observability
- **Priority:** Should
- **Status:** Not Started
- **Goal:** Make uploads diagnosable.
- **Build:** Log/metric material accepted/rejected/deleted and status changes using IDs only.
- **How it works:** No source content or secrets in logs.
- **Dependencies:** Material lifecycle
- **Tests / validation:** Inspect logs; privacy grep tests.
- **Expected result:** Operations can diagnose upload lifecycle.
- **Definition of Done:** Telemetry fields documented/tested.
- **Authority:** Documents 24
- **Evidence / link:** _To be recorded during implementation_
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
