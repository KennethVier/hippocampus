# AGENTS.md — Project Hippocampus

## Purpose

This file is the persistent operating guide for coding agents working in the Hippocampus repository.

Keep this file short. Detailed decisions live in `docs/` and reusable workflows live in `.agents/skills/`.

## Authority

Use this order whenever requirements appear ambiguous:

1. `docs/00-*` through `docs/15-*` — product and educational authority.
2. `docs/16-*` through `docs/25-*` — technical authority.
3. `docs/26-development-roadmap-and-implementation-phases.md` — implementation order.
4. `docs/27-decision-log-and-adr-index.md` — decision governance.
5. Accepted ADRs under `docs/adr/`.
6. `docs/IMPLEMENTATION-TRACKER.md` — exact implementation work and completion evidence.
7. `docs/design/DESIGN.md` — visual authority for approved frontend UI.
8. `docs/design/references/` — visual intent references; not pixel-perfect specifications.
9. Code.

Code never silently overrides a higher authority.

## Visual Design Authority

For frontend UI work:

- Product behavior, educational rules, security, domain rules, and architecture remain governed by the numbered Source-of-Truth documents, accepted ADRs, and the Implementation Tracker.
- `docs/design/DESIGN.md` governs visual language where those higher authorities do not define appearance.
- `docs/design/references/` demonstrates intended layout, density, hierarchy, and visual character.
- Reference screenshots contain illustrative mock content and must not create new product requirements.
- If a screenshot or DESIGN.md example conflicts with product behavior, the product Source of Truth wins.
- Do not independently redesign Hippocampus unless the owning tracker task explicitly requires a design change.

## Core Product Boundaries

- The primary user is the medical student.
- Study Missions are the core learning experience.
- Hippocampus is not primarily upload-and-chat, PDF summarization, flashcard generation, or quiz generation.
- AI supports bounded tasks; it does not own educational state.
- The Learning Engine owns pedagogical sequencing.
- Learning evidence must be traceable to real student activity.
- Material is not the same thing as Topic.
- RAG authorization/scope is enforced before retrieval ranking.
- Cross-user data leakage tolerance is zero.
- Gemini API and remote Ollama API are provider adapters behind the Provider Router.
- PostgreSQL is the authoritative persistent store; pgvector is part of the retrieval foundation.
- Uploaded binaries live in private object storage, not in PostgreSQL blobs.

## Engineering Principles

- Correctness, security, domain clarity, cohesion, and maintainability outrank novelty or conciseness.
- Apply SRP and SOLID as design guides, not mechanical rules.
- Prefer one cohesive responsibility and one primary reason to change; do not fragment cohesive behavior merely to create smaller classes.
- Use design patterns only when a real design pressure exists. The agent must be able to explain what problem the pattern solves and why simpler code is insufficient.
- Prefer composition over inheritance and explicit domain/use-case names over generic `Service`, `Manager`, `Processor`, `Util`, or `Helper` abstractions.
- Use modern Java/TypeScript/Spring features when they improve clarity, correctness, or domain expression. Do not use features merely because they are new or available.
- Do not introduce speculative abstractions, dependencies, infrastructure, or future architecture without an approved need.

Detailed guidance lives in:

- `hippocampus-java-spring-engineering` — Java 25 language/backend engineering baseline;
- `hippocampus-spring-boot-engineering` — Spring Boot 4.1.x framework gold standards;
- `hippocampus-react-typescript-engineering`;
- `hippocampus-architecture-patterns`;
- `hippocampus-testing-security`;
- `hippocampus-review-implementation`;
- `hippocampus-security-vulnerability-review`.

For backend work, apply the Java, Spring Boot, architecture/pattern, testing, review, and security skills together as applicable.

## Implementation Discipline

Before changing code:

1. Identify the tracker task.
2. Read only the authoritative documents relevant to that task.
3. Confirm phase scope and dependencies.
4. Plan before implementing when requested.
5. Do not implement later tracker tasks early.
6. Keep changes minimal and reviewable.
7. Run the tests required by the tracker task.
8. Record evidence before declaring completion.

## Architecture Discipline

Backend is a Spring Boot modular monolith.

Approved logical modules:

- `identity`
- `learning`
- `progress`
- `review`
- `materials`
- `rag`
- `ai`
- `shared`
- `bootstrap`

Where needed, feature internals follow:

- `api`
- `application`
- `domain`
- `port`
- `infrastructure`

Dependency direction:

`api -> application -> domain/ports <- infrastructure`

Do not let domain code depend on Spring MVC, JPA repositories, provider SDKs, HTTP clients, or other framework infrastructure.

## Java / Spring Boot Baseline

- Java 25.
- Spring Boot 4.1.x according to the version policy in Document 17.
- Maven.
- Constructor injection.
- Small explicit services/use cases.
- Prefer immutable values/records for DTOs, commands, results, and value carriers where semantics fit.
- Prefer explicit local variable types by default; `var` is compile-time type inference and has no runtime inference cost, but should be used only when the type remains obvious and readability improves.
- Prefer package-by-feature/module ownership over giant technical-layer packages.
- Keep Spring/framework annotations out of domain code where possible.
- Prefer typed `@ConfigurationProperties` for coherent configuration rather than scattered `@Value`.
- Keep controllers thin and backend authorization enforceable below the transport layer.
- Transactions belong primarily around application use cases and must account for Spring proxy semantics.
- Avoid holding database transactions open during AI/network calls.
- Do not expose JPA entities directly through APIs.
- Avoid `EAGER` as a default relationship strategy and do not rely on Open Session in View to hide persistence design problems.
- Use explicit validation and typed domain/application errors.
- Use Flyway for schema changes; never rely on production Hibernate auto-update.
- Keep provider-specific DTOs inside provider adapters.
- Prefer Spring Boot dependency management/BOM; do not override managed versions merely to chase newer releases.
- Preview Java features require explicit approval; permanent Java 25 language features may be used when they improve the design.
- Do not add Spring subsystems/features merely because the framework supports them; use only what solves approved requirements.

## Mandatory Security Gate

Security is both a design concern and an independent completion gate.

- Planning and implementation must follow secure-by-design practices.
- Required implementation tests must pass first.
- General implementation/code review must be completed.
- Then run `hippocampus-security-vulnerability-review` as an independent adversarial review.
- Use OWASP ASVS 5.0.0 as the verification baseline, with OWASP Top 10:2025 and OWASP API Security Top 10 as threat-oriented lenses.
- Critical and High findings block completion. Medium findings normally block completion unless a human explicitly documents risk acceptance. Agents must not silently accept security risk.
- Security findings require remediation, regression/security tests, rerun of affected tests, and security re-review.
- If a control cannot be adequately verified by the agent, return `MANUAL SECURITY REVIEW REQUIRED`; never invent a security guarantee.
- Do not claim an implementation is "secure from all vulnerabilities" or "OWASP compliant" without scoped evidence.

## Do Not Introduce Without Approved Decision

- microservices
- CQRS
- event sourcing
- Redis
- Kafka
- Kubernetes
- GraphQL
- reactive/WebFlux architecture
- JWT authentication replacing approved sessions
- dedicated vector database replacing PostgreSQL + pgvector
- undocumented external services
- undocumented MVP features

If a significant unresolved decision is discovered, stop and follow Document 27.

## Completion Rule

A task is not `Done` because code exists or tests are green.

`Done` requires:

- implementation exists;
- required tests pass;
- expected behavior is demonstrated;
- implementation/code review has no unresolved blocking findings;
- the independent security vulnerability review has passed or any required manual review/risk acceptance is explicitly documented;
- Definition of Done is satisfied;
- evidence is recorded;
- no undocumented architectural deviation remains.
