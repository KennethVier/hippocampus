---
Audience: Backend, architecture, AI, QA, security, and DevOps
  contributors.
Authors: Project Hippocampus Team
Created: 2026-08-24
Document ID: 19
Last Updated: 2026-08-24
Owner: Project Hippocampus Team
Prerequisites:
- 10 - AI Architecture v1.1+
- 11 - AI Learning Engine
- 12 - Prompt Engineering Strategy v1.0.1+
- 13 - RAG Architecture
- 14 - Knowledge Base Design
- 15 - AI Evaluation Strategy v1.0.1+
- 16 - System Architecture v1.1+
- 17 - Technology Stack & ADR Baseline
- 18 - Domain Model & Database Design
Purpose: Define the Spring Boot backend architecture for Hippocampus v1,
  including module boundaries, layering, application services, domain
  logic, repositories, AI provider integration, RAG, background
  processing, transactions, streaming, validation, error handling, and
  implementation constraints.
Related Documents:
- 20 - Frontend Architecture
- 21 - File Processing & Ingestion Architecture
- 22 - Security & Privacy Architecture
- 23 - Deployment & Infrastructure
- 24 - Observability & Operations
- 25 - Testing Strategy
- 26 - Development Roadmap & Implementation Phases
- 27 - Decision Log / ADR Index
Scope: Spring Boot modular-monolith structure, package/module ownership,
  controllers, use cases, domain services, persistence adapters, AI
  provider router, Ollama/Gemini adapters, Learning Engine, RAG
  services, background jobs, SSE, concurrency, idempotency, validation,
  security integration, observability hooks, and testing seams.
Status: Final
Title: Backend Architecture
Version: 1.0.0
---

# 19 - Backend Architecture

## 1. Purpose

This document defines how the Hippocampus v1 backend should be
implemented in Spring Boot.

It answers:

> **How should backend code be organized so that learning logic, AI
> providers, RAG, persistence, security, background work, and APIs
> remain explicit, testable, and replaceable?**

The backend is the application authority.

It owns:

-   Learning state
-   Study Mission progression
-   Review logic
-   Source authorization
-   RAG scope
-   AI task selection
-   AI provider routing
-   Persistence integrity
-   Validation
-   Error/failure behavior

------------------------------------------------------------------------

# 2. Locked Backend Principle

> **The backend must encode Hippocampus as a learning system, not as a
> collection of controllers that call an LLM.**

The anti-pattern is:

``` text
Controller
  ↓
Prompt
  ↓
LLM
  ↓
Response
```

The intended architecture is:

``` text
Controller
  ↓
Application Use Case
  ↓
Domain / Learning Engine
  ↓
Required Port
  ├── Repository
  ├── RAG
  ├── AI
  └── Background Work
  ↓
Infrastructure Adapter
```

------------------------------------------------------------------------

# 3. Architectural Style

The backend uses a **modular monolith** with explicit module ownership
and layered internals.

Recommended dependency direction:

``` text
API / Web
   ↓
Application
   ↓
Domain
   ↓
Ports
   ↓
Infrastructure Adapters
```

Infrastructure may depend on domain/application contracts.

Domain logic must not depend on Spring controllers, JPA repositories,
HTTP clients, Gemini classes, or Ollama-specific DTOs.

------------------------------------------------------------------------

# 4. Recommended Top-Level Modules

``` text
com.hippocampus
├── identity
├── learning
├── progress
├── review
├── materials
├── rag
├── ai
├── shared
└── bootstrap
```

These are logical module boundaries, not microservices.

------------------------------------------------------------------------

# 5. Module Responsibilities

## identity

Owns:

-   Current user identity
-   Authentication integration
-   Authorization helpers
-   Ownership validation

## learning

Owns:

-   StudyMission
-   LearningObjective
-   LearningActivity
-   Learning Engine
-   Mission transitions
-   Time-aware activity decisions
-   Activity orchestration

## progress

Owns:

-   StudentAttempt
-   EvidenceEvent
-   LearningEvidence
-   MisconceptionEvidence
-   ReflectionEvidence
-   Evidence aggregation

## review

Owns:

-   ReviewRecord
-   Review eligibility
-   Review priority
-   Review rationale
-   Review mission creation

## materials

Owns:

-   Material
-   MaterialVersion
-   DocumentNode
-   TextBlock
-   Chunk metadata
-   VisualAsset
-   MaterialTopicLink
-   SourceReference
-   Processing status

## rag

Owns:

-   RetrievalIntent
-   RetrievalScope
-   Hybrid retrieval
-   Reranking
-   EvidencePackage
-   Context assembly

## ai

Owns:

-   Typed AI tasks
-   Prompt registry
-   Prompt context builder coordination
-   Provider router
-   Gemini adapter
-   Ollama adapter
-   Output validation
-   Repair/retry
-   AI request records
-   Provider usage records

## shared

Contains only genuinely cross-cutting primitives.

Examples:

-   IDs
-   clock abstraction
-   pagination types
-   domain errors
-   common audit timestamps

Avoid turning `shared` into a dumping ground.

------------------------------------------------------------------------

# 6. Internal Module Structure

A module may use:

``` text
module/
├── api/
├── application/
├── domain/
├── port/
└── infrastructure/
```

Example:

``` text
learning/
├── api/
│   └── StudyMissionController
├── application/
│   ├── StartStudyMissionUseCase
│   ├── SubmitActivityResponseUseCase
│   └── ResumeStudyMissionUseCase
├── domain/
│   ├── StudyMission
│   ├── LearningActivity
│   ├── LearningEngine
│   └── MissionPolicy
├── port/
│   ├── StudyMissionRepository
│   ├── LearningEvidencePort
│   ├── RetrievalPort
│   └── AiTaskPort
└── infrastructure/
    ├── JpaStudyMissionRepository
    └── ...
```

------------------------------------------------------------------------

# 7. API Layer

Controllers should be thin.

Responsibilities:

-   Parse request
-   Resolve authenticated user
-   Validate transport-level input
-   Call one application use case
-   Map result to API DTO
-   Map known exceptions to HTTP errors

Controllers should not:

-   Implement Learning Engine rules
-   Build prompts
-   Call Gemini/Ollama directly
-   Query pgvector directly
-   Update multiple aggregates manually
-   Decide evidence state

------------------------------------------------------------------------

# 8. Application Use Cases

Application services coordinate one user intention.

Examples:

``` text
CreateSubjectUseCase
CreateTopicUseCase
UploadMaterialUseCase
StartStudyMissionUseCase
SubmitActivityResponseUseCase
RequestAlternativeExplanationUseCase
ResumeStudyMissionUseCase
StartReviewUseCase
DeleteMaterialUseCase
```

A use case may coordinate multiple domain services and ports but should
preserve transaction boundaries.

------------------------------------------------------------------------

# 9. Domain Layer

Domain logic should contain the rules that make Hippocampus Hippocampus.

Examples:

-   Mission state transitions
-   Activity eligibility
-   Retrieval-before-reteaching
-   Retry/scaffolding progression
-   Review eligibility logic
-   Evidence aggregation rules
-   Anti-repetition rules
-   Source-mode rules
-   Time-aware scope rules

This logic should be testable without Spring Boot starting.

------------------------------------------------------------------------

# 10. Learning Engine Placement

The Learning Engine lives inside the `learning.domain` boundary.

Conceptually:

``` text
LearningEvent
+
MissionState
+
LearningEvidenceSnapshot
+
TimeContext
+
SourceCapability
       ↓
LearningEngine
       ↓
NextLearningAction
```

It returns typed actions.

Examples:

``` text
PRESENT_EXPLANATION
ASK_RETRIEVAL
PRESENT_CONNECTION
PRESENT_APPLICATION
GIVE_FEEDBACK
REQUEST_REFLECTION
COMPLETE_MISSION
```

It does not return raw provider prompts.

------------------------------------------------------------------------

# 11. NextLearningAction Contract

Conceptually:

``` text
NextLearningAction
├── actionType
├── learningObjectiveId
├── conceptKey
├── difficulty
├── aiTaskRequired
├── groundingMode
├── retrievalScope
├── rationaleCode
└── constraints
```

The Application layer interprets this action and invokes RAG/AI only
when required.

------------------------------------------------------------------------

# 12. Domain Ports

The Learning Engine and application layer should depend on ports such
as:

``` text
StudyMissionRepository
LearningEvidenceRepository
ReviewRepository
MaterialRepository
RetrievalPort
AiTaskPort
ClockPort
BackgroundJobPort
```

Infrastructure implements these ports.

------------------------------------------------------------------------

# 13. Persistence Adapters

Use Spring Data JPA/Hibernate for relational persistence.

Infrastructure adapters may combine:

-   Spring Data repository
-   Entity mapper
-   Specialized native query repository

Example:

``` text
StudyMissionRepository (domain port)
      ↓
JpaStudyMissionRepositoryAdapter
      ↓
SpringDataStudyMissionJpaRepository
```

Do not expose Spring Data interfaces directly into domain logic.

------------------------------------------------------------------------

# 14. JPA Entity Boundary

JPA entities may be persistence models rather than identical domain
objects.

For complex aggregates, separate:

``` text
Domain Model
↔
Persistence Entity
```

This prevents JPA lifecycle concerns from dictating domain design.

For simple CRUD metadata, pragmatic mapping may be used.

------------------------------------------------------------------------

# 15. Transaction Boundary

Transactions belong primarily at application-use-case boundaries.

Examples:

## Submit Activity Response

One transaction may persist:

``` text
StudentAttempt
+
EvidenceEvent
+
Updated LearningEvidence
+
Updated StudyMission state
```

only after AI evaluation has been validated if AI is required.

## Material Version Activation

One transaction may:

``` text
Activate new MaterialVersion
+
Update Material.activeVersionId
+
Deactivate old retrieval eligibility
```

------------------------------------------------------------------------

# 16. External Calls and Transactions

Do not hold a database transaction open while waiting for Gemini or
Ollama unless unavoidable.

Preferred flow:

``` text
Load State
↓
Commit/End Read Transaction
↓
Call RAG / AI
↓
Validate
↓
Open Short Write Transaction
↓
Persist Result
```

Use optimistic concurrency/version checks before the write to ensure
state has not changed.

------------------------------------------------------------------------

# 17. Submit Response Flow

``` mermaid
sequenceDiagram
    actor Student
    participant API as Controller
    participant UC as SubmitResponseUseCase
    participant Engine as LearningEngine
    participant DB as Repositories
    participant RAG as RetrievalPort
    participant AI as AiTaskPort

    Student->>API: Submit answer
    API->>UC: command
    UC->>DB: Load mission/activity/evidence
    DB-->>UC: state

    UC->>Engine: Evaluate next required operation

    alt Deterministic evaluation
        Engine-->>UC: next action
    else AI evaluation required
        UC->>RAG: Retrieve expected evidence
        RAG-->>UC: EvidencePackage
        UC->>AI: RESPONSE_EVALUATION task
        AI-->>UC: validated result
        UC->>Engine: Apply validated evaluation
        Engine-->>UC: next action
    end

    UC->>DB: Persist attempt/evidence/mission transition
    UC-->>API: response
    API-->>Student: feedback + next activity
```

------------------------------------------------------------------------

# 18. AI Task Port

Application/domain code should invoke a provider-independent port:

``` text
AiTaskPort.execute(AiTaskRequest)
```

Conceptual request:

``` text
AiTaskRequest
├── taskType
├── promptVersion
├── learnerContext
├── activityContext
├── evidencePackage
├── groundingMode
└── outputContract
```

Conceptual response:

``` text
ValidatedAiResult<T>
```

------------------------------------------------------------------------

# 19. AI Orchestrator

The AI Orchestrator owns execution concerns.

Responsibilities:

-   Resolve prompt template
-   Build canonical prompt context
-   Ask Provider Router for route
-   Call provider adapter
-   Normalize provider result
-   Validate schema
-   Validate required source references
-   Repair/retry when permitted
-   Record diagnostics
-   Return validated task result

It does not decide educational progression.

------------------------------------------------------------------------

# 20. Provider Router

Conceptually:

``` text
AiTaskRequest
   ↓
ProviderRouter
   ↓
RoutingPolicy
   ├── task suitability
   ├── provider availability
   ├── quota
   ├── rate limit
   ├── model capability
   ├── evaluation status
   └── cost/latency
   ↓
ProviderRoute
```

Example:

``` text
provider = GEMINI
model = configured-model
fallback = OLLAMA
```

Exact model names remain configuration/evaluation driven.

------------------------------------------------------------------------

# 21. Provider Adapter Contract

``` text
AiProviderAdapter
├── providerId()
├── supports(task)
├── execute(request)
├── stream(request)
└── health/capability metadata
```

Implementations:

``` text
GeminiProviderAdapter
OllamaCloudProviderAdapter
```

Provider-specific request/response DTOs remain inside the adapter.

------------------------------------------------------------------------

# 22. Gemini Provider Adapter

Responsibilities:

-   Translate canonical AI task into Spring AI Google GenAI request
-   Apply provider/model configuration
-   Support structured output where available
-   Support multimodal input when approved
-   Normalize usage/latency/error metadata

No Gemini-specific type should escape the adapter boundary.

------------------------------------------------------------------------

# 23. Ollama Cloud Provider Adapter

Responsibilities:

-   Translate canonical AI task into Ollama Cloud API request
-   Add bearer authentication
-   Normalize provider response
-   Normalize stream events
-   Map provider errors/rate-limit responses
-   Record model/usage metadata where available

The rest of the application should not know the Ollama API base URL or
auth format.

------------------------------------------------------------------------

# 24. Provider Fallback

Fallback is orchestrated, not automatic inside adapters.

Flow:

``` text
Primary Provider
   ↓ fails with eligible failure
Provider Router
   ↓
Fallback Route
   ↓
Same Task Contract
   ↓
Same EvidencePackage
   ↓
Same Grounding Mode
   ↓
Same Validation
```

Do not fallback on:

-   Invalid source evidence
-   Safety rejection
-   Application validation failure

These are not provider-availability problems.

------------------------------------------------------------------------

# 25. AI Failure Taxonomy

Normalize provider errors to application errors.

Examples:

``` text
AI_PROVIDER_UNAVAILABLE
AI_RATE_LIMITED
AI_QUOTA_EXHAUSTED
AI_TIMEOUT
AI_INVALID_RESPONSE
AI_SCHEMA_FAILURE
AI_GROUNDING_FAILURE
AI_UNSUPPORTED_TASK
```

The frontend should not receive raw provider error bodies.

------------------------------------------------------------------------

# 26. AI Request Manager

The AI Request Manager owns:

-   Per-provider concurrency
-   Bounded queue
-   Priority
-   Timeouts
-   Cancellation
-   Rate limiting
-   Retry/backoff
-   Circuit breaker behavior
-   Usage diagnostics

It may be implemented within the `ai.application/infrastructure`
boundary.

------------------------------------------------------------------------

# 27. AI Request Priorities

Recommended:

``` text
P1 INTERACTIVE_EVALUATION
P2 INTERACTIVE_EXPLANATION
P3 INTERACTIVE_GENERATION
P4 MISSION_PREPARATION
P5 BACKGROUND_AI
```

Active student feedback should outrank non-urgent generation.

------------------------------------------------------------------------

# 28. RAG Port

Application code interacts with:

``` text
RetrievalPort.retrieve(RetrievalIntent)
```

Response:

``` text
EvidencePackage
```

Application code must not receive raw pgvector internals.

------------------------------------------------------------------------

# 29. Retrieval Service

Responsibilities:

-   Ownership validation
-   Retrieval-scope enforcement
-   Metadata filters
-   Semantic query
-   Lexical query
-   Candidate merge
-   Ranking
-   Deduplication
-   Evidence quality classification
-   SourceReference resolution

------------------------------------------------------------------------

# 30. Retrieval Intent

Conceptually:

``` text
RetrievalIntent
├── userId
├── taskType
├── groundingMode
├── retrievalScope
├── topicId
├── targetConcept
├── learningObjective
├── allowedMaterialVersions
├── allowedDocumentNodes
└── queryText
```

The backend derives `userId` and allowed source IDs.

Never trust them directly from client input.

------------------------------------------------------------------------

# 31. Hybrid Retrieval Repository

Infrastructure may use native PostgreSQL SQL to combine:

``` text
pgvector similarity
+
tsvector rank
+
pg_trgm similarity
+
metadata filters
```

This repository remains inside `rag.infrastructure`.

The Learning Engine must never depend on SQL ranking details.

------------------------------------------------------------------------

# 32. Evidence Package Builder

The RAG module returns:

``` text
EvidencePackage
├── quality
├── groundingMode
├── chunks
├── visuals
├── sourceReferences
├── limitations
└── retrievalDiagnostics
```

Prompt Builder converts this into bounded `SOURCE_CONTEXT`.

------------------------------------------------------------------------

# 33. Prompt Registry

Production prompts should be centralized.

Conceptually:

``` text
PromptTemplateRegistry
├── HIPPOCAMPUS_SYSTEM_V1
├── EXPLANATION_V1
├── QUESTION_GENERATION_V1
├── RESPONSE_EVALUATION_V1
├── CONCEPT_CONNECTION_V1
├── CONTEXTUAL_APPLICATION_V1
├── REFLECTION_INTERPRETATION_V1
├── MISSION_PLANNING_V1
└── STRUCTURED_OUTPUT_REPAIR_V1
```

Feature code must not embed arbitrary prompt strings.

------------------------------------------------------------------------

# 34. Prompt Context Builder

Responsibilities:

-   Select minimal learner evidence
-   Include current activity
-   Include student response
-   Include EvidencePackage
-   Enforce context budget
-   Deduplicate source context
-   Reserve output budget
-   Delimit untrusted source/student content

------------------------------------------------------------------------

# 35. Output Validation

Validation layers may include:

``` text
Provider Result
↓
Transport Parsing
↓
Schema Validation
↓
Enum/Business Constraint Validation
↓
Source Reference Validation
↓
Grounding/Safety Validation
↓
ValidatedAiResult
```

An invalid result never directly updates persistent learning state.

------------------------------------------------------------------------

# 36. Material Upload API

Recommended pattern:

``` text
POST /api/materials
multipart/form-data
```

Response:

``` text
Material summary
+
processing status
+
processing job identifier/status resource
```

Do not block upload requests until full extraction/embedding completes.

------------------------------------------------------------------------

# 37. Background Processing Architecture

Use durable database-backed jobs.

Logical worker:

``` text
ProcessingJobWorker
   ↓
claim next job
   ↓
dispatch handler
   ↓
update progress
   ↓
complete / retry / fail
```

Handlers:

``` text
ExtractMaterialJobHandler
DetectStructureJobHandler
ExtractVisualsJobHandler
ChunkMaterialJobHandler
EmbedChunksJobHandler
IndexMaterialJobHandler
CleanupMaterialJobHandler
```

------------------------------------------------------------------------

# 38. Job Chaining

A MaterialVersion processing pipeline may chain:

``` text
EXTRACT
↓
STRUCTURE_DETECT
↓
VISUAL_EXTRACT
↓
CHUNK
↓
EMBED
↓
INDEX
↓
ACTIVATE
```

Each stage is independently observable and retryable.

Do not hide a 600-page processing pipeline inside one opaque method.

------------------------------------------------------------------------

# 39. Job Claiming

Use PostgreSQL atomic claiming.

Recommended approach:

``` text
SELECT ...
FOR UPDATE SKIP LOCKED
```

within a short claim transaction.

The worker then marks:

``` text
RUNNING
locked_by
locked_at
```

and commits before processing.

------------------------------------------------------------------------

# 40. Idempotency

Background handlers must be designed for retries.

Examples:

-   Chunk generation should replace/upsert the current
    processing-version output rather than duplicate it.
-   Embedding job should respect IndexGeneration.
-   Activation should be safe when called twice.
-   File extraction should avoid duplicate binary assets through content
    hashes where practical.

------------------------------------------------------------------------

# 41. Processing Transactions

Long-running extraction should not occur inside one database
transaction.

Preferred:

``` text
Claim Job
↓
Read Source
↓
Process
↓
Persist Bounded Batch
↓
Update Progress
↓
Continue
```

This reduces locks and failure scope.

------------------------------------------------------------------------

# 42. Batch Processing

For very large material:

``` text
600 pages
```

process bounded page/chunk batches.

Benefits:

-   Recoverability
-   Progress reporting
-   Memory control
-   Retry scope
-   Reduced transaction length

------------------------------------------------------------------------

# 43. SSE Streaming

Use Server-Sent Events for student-facing streaming explanations where
useful.

Conceptual endpoint:

``` text
POST/GET learning action
↓
create request
↓
SSE stream resource
```

Exact HTTP design may use a two-step request + stream ID if POST
streaming becomes awkward.

------------------------------------------------------------------------

# 44. Streaming Boundary

Stream only content safe to display incrementally.

Do not update:

-   LearningEvidence
-   Review state
-   Mission completion

from partial streamed model output.

Persistent state uses the final validated result.

------------------------------------------------------------------------

# 45. Cancellation

If the student leaves a page or requests another explanation:

-   Cancel provider request where supported
-   Mark obsolete work
-   Avoid persisting stale result
-   Release concurrency capacity

Cancellation must not roll back already persisted unrelated learning
state.

------------------------------------------------------------------------

# 46. Validation Layers

## API Validation

Examples:

-   Required fields
-   File metadata
-   Enum values
-   Study time ranges

## Application Validation

Examples:

-   Topic belongs to user
-   Mission is active
-   Activity is current
-   Material is usable

## Domain Validation

Examples:

-   Valid mission state transition
-   Review rule
-   Retry policy

## Database Validation

Examples:

-   Foreign keys
-   Unique constraints
-   nullability

------------------------------------------------------------------------

# 47. DTO Boundary

API DTOs must be separate from:

-   JPA entities
-   Provider DTOs
-   Domain aggregates

Recommended naming:

``` text
CreateTopicRequest
TopicResponse
StartStudyMissionRequest
StudyMissionResponse
SubmitActivityResponseRequest
LearningFeedbackResponse
```

Avoid returning JPA entities directly.

------------------------------------------------------------------------

# 48. Mapping

Use explicit mapping code or lightweight mappers.

Avoid reflection-heavy abstraction if simple manual mapping is clearer.

Generated mappers may be introduced later if mapping volume justifies
it.

------------------------------------------------------------------------

# 49. Error Handling

Use one centralized exception mapping layer.

Conceptually:

``` text
DomainException
ApplicationException
InfrastructureException
        ↓
ProblemDetail / API Error
```

Do not expose:

-   SQL exceptions
-   provider stack traces
-   API keys
-   raw model errors
-   internal file paths

------------------------------------------------------------------------

# 50. API Error Contract

Recommended fields:

``` json
{
  "code": "MATERIAL_NOT_READY",
  "message": "This material is still being processed.",
  "correlationId": "...",
  "details": {}
}
```

Messages should be user-actionable where appropriate.

------------------------------------------------------------------------

# 51. HTTP Status Guidelines

Examples:

``` text
400 invalid input
401 unauthenticated
403 unauthorized resource
404 not found
409 invalid state/conflict
413 upload too large
422 valid request but unsupported/insufficient semantic state
429 application/provider capacity limit where appropriate
503 provider/infrastructure temporarily unavailable
```

Exact endpoint behavior is refined in API design.

------------------------------------------------------------------------

# 52. Security Integration

Use Spring Security.

Controller/use-case entrypoints must resolve the authenticated principal
server-side.

Ownership checks belong in application/domain access policies, not just
URL security rules.

------------------------------------------------------------------------

# 53. Authorization Pattern

Avoid:

``` text
GET /topics/{id}?userId=...
```

Prefer:

``` text
authenticatedUser
+
topicId
      ↓
TopicAccessPolicy
```

The backend derives ownership.

------------------------------------------------------------------------

# 54. File Security Boundary

Uploaded files pass through:

``` text
Transport Limits
↓
MIME/Type Validation
↓
Storage
↓
Background Inspection/Processing
```

Parsing occurs in controlled backend workers.

Detailed malware/content controls belong to Document 22/21.

------------------------------------------------------------------------

# 55. Configuration Structure

Recommended configuration namespaces:

``` text
hippocampus.ai.*
hippocampus.rag.*
hippocampus.materials.*
hippocampus.processing.*
hippocampus.review.*
hippocampus.security.*
```

Provider configs:

``` text
hippocampus.ai.providers.gemini.*
hippocampus.ai.providers.ollama.*
```

Secrets come from environment/secret storage, not configuration
committed to source.

------------------------------------------------------------------------

# 56. Feature Flags

Feature flags may be useful for:

-   Provider routing experiment
-   New prompt version
-   New retrieval strategy
-   Visual AI
-   Pilot-only features

Flags should not become a substitute for clean versioned architecture.

------------------------------------------------------------------------

# 57. Caching

Use bounded in-process caches only where correctness is clear.

Candidates:

-   Prompt templates
-   Stable config
-   Material hierarchy metadata
-   Source-reference lookup

Avoid in-process cache as the authoritative store for:

-   Learning state
-   Review
-   Jobs
-   Provider quota
-   Cross-instance locks

------------------------------------------------------------------------

# 58. No Redis Requirement

v1 should not introduce Redis merely for convenience.

If later horizontal scaling creates a requirement for distributed:

-   Rate limiting
-   Locks
-   Cache
-   Session performance

a new ADR can introduce it.

------------------------------------------------------------------------

# 59. Clock Abstraction

Time-sensitive domain logic should depend on an injectable Clock.

Examples:

-   Review eligibility
-   Mission timeout
-   Scheduled retry
-   evidence timestamps

This improves deterministic testing.

------------------------------------------------------------------------

# 60. Randomness Abstraction

If any activity selection uses randomness, it should be
controllable/testable.

Avoid hidden randomness inside domain policy.

AI generation itself is already non-deterministic and is evaluated
separately.

------------------------------------------------------------------------

# 61. Observability Hooks

Each request should carry a correlation/request ID.

Track important dimensions:

-   user-safe internal identifier
-   endpoint/use case
-   AI task type
-   provider/model
-   prompt version
-   retrieval quality
-   queue time
-   provider latency
-   validation outcome
-   processing job ID

Do not log full student source text by default.

------------------------------------------------------------------------

# 62. Structured Logging

Log structured fields rather than only free-form messages.

Example:

``` text
event=ai_task_completed
task=RESPONSE_EVALUATION
provider=GEMINI
prompt=RESPONSE_EVALUATION_V1
latency_ms=1320
status=SUCCESS
```

------------------------------------------------------------------------

# 63. Metrics

Backend metrics should include:

``` text
http_request_duration
ai_request_duration
ai_request_failures
ai_rate_limit_events
ai_queue_depth
rag_retrieval_duration
rag_insufficient_evidence
processing_job_duration
processing_job_failures
material_processing_pages
sse_active_streams
```

Exact names/tooling belong to Document 24.

------------------------------------------------------------------------

# 64. Module Enforcement

Use ArchUnit and optionally Spring Modulith verification to prevent
forbidden dependencies.

Examples:

``` text
learning.domain must not depend on ai.infrastructure
progress.domain must not depend on web/api
rag.domain must not depend on Gemini SDK
```

Architecture rules should run in CI.

------------------------------------------------------------------------

# 65. Testing Seams

The backend should allow substitution of:

``` text
FakeAiTaskPort
FakeRetrievalPort
FixedClock
InMemory/Test Repository
```

for fast domain/use-case tests.

Integration tests then verify real PostgreSQL/provider adapter behavior
separately.

------------------------------------------------------------------------

# 66. Provider Contract Tests

Each provider adapter should share a contract test suite.

Given canonical:

``` text
AiTaskRequest
```

verify:

-   request translation
-   auth handling
-   timeout mapping
-   rate-limit mapping
-   structured output parsing
-   stream normalization
-   provider metadata normalization

Live API tests should be isolated from ordinary CI unless
credentials/quota policy allows them.

------------------------------------------------------------------------

# 67. RAG Integration Tests

Use Testcontainers PostgreSQL + pgvector.

Test:

-   metadata scope
-   ownership isolation
-   vector ranking
-   lexical ranking
-   hybrid merge
-   source reference resolution
-   inactive version exclusion

------------------------------------------------------------------------

# 68. Transaction Tests

Critical flows should verify rollback/atomicity.

Examples:

-   Attempt persisted but evidence update fails
-   Material activation fails halfway
-   Review completion update fails
-   Duplicate background job claim

------------------------------------------------------------------------

# 69. Optimistic Concurrency

Use entity/domain versioning for flows prone to concurrent changes.

Example:

Two browser tabs submit the same current activity.

Expected:

``` text
first succeeds
second receives conflict / stale-state handling
```

not duplicate learning evidence.

------------------------------------------------------------------------

# 70. Application Idempotency

Where client retries are plausible, commands may accept an idempotency
key.

High-value candidates:

-   Material upload initialization
-   Activity submission
-   Mission creation

Exact API design may defer general idempotency until needed.

Background jobs require idempotency regardless.

------------------------------------------------------------------------

# 71. API Versioning

v1 internal product API may start under:

``` text
/api/v1
```

or use stable endpoint contracts without URL versioning initially.

The decision should prioritize clarity over ceremony.

If external/public API compatibility becomes a requirement, formal
versioning becomes mandatory.

------------------------------------------------------------------------

# 72. Pagination

All potentially unbounded list APIs require pagination.

Examples:

-   Materials
-   Study history
-   Generated artifacts
-   Reviews
-   Admin/diagnostic views

Do not return an entire user's long-term history by default.

------------------------------------------------------------------------

# 73. Database Query Discipline

Avoid N+1 loading.

Use:

-   explicit fetch queries
-   projections
-   batch loading
-   bounded aggregates

Do not mark every relationship `EAGER`.

------------------------------------------------------------------------

# 74. Domain Event Usage

In-process domain/application events may be used when they reduce
coupling.

Example:

``` text
MaterialVersionActivated
↓
invalidate source caches
```

Do not introduce event sourcing.

Events must not obscure critical transaction logic.

------------------------------------------------------------------------

# 75. Generated Artifact Lifecycle

Persist only artifacts required for:

-   mission continuity
-   reuse
-   evidence
-   audit/evaluation

Transient clarification wording may remain ephemeral.

------------------------------------------------------------------------

# 76. Review Architecture

`review` module exposes:

``` text
ReviewPolicy
ReviewService
ReviewRepository
```

Learning Engine requests review state but does not directly manipulate
review tables.

Example:

``` text
LearningEngine
↓
ReviewPort.getReviewContext(...)
```

------------------------------------------------------------------------

# 77. Progress Architecture

`progress` owns evidence projection.

Conceptually:

``` text
EvidenceEvent
↓
LearningEvidenceProjector
↓
LearningEvidence summary
```

The projector is deterministic/configurable and fully testable.

------------------------------------------------------------------------

# 78. Misconception Handling

AI may identify a potential misconception in validated response
evaluation.

The progress module determines:

-   whether to persist `POSSIBLE`
-   when repeated evidence promotes it to `ACTIVE`
-   when corrective evidence resolves it

The model does not directly change misconception status.

------------------------------------------------------------------------

# 79. Material Processing Boundary

`materials` owns source lifecycle metadata.

`processing` behavior may physically live under materials/infrastructure
but should remain explicit.

Material status is derived from the pipeline, not from UI guesses.

------------------------------------------------------------------------

# 80. Source Activation Rule

Only successfully processed/indexed MaterialVersions become active for
normal retrieval.

`PARTIALLY_READY` versions may be active only with explicit
capability/limitation metadata.

------------------------------------------------------------------------

# 81. RAG Failure Integration

If RAG returns:

``` text
INSUFFICIENT
LIMITED
FAILED
```

the application sends that state back into Learning Engine policy.

Do not hide retrieval failure inside an empty prompt.

------------------------------------------------------------------------

# 82. AI Failure Integration

AI failure is also a Learning Engine/application event.

Examples:

-   fallback provider
-   reuse validated existing artifact
-   offer retry
-   present source-only material
-   pause AI-dependent activity

The exact action depends on task type and policy.

------------------------------------------------------------------------

# 83. Backend Dependency Diagram

``` mermaid
flowchart TD

API[API Controllers]
APP[Application Use Cases]
DOMAIN[Domain / Learning Engine]

API --> APP
APP --> DOMAIN

DOMAIN --> PORTS[Domain Ports]

PORTS --> JPA[Persistence Adapters]
PORTS --> RAG[RAG Adapter]
PORTS --> AI[AI Orchestrator]
PORTS --> JOBS[Background Job Adapter]

AI --> ROUTER[Provider Router]
ROUTER --> GEMINI[Gemini Adapter]
ROUTER --> OLLAMA[Ollama Adapter]

JPA --> DB[(PostgreSQL)]
RAG --> DB
JOBS --> DB
```

------------------------------------------------------------------------

# 84. Backend Module Interaction

``` mermaid
flowchart LR

Identity --> Learning
Identity --> Materials
Identity --> Progress
Identity --> Review

Learning --> Progress
Learning --> Review
Learning --> RAG
Learning --> AI
Learning --> Materials

RAG --> Materials
AI --> RAG

Materials --> Shared
Learning --> Shared
Progress --> Shared
Review --> Shared
RAG --> Shared
AI --> Shared
```

Dependencies must remain directional and justified.

------------------------------------------------------------------------

# 85. Example Package Layout

``` text
src/main/java/com/hippocampus/

identity/
  api/
  application/
  domain/
  infrastructure/

learning/
  api/
  application/
  domain/
  port/
  infrastructure/

progress/
  application/
  domain/
  port/
  infrastructure/

review/
  api/
  application/
  domain/
  port/
  infrastructure/

materials/
  api/
  application/
  domain/
  port/
  infrastructure/

rag/
  application/
  domain/
  port/
  infrastructure/

ai/
  application/
  domain/
  port/
  infrastructure/
    gemini/
    ollama/

shared/
  domain/
  web/
  persistence/
  observability/

bootstrap/
```

This is the baseline organization; exact class names can evolve during
implementation.

------------------------------------------------------------------------

# 86. Code Dependency Rules

Examples:

``` text
domain -> no infrastructure
application -> domain + ports
api -> application
infrastructure -> ports + external frameworks
```

Forbidden:

``` text
learning.domain -> Gemini SDK
rag.domain -> JPA EntityManager
controller -> Spring Data repository
provider adapter -> LearningEvidenceRepository
```

------------------------------------------------------------------------

# 87. Backend Coding Standards Baseline

Implementation should favor:

-   Constructor injection
-   Immutable request/response records where appropriate
-   Explicit domain enums/value objects
-   Small focused services
-   No field injection
-   No generic "Util" dumping grounds
-   No static service locators
-   No silent exception swallowing
-   No business logic in entities merely because JPA allows it
-   No raw provider DTOs outside adapters

A dedicated Coding Standards document may be added later if needed.

------------------------------------------------------------------------

# 88. Performance Rules

For the \~40-user MVP:

-   Avoid unnecessary AI calls.
-   Avoid holding DB connections during external inference.
-   Batch large material persistence.
-   Keep retrieval metadata-filtered.
-   Paginate.
-   Use appropriate DB indexes.
-   Bound worker concurrency.
-   Bound AI/provider concurrency.
-   Cancel obsolete AI requests.

------------------------------------------------------------------------

# 89. Provider Quota Handling

The backend should track provider responses indicating:

-   rate limit
-   quota exhaustion
-   unsupported model
-   temporary outage

Routing policy may choose fallback only if allowed.

If neither provider can satisfy the task:

``` text
AI_TEMPORARILY_UNAVAILABLE
```

with a transparent client response.

------------------------------------------------------------------------

# 90. Free-Tier Assumption Rule

Do not encode provider free-tier limits as constants in domain logic.

They belong to configuration/operational policy.

This protects the product from provider plan changes.

------------------------------------------------------------------------

# 91. Backend Security Principle

No sensitive trust decision should be delegated to:

-   Browser
-   LLM
-   Source document
-   Request-supplied ownership fields

The backend resolves and validates all authoritative identity and scope.

------------------------------------------------------------------------

# 92. Backend MVP Exit Criteria

Backend architecture is implemented correctly when:

1.  Module boundaries are enforceable.
2.  Controllers remain thin.
3.  Learning Engine is testable without AI/providers.
4.  Gemini and Ollama are replaceable adapters.
5.  Provider routing is centralized.
6.  No API key reaches frontend.
7.  RAG returns EvidencePackage.
8.  User retrieval isolation is enforced.
9.  AI results are validated before persistence.
10. LearningEvidence derives from EvidenceEvents.
11. Large file processing is asynchronous and recoverable.
12. Study responses do not hold DB transactions during long AI calls.
13. Critical writes are transactional.
14. Jobs are idempotent.
15. SSE does not persist partial output as evidence.
16. Provider errors map to application errors.
17. PostgreSQL/pgvector integration is covered by integration tests.
18. Architecture dependency tests run in CI.

------------------------------------------------------------------------

# 93. Locked v1 Backend Decisions

The following are approved:

1.  Spring Boot backend remains a modular monolith.
2.  Modules are identity, learning, progress, review, materials, rag,
    ai, shared, and bootstrap.
3.  Each substantial module separates API, application, domain/ports,
    and infrastructure concerns.
4.  Controllers are transport adapters, not business services.
5.  Use cases own user-intention orchestration.
6.  Learning Engine remains domain/application-owned.
7.  Domain code does not depend on provider SDKs or JPA infrastructure.
8.  Repositories are exposed through domain/application ports.
9.  JPA entities do not need to equal domain models.
10. Transaction boundaries sit primarily around application use cases.
11. External AI calls should not hold long DB transactions open.
12. AI integration uses a provider-independent `AiTaskPort`.
13. AI Orchestrator centralizes prompt/provider/validation execution.
14. Provider Router chooses Ollama API or Gemini API through
    configuration/evaluation policy.
15. Provider adapters isolate provider-specific HTTP/SDK concerns.
16. Fallback preserves task contract, grounding, evidence, schema, and
    safety.
17. Provider errors are normalized.
18. AI Request Manager owns queue/concurrency/rate-limit concerns.
19. RAG is accessed through `RetrievalPort`.
20. RAG returns EvidencePackage rather than raw SQL/vector results.
21. Prompt templates are centralized and versioned.
22. Prompt context building is centralized.
23. AI results pass schema/source/grounding validation.
24. Large file processing uses durable DB-backed jobs.
25. Processing stages are explicit and independently retryable.
26. Job claiming is atomic and safe for future multiple workers.
27. Long processing occurs outside long DB transactions.
28. Large materials process in bounded batches.
29. SSE is used only for suitable incremental output.
30. Partial streamed output does not update learning state.
31. Validation is layered across API/application/domain/database.
32. API DTOs, provider DTOs, JPA entities, and domain models remain
    separate concepts.
33. Error handling is centralized.
34. Authorization uses authenticated identity plus authoritative
    ownership relationships.
35. Configuration/secrets are externalized.
36. Redis is not required for v1.
37. Clock is injectable for time-sensitive learning/review rules.
38. Structured observability is built into use cases, RAG, AI, and
    processing.
39. ArchUnit/module verification enforces architectural boundaries.
40. Provider adapters receive contract tests.
41. RAG integration uses real PostgreSQL + pgvector in tests.
42. Optimistic concurrency protects stale mission submissions.
43. Learning evidence remains application-owned.
44. Material processing and AI provider availability degrade
    transparently.
45. Backend implementation must preserve Documents 00--18.

------------------------------------------------------------------------

# 94. Out of Scope

This document does not yet define:

-   Exact endpoint inventory
-   Exact API payloads
-   Exact Java class names
-   Exact ORM mappings
-   Exact Flyway scripts
-   Exact provider model names
-   Exact retry numbers
-   Exact executor sizes
-   Exact SSE endpoint mechanics
-   Exact security configuration
-   Exact OCR implementation
-   Exact deployment topology

These are resolved through implementation, Documents 21--25, and ADRs.

------------------------------------------------------------------------

# 95. Next Document

**20 - Frontend Architecture**

It should define:

-   React application structure
-   Route hierarchy
-   Guided Study Mission UI
-   State ownership
-   TanStack Query usage
-   Minimal Zustand usage
-   Upload flow
-   Processing progress
-   SSE handling
-   Visual-learning components
-   Learning activity rendering
-   Resume flow
-   Progress/review UI
-   Accessibility
-   Error/loading states
-   API client
-   frontend testing seams

The frontend must preserve the product rule:

> **Features should appear as a guided learning flow, not as an
> overwhelming collection of tools and sidebars.**

------------------------------------------------------------------------

# 96. Revision History

  ----------------------------------------------------------------------------
  Version           Date              Author            Changes
  ----------------- ----------------- ----------------- ----------------------
  1.0.0             2026-08-24        Project           Initial finalized
                                      Hippocampus Team  Backend Architecture
                                                        defining Spring Boot
                                                        modular boundaries,
                                                        Learning Engine
                                                        placement,
                                                        provider-independent
                                                        AI orchestration,
                                                        Ollama/Gemini
                                                        adapters, RAG ports,
                                                        transaction rules,
                                                        background workers,
                                                        SSE, validation,
                                                        security integration,
                                                        and testing seams

  ----------------------------------------------------------------------------

------------------------------------------------------------------------

# 97. Approval

**Status:** Final

**Reviewed By:** Project Hippocampus Team

**Approved By:** Project Hippocampus Team
