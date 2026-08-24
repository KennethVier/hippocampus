---
Audience: Solution architecture, backend, frontend, AI, data, DevOps,
  QA, security, and product contributors.
Authors: Project Hippocampus Team
Created: 2026-08-24
Document ID: 17
Last Updated: 2026-08-24
Owner: Project Hippocampus Team
Prerequisites:
- 00 - Project Vision
- 01 - Guiding Principles
- 02 - Problem Statement
- 03 - Educational Foundation
- 04 - Product Requirements
- 05 - User Personas
- 06 - User Journey & Learning Flow
- 07 - Feature Specifications
- 08 - Non-Functional Requirements
- 09 - MVP Scope & Roadmap
- 10 - AI Architecture v1.1+
- 11 - AI Learning Engine
- 12 - Prompt Engineering Strategy v1.0.1+
- 13 - RAG Architecture
- 14 - Knowledge Base Design
- 15 - AI Evaluation Strategy v1.0.1+
- 16 - System Architecture v1.1+
Purpose: Select and justify the concrete technology baseline for
  Hippocampus v1 and establish the initial Architecture Decision Record
  set that future implementation must follow.
Related Documents:
- 18 - Domain Model & Database Design
- 19 - Backend Architecture
- 20 - Frontend Architecture
- 21 - File Processing & Ingestion Architecture
- 22 - Security & Privacy Architecture
- 23 - Deployment & Infrastructure
- 24 - Observability & Operations
- 25 - Testing Strategy
- 26 - Development Roadmap & Implementation Phases
- 27 - Decision Log / ADR Index
Scope: Backend, frontend, AI provider integration, database, vector
  search, file processing, security baseline, background processing,
  testing, local development, build tooling, observability baseline,
  version policy, rejected alternatives, and ADR governance.
Status: Final
Title: Technology Stack & ADR Baseline
Version: 1.0.0
---

# 17 - Technology Stack & ADR Baseline

## 1. Purpose

This document converts the approved system architecture into a concrete
implementation baseline.

It answers:

> **Which technologies will Hippocampus v1 use, why are they
> appropriate, and which important alternatives are intentionally not
> being selected?**

The technology stack must implement Documents 00--16.

Technology choices must not redefine the product or educational model.

------------------------------------------------------------------------

# 2. Stack Selection Principles

The v1 stack should optimize for:

1.  Maintainability
2.  Strong Java/Spring compatibility
3.  Low infrastructure complexity
4.  Free-tier / low-cost operation where practical
5.  Strong relational consistency
6.  RAG capability without introducing unnecessary infrastructure
7.  Secure external AI-provider integration
8.  Straightforward local development
9.  Testability
10. Future replaceability of providers and infrastructure

The stack should be intentionally boring where novelty adds little
value.

------------------------------------------------------------------------

# 3. Locked v1 Stack Summary

  -----------------------------------------------------------------------
  Area                                v1 Baseline
  ----------------------------------- -----------------------------------
  Backend Language                    Java 25 LTS

  Backend Framework                   Spring Boot 4.1.x

  Core Framework                      Spring Framework 7.x via Spring
                                      Boot

  AI Framework                        Spring AI 2.0.x

  Build Tool                          Maven

  Web/API                             Spring MVC REST + Server-Sent
                                      Events where streaming is required

  Persistence ORM                     Spring Data JPA / Hibernate

  Database                            PostgreSQL 18.x

  Vector Search                       pgvector in PostgreSQL

  Lexical Search                      PostgreSQL Full-Text Search +
                                      `pg_trgm` where useful

  Database Migration                  Flyway

  Authentication                      Spring Security + server-side
                                      session cookie

  Session Persistence                 Spring Session JDBC

  External AI Providers               Ollama API + Google Gemini API

  Gemini Integration                  Spring AI Google GenAI / official
                                      Google GenAI Java SDK underneath

  Ollama Integration                  Backend provider adapter over
                                      Ollama Cloud REST API

  Embeddings                          Provider-abstracted; Gemini
                                      embedding API is the initial v1
                                      candidate, evaluation-gated

  File Parsing                        Apache Tika + Apache PDFBox

  OCR                                 Adapter boundary; exact engine
                                      selected in Document 21

  Binary Storage                      S3-compatible object-storage
                                      abstraction

  Background Processing               Database-backed processing jobs +
                                      Spring-managed worker executor

  Frontend                            React 19.2.x

  Frontend Language                   TypeScript 6.0.x

  Frontend Tooling                    Vite 8.1.x

  Styling                             Tailwind CSS 4.3.x

  Routing                             React Router 7.x

  Server State                        TanStack Query 5.x

  Client/UI State                     React state first; Zustand 5.x only
                                      for genuinely shared client state

  Forms                               React Hook Form + Zod

  Backend Testing                     JUnit 5, AssertJ, Mockito,
                                      Testcontainers

  Architecture Testing                ArchUnit; Spring Modulith
                                      verification may be adopted where
                                      useful

  Frontend Testing                    Vitest + React Testing Library

  End-to-End Testing                  Playwright

  Local Runtime                       Docker Compose

  CI Baseline                         GitHub Actions

  API Format                          JSON over HTTPS; multipart upload;
                                      SSE for streamed learning responses
  -----------------------------------------------------------------------

------------------------------------------------------------------------

# 4. Version Policy

This document locks **major/minor technology lines**, not every patch
forever.

Example:

``` text
Spring Boot 4.1.x
```

means:

> Use the latest tested security/bug-fix release in the Spring Boot 4.1
> line.

At the time of this document, Spring Boot 4.1.1 is available.

Likewise:

``` text
PostgreSQL 18.x
React 19.2.x
Vite 8.1.x
Tailwind CSS 4.3.x
```

should track compatible patch releases after normal regression testing.

Security patching must not require an ADR unless a patch changes
architecture or behavior materially.

------------------------------------------------------------------------

# 5. ADR-001 --- Java 25 LTS

## Decision

Use **Java 25 LTS** for Hippocampus v1.

## Rationale

Java 25 is the current Long-Term Support release and provides a stable
greenfield baseline.

Spring Boot 4.1 supports Java versions through Java 26, so Java 25 is
within the supported runtime range.

## Why Not Java 26?

Java 26 is newer but is not the current LTS line.

For a long-lived product baseline, the LTS release is preferable unless
a Java 26 feature becomes materially necessary.

## Why Not Java 21?

Java 21 remains a valid LTS version, but a greenfield 2026 application
benefits from beginning on the current LTS rather than intentionally
starting one LTS generation behind.

## Status

**ACCEPTED**

------------------------------------------------------------------------

# 6. ADR-002 --- Spring Boot 4.1.x

## Decision

Use **Spring Boot 4.1.x**.

## Rationale

Spring Boot 4.1 is the current stable 4.x minor line and is compatible
with Spring AI 2.0.

It provides the modern Spring Framework 7 generation and current
security, observability, HTTP-client, and dependency baselines.

## Patch Strategy

Always prefer the newest tested 4.1.x patch release.

Do not pin indefinitely to 4.1.0 merely because it was the initial GA.

## Status

**ACCEPTED**

------------------------------------------------------------------------

# 7. ADR-003 --- Spring AI 2.0.x

## Decision

Use **Spring AI 2.0.x** as the primary Java AI abstraction layer.

## Responsibilities

Spring AI may support:

-   Common chat model abstractions
-   Structured outputs
-   Prompt integration
-   Google GenAI integration
-   Embedding abstractions
-   Vector store integration where useful

Hippocampus still owns its own:

``` text
TypedAITask
ProviderRouter
ProviderAdapter
PromptTemplateRegistry
OutputValidation
LearningEngine
```

Spring AI must not become the educational architecture.

## Status

**ACCEPTED**

------------------------------------------------------------------------

# 8. ADR-004 --- Maven

## Decision

Use **Maven**.

## Rationale

-   Mature Spring ecosystem support
-   Straightforward BOM/dependency management
-   Strong CI compatibility
-   Familiar Java project structure
-   Minimal additional build-language complexity

Spring Boot and Spring AI dependency versions should be managed through
their supported BOM/parent mechanisms.

## Status

**ACCEPTED**

------------------------------------------------------------------------

# 9. ADR-005 --- Spring MVC as Primary Web Stack

## Decision

Use **Spring MVC** as the primary application web stack.

Use Server-Sent Events or controlled streaming integration for AI
responses where streaming improves the learning experience.

## Rationale

Most Hippocampus workloads are:

-   CRUD
-   Transactional learning state
-   File metadata
-   Study Mission state
-   Database-backed evidence

They do not require a fully reactive application architecture.

External AI calls may use asynchronous/streaming clients internally
without forcing the entire application into WebFlux.

## Rejected

Full end-to-end WebFlux as the default architecture.

## Reason

It adds cognitive and persistence complexity without a demonstrated v1
requirement.

## Status

**ACCEPTED**

------------------------------------------------------------------------

# 10. ADR-006 --- PostgreSQL 18.x

## Decision

Use **PostgreSQL 18.x** as the primary relational database.

## Responsibilities

PostgreSQL stores:

-   Users
-   Sessions
-   Subjects
-   Topics
-   Materials
-   Material versions
-   Document structure
-   Chunk metadata
-   Visual metadata
-   Study Missions
-   Attempts
-   Learning evidence
-   Review records
-   Prompt/model metadata
-   Background-job state

## Why PostgreSQL?

Hippocampus needs strong relational integrity more than it needs a
schemaless primary database.

The product contains many important relationships and provenance chains.

## Status

**ACCEPTED**

------------------------------------------------------------------------

# 11. ADR-007 --- PostgreSQL + pgvector for Vector Search

## Decision

Use the **pgvector** PostgreSQL extension for v1 vector storage/search.

## Rationale

For the initial approximately 40-user target:

``` text
PostgreSQL
+
pgvector
```

is materially simpler than introducing a dedicated vector database.

Benefits:

-   Same data-security boundary
-   Strong metadata filtering
-   Transactional metadata relationships
-   Simpler backup/restore
-   Fewer services
-   Easier local development

## Future Extraction

A dedicated vector/search system may be introduced if evaluation
demonstrates PostgreSQL is insufficient for:

-   Retrieval latency
-   Corpus size
-   Advanced multimodal search
-   Scale
-   Ranking capability

## Status

**ACCEPTED**

------------------------------------------------------------------------

# 12. ADR-008 --- PostgreSQL Hybrid Retrieval

## Decision

Use:

``` text
pgvector semantic similarity
+
PostgreSQL full-text search
+
pg_trgm / lexical similarity where useful
+
metadata filters
```

as the v1 hybrid-retrieval baseline.

## Rationale

Medical learning contains both semantic and exact lexical signals.

Examples:

``` text
C5-T1
CN VII
Na+
β1
IL-6
posterior cord
```

A semantic-only vector strategy is insufficient as the architectural
baseline.

## Rejected for v1

-   Elasticsearch
-   OpenSearch
-   Dedicated search cluster

## Status

**ACCEPTED**

------------------------------------------------------------------------

# 13. ADR-009 --- Spring Data JPA / Hibernate

## Decision

Use **Spring Data JPA with Hibernate** for the primary relational
domain.

## Rationale

Hippocampus contains many aggregate relationships and transactional
operations.

JPA is appropriate for:

-   User/topic/material metadata
-   Learning state
-   Evidence
-   Review
-   Generated-artifact metadata

## Boundary

Do not force vector retrieval and specialized search queries through
awkward ORM abstractions.

Native SQL / repository-level specialized queries are acceptable for:

-   pgvector
-   FTS
-   `pg_trgm`
-   ranking
-   batch operations

## Status

**ACCEPTED**

------------------------------------------------------------------------

# 14. ADR-010 --- Flyway for Database Migrations

## Decision

Use **Flyway**.

Every schema change must be versioned.

The application must not rely on Hibernate automatic schema mutation in
production.

Recommended production behavior:

``` text
ddl-auto = validate
```

rather than:

``` text
ddl-auto = update
```

## Status

**ACCEPTED**

------------------------------------------------------------------------

# 15. ADR-011 --- Dual External AI Providers

## Decision

Use:

``` text
Ollama API
+
Google Gemini API
```

through the server-side AI Provider Router.

## Rules

-   Both API keys remain server-side.
-   Browser code never calls either provider directly.
-   Providers do not own Learning Engine state.
-   Provider fallback preserves grounding and output contracts.
-   Routing is evaluation-driven.

## Status

**ACCEPTED**

------------------------------------------------------------------------

# 16. ADR-012 --- Gemini Integration through Spring AI Google GenAI

## Decision

Use Spring AI's **Google GenAI** integration as the primary Gemini Java
integration.

Spring AI's implementation uses Google's current GenAI Java SDK
underneath.

## Benefits

-   Compatible Spring model abstraction
-   Gemini Developer API key support
-   Multimodal support
-   Structured integration with Spring
-   Easier provider normalization

## Boundary

Hippocampus's Provider Adapter remains the application contract.

Application code outside the adapter should not depend directly on
Gemini-specific model classes.

## Status

**ACCEPTED**

------------------------------------------------------------------------

# 17. ADR-013 --- Ollama Cloud Integration through Dedicated Provider Adapter

## Decision

Use a dedicated backend **OllamaCloudProviderAdapter** against Ollama's
external API.

Conceptually:

``` text
OllamaCloudProviderAdapter
   ↓
HTTPS
   ↓
https://ollama.com/api
```

with:

``` text
Authorization: Bearer <OLLAMA_API_KEY>
```

## Why a Dedicated Adapter?

Ollama Cloud's authentication and remote API behavior are
external-provider concerns.

The rest of Hippocampus should not know:

-   Base URL
-   Authorization header
-   Provider model naming
-   Provider-specific error formats

## Implementation Baseline

Use Spring's supported HTTP client facilities inside the adapter.

Spring AI's portable model types may be used where they cleanly fit, but
provider-specific cloud authentication must not leak into Learning
Engine code.

## Status

**ACCEPTED**

------------------------------------------------------------------------

# 18. ADR-014 --- Embedding Provider Is Replaceable

## Decision

Embedding is an independent provider role.

Initial v1 implementation should begin by evaluating the **Gemini
embedding API**, with `gemini-embedding-2` as a current candidate.

## Why Not Permanently Lock It Here?

Documents 13 and 15 require embedding selection to be
retrieval-evaluation driven.

Changing an embedding model requires:

``` text
New IndexGeneration
↓
Re-embedding
↓
Retrieval regression test
↓
Activation
```

## Rule

Never mix incompatible embedding generations.

## Status

**ACCEPTED --- MODEL SELECTION EVALUATION-GATED**

------------------------------------------------------------------------

# 19. ADR-015 --- S3-Compatible Binary Storage Abstraction

## Decision

Original files and extracted visual assets use an **S3-compatible
object-storage abstraction**.

## Production Provider

Not locked in Document 17.

Deployment/provider selection belongs to Document 23.

## Local Development

A local S3-compatible service or local filesystem adapter may be used.

## Why Not Store Large PDFs in PostgreSQL?

Binary learning materials should not inflate transactional database
storage unnecessarily.

The database stores metadata and object references.

## Status

**ACCEPTED**

------------------------------------------------------------------------

# 20. ADR-016 --- Apache Tika + Apache PDFBox

## Decision

Use:

``` text
Apache Tika
+
Apache PDFBox
```

as baseline Java libraries for document/media inspection and PDF
extraction.

## Tika

Useful for:

-   MIME detection
-   Metadata extraction
-   General document parsing
-   Input-type inspection

## PDFBox

Useful for:

-   Page-level PDF access
-   Text extraction
-   Embedded image handling
-   Layout/page operations

## OCR

OCR is behind an adapter and is not permanently selected here.

Document 21 will define the exact OCR strategy.

## Status

**ACCEPTED**

------------------------------------------------------------------------

# 21. ADR-017 --- Database-Backed Background Jobs

## Decision

Use a **database-backed processing-job model with Spring-managed worker
execution** for v1.

Conceptually:

``` text
processing_job
   ↓
Worker Poll / Claim
   ↓
Process
   ↓
Progress
   ↓
Complete / Retry / Fail
```

## Requirements

Jobs must support:

-   Durable state
-   Retry count
-   Idempotency
-   Progress
-   Failure reason
-   Priority
-   Cancellation where practical

## Rejected for v1

-   Kafka
-   RabbitMQ
-   Pulsar

## Rationale

Approximately 40 users do not justify a distributed messaging platform
yet.

## Status

**ACCEPTED**

------------------------------------------------------------------------

# 22. ADR-018 --- No Redis in Initial MVP Baseline

## Decision

Redis is **not required** for initial MVP architecture.

## Rationale

Current requirements can use:

-   PostgreSQL sessions
-   PostgreSQL job state
-   In-process bounded caches
-   Application concurrency gates

Introducing Redis now creates another operational dependency.

## Future

Redis may be introduced if measured needs emerge for:

-   Distributed caching
-   Distributed locks
-   Rate limiting across multiple app instances
-   High-frequency ephemeral state

## Status

**ACCEPTED**

------------------------------------------------------------------------

# 23. ADR-019 --- Spring Security + Server-Side Sessions

## Decision

Use:

``` text
Spring Security
+
Secure HttpOnly Session Cookie
+
Spring Session JDBC
```

for the web MVP.

## Rationale

For a browser-focused first-party application, server-side sessions
provide:

-   Simple revocation
-   No frontend token storage
-   Lower JWT complexity
-   Centralized authorization

## Cookie Requirements

Production cookies must be configured appropriately for:

-   HTTPS
-   HttpOnly
-   Secure
-   SameSite policy
-   CSRF design

Detailed security behavior belongs to Document 22.

## Rejected Baseline

Custom JWT access/refresh-token architecture.

It can be revisited for native/mobile/external API clients.

## Status

**ACCEPTED**

------------------------------------------------------------------------

# 24. ADR-020 --- React 19.2.x

## Decision

Use **React 19.2.x**.

## Rationale

React remains appropriate for the interactive Study Mission experience:

-   Streaming feedback
-   Upload state
-   Visual learning
-   Mission progression
-   Rich forms
-   Responsive educational components

## Status

**ACCEPTED**

------------------------------------------------------------------------

# 25. ADR-021 --- TypeScript 6.0.x

## Decision

Use **TypeScript 6.0.x** with strict type checking.

TypeScript 7 is still a beta/native-transition line at the time of this
baseline and should not be used for MVP production until stable and
evaluated.

## Recommended Baseline

``` text
strict: true
```

Avoid `any` as a default escape mechanism.

## Status

**ACCEPTED**

------------------------------------------------------------------------

# 26. ADR-022 --- Vite 8.1.x

## Decision

Use **Vite 8.1.x**.

## Rationale

-   Current stable modern build tooling
-   Strong React support
-   Fast development
-   Straightforward static frontend deployment
-   No need for server-side React rendering in MVP

## Status

**ACCEPTED**

------------------------------------------------------------------------

# 27. ADR-023 --- React SPA, Not Next.js

## Decision

Use a **client-rendered React application** built with Vite.

## Why?

Hippocampus is primarily an authenticated application.

SEO/SSR is not a primary product requirement.

Spring Boot is already the backend.

Using Next.js would introduce a second application server/runtime and
blur backend responsibilities.

## Rejected

Next.js as the v1 application architecture.

## Status

**ACCEPTED**

------------------------------------------------------------------------

# 28. ADR-024 --- Tailwind CSS 4.3.x

## Decision

Use **Tailwind CSS 4.3.x**.

## Rationale

-   Fast implementation
-   Token-friendly design system
-   Responsive utilities
-   Modern CSS support
-   Good fit for focused Study Mission interfaces

The UX must remain guided and uncluttered regardless of framework.

## Status

**ACCEPTED**

------------------------------------------------------------------------

# 29. ADR-025 --- React Router 7.x

## Decision

Use **React Router 7.x** for client routing.

Primary route contexts may include:

``` text
Subjects
Topics
Material
Study Mission
Progress
Review
Settings
```

Route count must not become equivalent to feature count.

The guided-learning principle remains authoritative.

## Status

**ACCEPTED**

------------------------------------------------------------------------

# 30. ADR-026 --- TanStack Query 5.x

## Decision

Use **TanStack Query 5.x** for server-state synchronization.

Use it for:

-   API caching
-   Query invalidation
-   Loading/error states
-   Refetch
-   Mutation state

Do not duplicate server state into a global client store without reason.

## Status

**ACCEPTED**

------------------------------------------------------------------------

# 31. ADR-027 --- Minimal Client Global State

## Decision

Prefer:

``` text
React local state
+
URL state
+
TanStack Query server state
```

before adding global state.

Use **Zustand 5.x** only for cross-cutting UI/session state that is
genuinely client-owned.

Possible examples:

-   Active transient mission UI state
-   Global upload drawer
-   Non-persistent interface preference

## Status

**ACCEPTED**

------------------------------------------------------------------------

# 32. ADR-028 --- React Hook Form + Zod

## Decision

Use:

``` text
React Hook Form
+
Zod
```

for frontend form state and validation.

Backend validation remains authoritative.

Frontend validation improves UX; it does not replace server validation.

## Status

**ACCEPTED**

------------------------------------------------------------------------

# 33. ADR-029 --- REST + JSON + SSE

## Decision

Primary API style:

``` text
REST
JSON
Multipart uploads
Server-Sent Events for streamed responses
```

## Why SSE?

Most AI streaming in Hippocampus is:

``` text
Server → Browser
```

rather than bidirectional realtime communication.

SSE is simpler than WebSocket for this requirement.

## WebSocket

Deferred until a genuine bidirectional realtime feature requires it.

## Status

**ACCEPTED**

------------------------------------------------------------------------

# 34. ADR-030 --- No GraphQL for MVP

## Decision

Use REST rather than GraphQL.

## Rationale

-   Smaller API surface complexity
-   Easier observability
-   Easier Spring Security rules
-   Easier caching/debugging
-   MVP does not demonstrate a GraphQL-specific need

## Status

**ACCEPTED**

------------------------------------------------------------------------

# 35. ADR-031 --- Backend Testing Stack

## Decision

Use:

-   JUnit 5
-   AssertJ
-   Mockito
-   Spring Boot Test
-   Testcontainers
-   ArchUnit

## Testcontainers

Use real ephemeral dependencies where behavior matters:

-   PostgreSQL
-   pgvector-capable PostgreSQL image
-   File-storage adapter where practical

Mocks should not replace meaningful integration tests for
persistence/RAG behavior.

## Status

**ACCEPTED**

------------------------------------------------------------------------

# 36. ADR-032 --- Frontend Testing Stack

## Decision

Use:

``` text
Vitest
React Testing Library
Playwright
```

Roles:

-   Vitest → unit tests
-   React Testing Library → component behavior
-   Playwright → end-to-end user journeys

Tests should prioritize learner behavior over implementation detail.

## Status

**ACCEPTED**

------------------------------------------------------------------------

# 37. ADR-033 --- Docker Compose for Local Development

## Decision

Use Docker Compose for local infrastructure.

Typical local stack:

``` text
PostgreSQL + pgvector
Optional S3-compatible local storage
Backend
Frontend
```

External Ollama API and Gemini API are accessed with development API
keys.

No local Ollama runtime is required by the v1 architecture.

## Status

**ACCEPTED**

------------------------------------------------------------------------

# 38. ADR-034 --- GitHub Actions CI Baseline

## Decision

Use GitHub Actions for CI unless repository-hosting constraints later
require another platform.

Initial pipeline:

``` text
Checkout
 ↓
Backend compile/test
 ↓
Frontend lint/typecheck/test
 ↓
Integration tests
 ↓
Build artifacts
```

Security scanning and deployment stages are expanded in Documents
22/23/25.

## Status

**ACCEPTED**

------------------------------------------------------------------------

# 39. ADR-035 --- Observability Baseline

## Decision

Use Spring Boot Actuator and structured application logging as the base.

The architecture must expose metrics for:

-   HTTP requests
-   AI provider latency
-   AI provider failure
-   Rate limits
-   Quota exhaustion
-   Retrieval latency
-   Material processing
-   Background jobs
-   Validation failures

Exact telemetry backend is deferred to Document 24.

## Status

**ACCEPTED**

------------------------------------------------------------------------

# 40. ADR-036 --- Configuration and Secrets

## Decision

Configuration is externalized using environment-specific configuration.

Secrets include:

``` text
OLLAMA_API_KEY
GEMINI_API_KEY
DATABASE_PASSWORD
OBJECT_STORAGE_SECRET
SESSION_SECRET / security configuration
```

Secrets must never be committed to source control.

Production secret storage is defined in Document 23.

## Status

**ACCEPTED**

------------------------------------------------------------------------

# 41. AI Provider Integration View

``` mermaid
flowchart TD

A[Learning Engine]
--> B[Typed AI Task]

B --> C[AI Orchestrator]
C --> D[Provider Router]

D --> E[Gemini Provider Adapter]
D --> F[Ollama Cloud Provider Adapter]

E --> G[Spring AI Google GenAI]
G --> H[Gemini API]

F --> I[Spring HTTP Client]
I --> J[Ollama API]

H --> K[Normalized AI Result]
J --> K

K --> L[Output Validator]
L --> M[Learning Engine]
```

------------------------------------------------------------------------

# 42. Data / Retrieval Technology View

``` mermaid
flowchart LR

A[Material]
--> B[Apache Tika / PDFBox]

B --> C[Structured Evidence]
C --> D[Chunks]

D --> E[Embedding Provider]
E --> F[(PostgreSQL 18 + pgvector)]

D --> G[(PostgreSQL FTS / pg_trgm)]

H[Retrieval Intent]
--> F
H --> G

F --> I[Candidate Evidence]
G --> I

I --> J[Hybrid Rank / Deduplicate]
J --> K[Evidence Package]
```

------------------------------------------------------------------------

# 43. Frontend Technology View

``` mermaid
flowchart TD

A[React 19.2]
--> B[TypeScript 6]

A --> C[React Router 7]
A --> D[TanStack Query 5]
A --> E[React Hook Form + Zod]
A --> F[Minimal Zustand 5]
A --> G[Tailwind CSS 4.3]

H[Vite 8.1]
--> A
```

------------------------------------------------------------------------

# 44. Backend Technology View

``` mermaid
flowchart TD

A[Java 25 LTS]
--> B[Spring Boot 4.1.x]

B --> C[Spring MVC]
B --> D[Spring Security]
B --> E[Spring Data JPA]
B --> F[Spring Session JDBC]
B --> G[Spring AI 2.0]
B --> H[Spring Actuator]

E --> I[PostgreSQL 18]
I --> J[pgvector]
I --> K[FTS / pg_trgm]

L[Flyway]
--> I
```

------------------------------------------------------------------------

# 45. Technologies Explicitly Not in v1 Baseline

The following are not selected unless a later ADR supersedes this
decision:

-   Microservices
-   Kubernetes
-   Kafka
-   RabbitMQ
-   Redis
-   Elasticsearch/OpenSearch
-   Dedicated vector database
-   Graph database
-   GraphQL
-   Next.js
-   WebSocket as default transport
-   Local Ollama runtime
-   Browser-to-AI-provider calls
-   JWT-first browser authentication
-   Event sourcing
-   CQRS

This is deliberate scope control.

------------------------------------------------------------------------

# 46. Free-Tier / Cost Implications

Technology selection should minimize required paid infrastructure.

However:

> **Free tier is an operating preference, not an architectural
> guarantee.**

Ollama and Gemini quotas may change.

Hosting/storage providers may change.

The application must therefore depend on abstractions rather than
free-tier-specific behavior.

Document 23 will select actual deployment providers after evaluating:

-   Free quotas
-   Storage capacity
-   Database requirements
-   Region
-   Reliability
-   Upgrade path

------------------------------------------------------------------------

# 47. Technology Upgrade Policy

## Patch Updates

Normally allowed after tests.

## Minor Updates

Require:

-   Compatibility review
-   Regression test
-   Changelog review

## Major Updates

Require a new ADR or explicit superseding decision.

## AI Models

Any material model/provider change is evaluation-gated under Document 15
even if no code dependency changes.

------------------------------------------------------------------------

# 48. ADR Status Vocabulary

Use:

``` text
PROPOSED
ACCEPTED
SUPERSEDED
REJECTED
DEPRECATED
```

Document 17 contains the initial accepted baseline.

Document 27 will become the living ADR index.

------------------------------------------------------------------------

# 49. ADR Change Rule

A technology decision should receive a new/superseding ADR when changing
it would materially alter:

-   Architecture
-   Operational burden
-   Security model
-   Data model
-   Provider lock-in
-   Development workflow
-   Product reliability

Example:

``` text
PostgreSQL + pgvector
        ↓
Dedicated Qdrant Cluster
```

requires an ADR.

Updating:

``` text
PostgreSQL 18.5 → 18.6
```

normally does not.

------------------------------------------------------------------------

# 50. Compatibility Matrix

  -----------------------------------------------------------------------
  Component                           Baseline Relationship
  ----------------------------------- -----------------------------------
  Java 25                             Supported by Spring Boot 4.1

  Spring Boot 4.1.x                   Compatible with Spring AI 2.0.x

  Spring AI 2.0.x                     Supports Google GenAI and unified
                                      model abstractions

  Gemini API                          Accessed server-side using API key

  Ollama API                          Accessed server-side using bearer
                                      API key

  PostgreSQL 18                       Primary structured persistence

  pgvector                            Vector index inside PostgreSQL

  React 19.2                          Browser UI

  TypeScript 6                        Frontend type system

  Vite 8.1                            React build/dev tool
  -----------------------------------------------------------------------

------------------------------------------------------------------------

# 51. External Technical References

Reference baseline verified on **2026-08-24**.

Official references:

-   Oracle Java downloads / support lines:
    https://www.oracle.com/java/technologies/downloads/
-   Spring Boot system requirements:
    https://docs.spring.io/spring-boot/system-requirements.html
-   Spring Boot releases: https://spring.io/blog/
-   Spring AI 2.0 reference: https://docs.spring.io/spring-ai/reference/
-   Spring AI Google GenAI:
    https://docs.spring.io/spring-ai/reference/api/chat/google-genai-chat.html
-   Gemini Java SDK: https://ai.google.dev/gemini-api/docs/libraries
-   Gemini embeddings: https://ai.google.dev/gemini-api/docs/embeddings
-   Ollama Cloud API: https://docs.ollama.com/cloud
-   Ollama authentication: https://docs.ollama.com/api/authentication
-   PostgreSQL: https://www.postgresql.org/
-   React: https://react.dev/
-   Vite: https://vite.dev/
-   TypeScript: https://www.typescriptlang.org/
-   Tailwind CSS: https://tailwindcss.com/

------------------------------------------------------------------------

# 52. Locked v1 Technology Decisions

The following baseline is now approved:

1.  Java 25 LTS.
2.  Spring Boot 4.1.x.
3.  Spring AI 2.0.x.
4.  Maven.
5.  Spring MVC REST backend.
6.  SSE for AI streaming where required.
7.  PostgreSQL 18.x.
8.  pgvector for vector search.
9.  PostgreSQL FTS + pg_trgm for hybrid lexical search.
10. Spring Data JPA/Hibernate for relational domain persistence.
11. Native/specialized SQL allowed for retrieval and vector operations.
12. Flyway owns schema migration.
13. Ollama API and Gemini API are the external AI providers.
14. Gemini integration uses Spring AI Google GenAI.
15. Ollama Cloud uses a dedicated backend provider adapter.
16. Provider credentials are server-only.
17. Embedding model selection remains evaluation-gated.
18. Gemini embedding API is the initial embedding candidate.
19. Binary materials use an S3-compatible storage abstraction.
20. Apache Tika + PDFBox are the baseline document-processing libraries.
21. OCR remains behind an adapter pending Document 21.
22. Background processing uses durable database-backed jobs; no broker
    initially.
23. Redis is not required for v1.
24. Spring Security + Spring Session JDBC is the browser authentication
    baseline.
25. React 19.2.x.
26. TypeScript 6.0.x.
27. Vite 8.1.x.
28. Tailwind CSS 4.3.x.
29. React Router 7.x.
30. TanStack Query 5.x.
31. Zustand is optional/minimal rather than the default store for
    everything.
32. React Hook Form + Zod.
33. REST + JSON + multipart + SSE.
34. No GraphQL in v1.
35. JUnit/AssertJ/Mockito/Testcontainers/ArchUnit for backend testing.
36. Vitest/React Testing Library/Playwright for frontend/E2E testing.
37. Docker Compose for local infrastructure.
38. GitHub Actions as CI baseline.
39. Spring Actuator + structured logs as observability baseline.
40. Major technology changes require ADRs.
41. Microservices/Kubernetes/Kafka/RabbitMQ/Redis/dedicated vector DB
    are intentionally deferred.
42. The technology stack must remain subordinate to the educational
    architecture in Documents 00--16.

------------------------------------------------------------------------

# 53. Out of Scope

Document 17 does not yet define:

-   Exact database tables
-   Exact package structure
-   Exact REST endpoints
-   Exact deployment provider
-   Exact S3-compatible provider
-   Exact OCR engine
-   Exact Gemini chat model
-   Exact Ollama cloud model
-   Exact embedding dimensions
-   Exact background worker thread count
-   Exact authentication flows/UI
-   Exact production monitoring vendor
-   Exact CI/CD deployment pipeline

These belong to Documents 18--25 or are evaluation-driven.

------------------------------------------------------------------------

# 54. Next Document

**18 - Domain Model & Database Design**

The next document should convert Documents 14, 16, and 17 into the
concrete relational/domain model.

It should define:

-   Aggregate boundaries
-   Entity ownership
-   Tables/entities
-   Primary keys
-   Foreign keys
-   Material/version hierarchy
-   Topic-material mappings
-   Chunks
-   Visual assets
-   Vector records
-   Study Missions
-   Activities
-   Attempts
-   Learning evidence
-   Review records
-   Generated artifacts
-   Background jobs
-   Sessions/auth-related persistence
-   Indexes
-   Deletion behavior
-   Migration rules

It must preserve:

> **Material ≠ Topic**

and:

> **Learning evidence remains application-owned and traceable to
> meaningful student activity.**

------------------------------------------------------------------------

# 55. Revision History

  -----------------------------------------------------------------------------
  Version           Date              Author            Changes
  ----------------- ----------------- ----------------- -----------------------
  1.0.0             2026-08-24        Project           Initial finalized
                                      Hippocampus Team  Technology Stack & ADR
                                                        Baseline selecting Java
                                                        25, Spring Boot 4.1,
                                                        Spring AI 2.0,
                                                        PostgreSQL 18 +
                                                        pgvector, dual external
                                                        Ollama/Gemini
                                                        providers,
                                                        React/TypeScript/Vite
                                                        frontend, and
                                                        intentionally minimal
                                                        MVP infrastructure

  -----------------------------------------------------------------------------

------------------------------------------------------------------------

# 56. Approval

**Status:** Final

**Reviewed By:** Project Hippocampus Team

**Approved By:** Project Hippocampus Team
