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
- Hippocampus is not primarily upload/chat, summarization, flashcard generation, or quiz generation.
- AI supports bounded tasks; the Learning Engine owns pedagogical sequencing and educational state.
- Learning evidence must map to real student activity.
- Material != Topic.
- Provenance must survive retrieval/generation.
- RAG authorization/scope happens before ranking; cross-user leakage tolerance is zero.
- Gemini API and remote Ollama API remain provider adapters behind the Provider Router.
- PostgreSQL is authoritative persistence; pgvector is the retrieval foundation.
- Uploaded binaries belong in private object storage, not PostgreSQL blobs.

## Agent Workflow

Normal tracker work uses three roles:

1. **Plan** — Human + ChatGPT resolve the tracker task, minimum authority, scope, decisions, validation commands, and stop conditions, then produce a compact implementation packet.
2. **Implement** — Codex implements that packet directly with `hippocampus-implement-task`. It does not re-plan unless a concrete contradiction or blocker appears.
3. **Review** — an independent reviewer inspects the actual diff plus user/CI evidence, performs general review, then the required independent security pass.

Do not add a separate validation agent to the normal loop. Broad validation is user-executed or CI-executed and supplied as evidence to review.

## Implementation Command Policy

During ordinary implementation, Codex runs **no commands** except an eligible test created or modified by the current task.

An eligible agent-run test must satisfy all of these:

- the test/test method was created or modified by the current implementation;
- the narrowest practical target is used (method first, then class/file only when method-level execution is impractical);
- it does not require Docker, Docker Compose, Testcontainers, external infrastructure, application startup, or other expensive environment setup.

Everything else is user-run unless the current user instruction explicitly authorizes the exact command.

Codex must not run by default:

- full/module test suites or unchanged tests;
- build, package, verify, lint, typecheck, or validation scripts;
- application startup;
- Docker/Docker Compose/Testcontainers/database startup;
- Flyway/container validation;
- Playwright/E2E;
- architecture suites;
- Git inspection/hygiene commands solely for ceremony;
- any other command not permitted above.

Do not infer command authorization from "recommended", "normally required", tracker validation language, or good practice. Instead, list the exact commands the user should run in the implementation report.

An implementation report must not claim `VALIDATION PASS` or `Done` from the agent-run changed test alone. Use `IMPLEMENTED — USER VALIDATION REQUIRED` until broader required evidence is supplied.

## Communication Policy

Implementation agents are quiet by default.

Do not narrate file reads, routine reasoning, planned edits, progress, skipped commands, or obvious implementation steps. Speak only when:

- a real blocker prevents implementation;
- the approved packet conflicts with authority/repository reality;
- a significant unresolved decision requires human input;
- an eligible changed test fails and materially affects the implementation; or
- implementation is finished.

Keep blocker and final reports terse and factual.

## Context / Token Efficiency

- Prefer targeted file reads and targeted searches.
- Do not perform broad repository reconnaissance by default.
- Do not inspect Git HEAD, commit history, branches, PR metadata, or unrelated diffs unless the current task genuinely depends on them.
- Do not search for, enumerate, or spawn agents/subagents unless delegation solves a concrete parallelizable problem.
- Do not enumerate skills or load broad skills “just in case.”
- Do not install/download tools, dependencies, agents, or CLIs unless implementation cannot proceed correctly without them and the task authorizes it.
- Do not repeatedly reread files or rediscover repository structure already established in the current task.
- Expand context only for a concrete implementation blocker, ambiguity, dependency, failing eligible test, architecture question, or security requirement.
- Successful broad validation output should stay outside implementation context; provide only concise pass/fail evidence to review unless a failure needs investigation.

Single-agent execution is the default within each role.

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

Detailed Java, Spring Boot, React/TypeScript, architecture, testing, review, and security guidance remains available in the corresponding `.agents/skills/` skill and should be loaded only when a concrete issue requires it.

## Mandatory Security Gate

Security remains a separate review pass before completion:

1. required user/CI validation evidence is available;
2. general implementation/code review completes;
3. run `hippocampus-security-vulnerability-review` independently using the diff and available evidence.

Use OWASP ASVS 5.0.0 as the verification baseline with OWASP Top 10:2025 and OWASP API Security Top 10 as threat lenses.

Critical/High findings block completion. Medium findings normally block unless a human explicitly accepts the risk. If a control cannot be adequately verified, return `MANUAL SECURITY REVIEW REQUIRED`.

Review agents do not rerun broad validation by default. They request missing user/CI evidence when required.

## Do Not Introduce Without Approved Decision

Do not introduce microservices, CQRS, event sourcing, Redis, Kafka, Kubernetes, GraphQL, reactive/WebFlux architecture, JWT replacing approved session auth, a dedicated vector DB replacing PostgreSQL + pgvector, undocumented external services, or undocumented MVP features.

If a significant unresolved decision appears, follow Document 27.

## Completion Rule

`Done` requires implementation, required user/CI validation evidence, demonstrated expected behavior, blocker-free general review, the independent security gate (or explicit manual review/risk acceptance), tracker Definition of Done/evidence, and no undocumented architectural deviation.
