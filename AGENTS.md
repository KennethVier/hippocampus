# AGENTS.md — Project Hippocampus

## Purpose

Persistent router for coding agents working in Hippocampus. Keep this file small; detailed product/technical decisions live in `docs/`, and reusable task guidance lives in `.agents/skills/`.

## Authority

When requirements conflict, follow:

1. `docs/00-*`–`docs/15-*` — product/education
2. `docs/16-*`–`docs/25-*` — technical
3. `docs/26-development-roadmap-and-implementation-phases.md`
4. `docs/27-decision-log-and-adr-index.md`
5. accepted ADRs in `docs/adr/`
6. `docs/IMPLEMENTATION-TRACKER.md`
7. `docs/design/DESIGN.md`
8. `docs/design/references/`
9. code

Code never silently overrides higher authority.

## Product Boundaries

- Primary user: medical student.
- Study Missions are the core learning experience.
- AI supports bounded tasks; the Learning Engine owns pedagogical sequencing and educational state.
- Learning evidence must map to real student activity.
- Material != Topic.
- RAG authorization/scope happens before ranking; cross-user leakage tolerance is zero.
- Gemini API and remote Ollama API remain provider adapters behind the Provider Router.
- PostgreSQL is authoritative persistence; pgvector is the retrieval foundation.
- Uploaded binaries belong in private object storage, not PostgreSQL blobs.

## Implementation Workflow

For normal tracker implementation:

1. Identify the exact tracker task.
2. Read the task entry and only the authority documents needed for that task.
3. Confirm phase/dependency scope and do not pull later tracker work forward.
4. If a detailed plan was already approved externally, implement it directly; do not re-plan unless a concrete blocker or contradiction appears.
5. Make the smallest correct, reviewable change.
6. Run focused validation first, then tracker-required validation.
7. Report concise factual evidence and leave publication/merge decisions to external review unless explicitly requested.

Use `hippocampus-implement-task` for ordinary implementation execution. Use other skills only when their detailed guidance is materially relevant. Do not chain all backend/frontend/security skills by default.

## Context / Token Efficiency

- Prefer targeted file reads, targeted searches, and focused tests.
- Do not perform broad repository reconnaissance by default.
- Do not inspect Git HEAD, commit history, branches, PR metadata, or unrelated diffs unless the current task genuinely depends on them.
- Do not search for, enumerate, or spawn agents/subagents unless delegation solves a concrete parallelizable problem.
- Do not enumerate skills or load broad skills “just in case.”
- Do not install/download tools, dependencies, agents, or CLIs unless implementation cannot proceed correctly without them.
- Do not repeatedly reread files or rediscover repository structure already established in the current task.
- Expand context only for a concrete implementation blocker, ambiguity, dependency, failing test, architecture question, or security requirement.

Single-agent execution is the default.

## Architecture

Backend: Spring Boot modular monolith.

Approved modules: `identity`, `learning`, `progress`, `review`, `materials`, `rag`, `ai`, `shared`, `bootstrap`.

Feature internals where needed: `api`, `application`, `domain`, `port`, `infrastructure`.

Dependency direction:

`api -> application -> domain/ports <- infrastructure`

Domain code must not depend on Spring MVC, JPA repositories, provider SDKs, HTTP clients, or framework infrastructure.

## Engineering Baseline

- Java 25; Spring Boot 4.1.x per Document 17; Maven.
- Constructor injection; explicit cohesive responsibilities; composition over inheritance.
- No speculative abstractions, dependencies, infrastructure, or future architecture.
- Controllers stay thin; authorization must be enforceable below transport.
- Transactions belong around application use cases and must not span unnecessary AI/network calls.
- Do not expose JPA entities through APIs; Flyway owns schema evolution.
- Provider DTOs stay inside provider adapters.
- Frontend visual work follows `docs/design/DESIGN.md`; reference screenshots communicate intent, not new product requirements.

Detailed Java, Spring Boot, React/TypeScript, architecture, testing, review, and security guidance remains available in the corresponding `.agents/skills/` skill and should be loaded only when relevant.

## Mandatory Security Gate

Security remains a separate completion gate:

1. implementation tests pass;
2. general implementation/code review completes;
3. run `hippocampus-security-vulnerability-review` independently.

Use OWASP ASVS 5.0.0 as the verification baseline with OWASP Top 10:2025 and OWASP API Security Top 10 as threat lenses.

Critical/High findings block completion. Medium findings normally block unless a human explicitly accepts the risk. If a control cannot be adequately verified, return `MANUAL SECURITY REVIEW REQUIRED`.

## Do Not Introduce Without Approved Decision

Do not introduce microservices, CQRS, event sourcing, Redis, Kafka, Kubernetes, GraphQL, reactive/WebFlux architecture, JWT replacing approved session auth, a dedicated vector DB replacing PostgreSQL + pgvector, undocumented external services, or undocumented MVP features.

If a significant unresolved decision appears, follow Document 27.

## Completion Rule

`Done` requires implementation, required tests, demonstrated expected behavior, blocker-free general review, the independent security gate (or explicit manual review/risk acceptance), tracker Definition of Done/evidence, and no undocumented architectural deviation.
