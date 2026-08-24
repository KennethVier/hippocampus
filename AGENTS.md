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
7. Code.

Code never silently overrides a higher authority.

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

Do not let domain code depend on Spring MVC, JPA repositories, provider SDKs, or HTTP clients.

## Java Baseline

- Java 25.
- Spring Boot 4.1.x according to the version policy in Document 17.
- Maven.
- Constructor injection.
- Small explicit services.
- Prefer immutable values/records for DTOs and commands where appropriate.
- Prefer package-by-feature/module ownership over giant technical-layer packages.
- Transactions belong primarily around application use cases.
- Avoid holding database transactions open during AI/network calls.
- Do not expose JPA entities directly through APIs.
- Avoid `EAGER` as a default relationship strategy.
- Use explicit validation and typed domain/application errors.
- Use Flyway for schema changes; never rely on production Hibernate auto-update.
- Keep provider-specific DTOs inside provider adapters.

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

A task is not `Done` because code exists.

`Done` requires:

- implementation exists;
- required tests pass;
- expected behavior is demonstrated;
- Definition of Done is satisfied;
- evidence is recorded;
- no undocumented architectural deviation remains.
