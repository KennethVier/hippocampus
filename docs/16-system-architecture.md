---
Audience: Solution architecture, backend, frontend, AI, data, DevOps,
  QA, security, and product contributors.
Authors: Project Hippocampus Team
Created: 2026-08-23
Document ID: 16
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
- 10 - AI Architecture
- 11 - AI Learning Engine
- 12 - Prompt Engineering Strategy
- 13 - RAG Architecture
- 14 - Knowledge Base Design
- 15 - AI Evaluation Strategy
Purpose: Define the top-level technical architecture of Hippocampus and
  how product, learning, AI, RAG, storage, processing, security, and
  operational concerns interact as one deployable system.
Related Documents:
- 17 - Technology Stack & ADR Baseline
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
Scope: Architectural style, system boundaries, logical components,
  runtime interactions, synchronous/asynchronous flows, data flow, AI
  integration, ingestion, persistence, security boundaries, deployment
  shape, scalability, failure containment, and implementation
  constraints.
Status: Final
Title: System Architecture
Version: 1.1.1
---

# 16 - System Architecture

## 1. Purpose

This document defines the top-level technical architecture of
Hippocampus.

It answers:

> **How should all approved Hippocampus capabilities work together as
> one coherent technical system?**

The architecture must implement the educational and product decisions
established in Documents 00--15.

It must not redefine them.

------------------------------------------------------------------------

# 2. Architectural Objective

Hippocampus should be:

-   Student-centered
-   Web-first
-   External-AI-provider-aware
-   Cost-conscious
-   Modular
-   Explainable
-   Grounded
-   Secure
-   Maintainable
-   Deployable without unnecessary infrastructure complexity
-   Capable of supporting an initial population of approximately 40
    medical-student users

The architecture should remain simple enough for MVP development while
leaving clear seams for future scale.

------------------------------------------------------------------------

# 3. Locked Architectural Style

For v1, Hippocampus uses a:

> **Modular Monolith + External Dual-Provider AI Gateway + Background
> Worker Model**

``` text
Frontend
   ↓
Single Spring Boot Application
   ├── Product Modules
   ├── Learning Engine
   ├── RAG
   ├── AI Orchestration
   ├── Provider Router
   ├── Persistence
   └── Background Job Coordination
   ↓
Persistence / File Storage / Search
   +
External AI Providers
   ├── Ollama API
   └── Google Gemini API
```

We should not begin with microservices.

The AI providers execute bounded tasks. They do not own learning state,
progression, grounding policy, or authorization.

# 4. Why Modular Monolith

A modular monolith is appropriate because:

-   Initial user volume is modest.
-   Product logic is still evolving.
-   Educational flows cross multiple functional areas.
-   AI/RAG behavior needs end-to-end traceability.
-   One developer/small team should be able to run the system locally.
-   Operational complexity should remain low.
-   Transaction boundaries are easier to manage.
-   Refactoring is easier before domain boundaries stabilize.

The system should still enforce internal modular boundaries so future
extraction remains possible.

------------------------------------------------------------------------

# 5. High-Level System Context

``` mermaid
flowchart LR

Student[Medical Student]
Frontend[Web Frontend]
Backend[Spring Boot Application]
DB[(Relational Database)]
Files[(File/Object Storage)]
Search[(Vector/Search Index)]
Router[AI Provider Router]
Ollama[Ollama API]
Gemini[Google Gemini API]

Student --> Frontend
Frontend --> Backend

Backend --> DB
Backend --> Files
Backend --> Search
Backend --> Router
Router --> Ollama
Router --> Gemini
```

# 6. Top-Level Logical Architecture

``` text
┌─────────────────────────────────────────────┐
│               WEB FRONTEND                  │
│                                             │
│  Subjects / Topics                          │
│  Material Upload                            │
│  Study Mission                              │
│  Visual Learning                            │
│  Progress / Review                          │
└──────────────────────┬──────────────────────┘
                       │ HTTPS / API
                       ▼
┌─────────────────────────────────────────────┐
│            SPRING BOOT APPLICATION          │
│                                             │
│  API Layer                                  │
│  Application Services                       │
│                                             │
│  ┌───────────────────────────────────────┐  │
│  │ Learning Domain                       │  │
│  │ - Study Mission                       │  │
│  │ - Learning Engine                     │  │
│  │ - Learning Evidence                   │  │
│  │ - Review                              │  │
│  └───────────────────────────────────────┘  │
│                                             │
│  ┌───────────────────────────────────────┐  │
│  │ Material / Knowledge Domain           │  │
│  │ - Material Intake                     │  │
│  │ - Structure                           │  │
│  │ - Chunk / Visual Metadata             │  │
│  │ - Topic Mapping                       │  │
│  └───────────────────────────────────────┘  │
│                                             │
│  ┌───────────────────────────────────────┐  │
│  │ AI Domain                             │  │
│  │ - AI Orchestrator                     │  │
│  │ - Prompt Registry                     │  │
│  │ - Output Validation                   │  │
│  │ - AI Request Manager                  │  │
│  └───────────────────────────────────────┘  │
│                                             │
│  ┌───────────────────────────────────────┐  │
│  │ RAG Domain                            │  │
│  │ - Retrieval Intent                    │  │
│  │ - Search / Ranking                    │  │
│  │ - Evidence Package                    │  │
│  │ - Source References                   │  │
│  └───────────────────────────────────────┘  │
│                                             │
│  Background Job Layer                      │
│  Security / Auth                           │
│  Observability                             │
└──────────┬─────────────┬───────────┬────────┘
           │             │           │
           ▼             ▼           ▼
     Relational DB   File Storage   Search/Vector
                           │
                           ▼
                        Ollama
```

------------------------------------------------------------------------

# 7. System Boundary

Hippocampus v1 consists of four principal runtime boundaries:

## 7.1 Web Client

Responsible for:

-   User interaction
-   Study Mission UI
-   Subject/topic navigation
-   Upload UX
-   Progress
-   Source display
-   Client-side state where appropriate

## 7.2 Spring Boot Application

Responsible for:

-   Business logic
-   Learning Engine
-   AI orchestration
-   RAG orchestration
-   Persistence
-   Security enforcement
-   Background-job coordination
-   Validation
-   API surface

## 7.3 Persistence Layer

Responsible for:

-   Structured relational data
-   File/object storage
-   Search/vector data

## 7.4 External AI Provider Boundary

Responsible for executing approved AI tasks through external provider
APIs.

Approved v1 providers:

-   Ollama API
-   Google Gemini API

The backend owns provider routing, task contracts, grounding,
validation, quotas, and educational state.

------------------------------------------------------------------------

# 8. Major Backend Modules

The Spring Boot application should be organized into logical modules.

Recommended v1 modules:

``` text
identity
learning
materials
rag
ai
review
progress
shared
```

These names are conceptual; exact package names will be finalized in
Backend Architecture.

------------------------------------------------------------------------

# 9. Identity Module

Responsibilities:

-   User identity
-   Authentication integration
-   Authorization
-   Ownership checks
-   User-scoped access

It does not own learning logic.

------------------------------------------------------------------------

# 10. Learning Module

Responsibilities:

-   Study Mission
-   Learning Objective
-   Learning Activity
-   Mission state
-   Learning Engine
-   Activity transitions
-   Time-aware learning decisions
-   Learner-state adaptation

This is the educational control center.

------------------------------------------------------------------------

# 11. Progress Module

Responsibilities:

-   Student attempts
-   Learning evidence
-   Evidence summaries
-   Misconception evidence
-   Reflection evidence
-   Progress views

It should not directly decide next-action behavior.

That belongs to Learning Engine.

------------------------------------------------------------------------

# 12. Review Module

Responsibilities:

-   Review eligibility
-   Review priority
-   Review records
-   Review rationale
-   Spaced relearning state

The scheduling logic is application-owned and
deterministic/configurable.

------------------------------------------------------------------------

# 13. Materials Module

Responsibilities:

-   Material metadata
-   Material versions
-   Upload state
-   Document structure
-   Visual assets
-   Material-topic mapping
-   Processing diagnostics
-   Source references

------------------------------------------------------------------------

# 14. RAG Module

Responsibilities:

-   Retrieval intent
-   Retrieval scope
-   Metadata filtering
-   Hybrid retrieval
-   Ranking
-   Deduplication
-   Evidence Package creation
-   Context assembly
-   Source traceability

------------------------------------------------------------------------

# 15. AI Module

Responsibilities:

-   Typed AI tasks
-   Prompt template resolution
-   Prompt context assembly coordination
-   AI request priority
-   Ollama API and Gemini API communication through provider adapters
-   Structured output validation
-   Repair/retry
-   AI diagnostics

Permanent rule:

> **The AI module does not own educational state.**

------------------------------------------------------------------------

# 16. Background Job Layer

Some tasks should be asynchronous.

Examples:

-   Large PDF processing
-   OCR
-   Structure detection
-   Chunking
-   Embedding generation
-   Re-indexing
-   Safe pre-generation
-   Cleanup

These should not block interactive Study Missions.

------------------------------------------------------------------------

# 17. Synchronous vs Asynchronous Work

## Synchronous

Typical:

-   Load topic
-   View progress
-   Submit MCQ
-   Retrieve saved evidence
-   Resume mission
-   Start interactive AI explanation
-   Evaluate short answer

## Asynchronous

Typical:

-   Process 600-page PDF
-   Generate embeddings
-   Extract figures
-   Re-index changed material
-   Background cleanup

------------------------------------------------------------------------

# 18. Request Flow --- Non-AI

``` mermaid
sequenceDiagram
    actor Student
    participant UI as Web Frontend
    participant API as Spring Boot
    participant DB as Database

    Student->>UI: Open topic
    UI->>API: GET topic context
    API->>DB: Load topic + evidence + mission state
    DB-->>API: Data
    API-->>UI: Topic context
    UI-->>Student: Render
```

This type of request should remain fast and independent of external AI
provider availability.

------------------------------------------------------------------------

# 19. Request Flow --- AI-Assisted

``` mermaid
sequenceDiagram
    actor Student
    participant UI as Web Frontend
    participant API as Spring Boot
    participant Engine as Learning Engine
    participant RAG as RAG Module
    participant AI as AI Module
    participant Router as Provider Router
    participant Provider as Ollama API / Gemini API
    participant DB as Database

    Student->>UI: Submit learning response
    UI->>API: Submit activity response

    API->>Engine: Process learning event
    Engine->>DB: Load relevant evidence
    DB-->>Engine: Evidence

    Engine->>RAG: Request source evidence
    RAG-->>Engine: Evidence Package

    Engine->>AI: Execute typed AI task
    AI->>Router: Typed AI task
    Router->>Provider: Provider-specific request
    Provider-->>Router: Provider result
    Router-->>AI: Normalized result
    AI->>AI: Validate / repair if needed
    AI-->>Engine: Validated result

    Engine->>DB: Persist attempt/evidence/state
    Engine-->>API: Next action
    API-->>UI: Learning result
    UI-->>Student: Feedback / next activity
```

------------------------------------------------------------------------

# 20. Material Upload Flow

``` mermaid
sequenceDiagram
    actor Student
    participant UI as Web Frontend
    participant API as Spring Boot
    participant Files as File Storage
    participant Job as Background Processor
    participant DB as Database
    participant Search as Search Index
    participant Router as Provider Router
    participant Provider as Ollama API / Gemini API

    Student->>UI: Upload PDF
    UI->>API: Upload material
    API->>Files: Store original file
    API->>DB: Create Material + MaterialVersion
    API->>Job: Queue ingestion
    API-->>UI: Accepted / processing

    Job->>Files: Read material
    Job->>Job: Extract + detect structure
    Job->>Job: Build chunks/visual metadata
    Job->>Router: Request embeddings
    Router->>Provider: Embedding request
    Provider-->>Router: Embeddings
    Router-->>Job: Normalized embeddings
    Job->>Search: Index chunks
    Job->>DB: Update structure + status
    DB-->>Job: Persisted

    UI->>API: Poll/check processing status
    API-->>UI: Progress / READY / PARTIAL / FAILED
```

------------------------------------------------------------------------

# 21. Large PDF Architecture

A 600-page PDF should follow:

``` text
Upload
 ↓
Persist Original
 ↓
Create Processing Job
 ↓
Page-by-Page Extraction
 ↓
Structure Detection
 ↓
Normalize
 ↓
Segment
 ↓
Chunk
 ↓
Embed
 ↓
Index
 ↓
READY
```

The user should not wait for the entire process in a blocking HTTP
request.

------------------------------------------------------------------------

# 22. API Boundary

The frontend should communicate only with Spring Boot.

It should not directly communicate with:

-   Ollama
-   Database
-   Vector index
-   File storage
-   Background worker internals

This centralizes security and educational logic.

------------------------------------------------------------------------

# 23. External AI Provider Boundary

Only the backend should communicate with Ollama API and Gemini API.

Benefits:

-   Prompt protection
-   Central validation
-   Authentication isolation
-   Model replacement
-   Request queueing
-   Rate limiting
-   Diagnostics

The browser must never receive Ollama API keys, Gemini API keys, or
unrestricted provider/model access.

The backend exposes a provider-independent internal contract:

``` text
Learning Engine
   ↓
AI Orchestrator
   ↓
Provider Router
   ├── Ollama API Adapter
   └── Gemini API Adapter
```

Routing may consider validated task quality, model capability, latency,
quota, rate limits, availability, and cost.

Provider fallback is allowed only when the alternate provider preserves
the same grounding mode, Evidence Package, output schema, validation
requirements, and safety behavior.

------------------------------------------------------------------------

# 24. Persistence Strategy

At the top-level architecture, v1 requires:

``` text
Relational Database
+
File/Object Storage
+
Vector/Search Capability
```

Exact technologies are deferred to 17/18/23.

For the initial scale, these may be co-located or implemented with
minimal separate infrastructure.

------------------------------------------------------------------------

# 25. Data Ownership Boundary

The relational database is authoritative for:

-   Users
-   Topics
-   Material metadata
-   Study state
-   Learning evidence
-   Review
-   Provenance

File storage is authoritative for:

-   Original uploaded binary sources
-   Extracted visual assets

Search/vector storage is an index.

It must not become the sole source of truth for source content.

------------------------------------------------------------------------

# 26. Search Index Rebuildability

The search/vector index should be considered rebuildable from
authoritative source/metadata records where practical.

This is important because:

-   Embedding models may change
-   Chunking may change
-   Index technology may change
-   Corruption may occur

------------------------------------------------------------------------

# 27. Event / Job Coordination

v1 does not require a full distributed message broker.

A durable background-job mechanism is sufficient if it can support:

-   Status
-   Retry
-   Failure state
-   Idempotency
-   Priority
-   Progress

Exact implementation belongs to 17/19/21.

------------------------------------------------------------------------

# 28. Priority Model

System work should follow:

``` text
Interactive Student Request
        >
Interactive AI Request
        >
Mission Preparation
        >
Background Processing
        >
Maintenance
```

This is especially important for the \~40-user target.

------------------------------------------------------------------------

# 29. AI Concurrency

The backend should enforce bounded concurrency before sending work to
either external provider.

Conceptually:

``` text
AI Request
   ↓
Priority
   ↓
Concurrency Gate
   ↓
Queue
   ↓
Ollama
```

Actual concurrency is configured after provider quota, rate-limit,
latency, and model benchmarking.

------------------------------------------------------------------------

# 30. Failure Containment

Failure in one subsystem should not unnecessarily cascade.

Examples:

## AI provider unavailable / rate limited

-   Deterministic features remain usable
-   Existing progress remains visible
-   Saved material remains accessible
-   AI-dependent activities fail transparently

## Vector index unavailable

-   Non-RAG product functions remain available
-   Source-grounded AI tasks fail safely

## Background ingestion failed

-   Existing materials remain intact
-   Failed material receives explicit status

------------------------------------------------------------------------

# 31. Graceful Degradation

The system should preserve as much safe functionality as possible.

Example:

``` text
Visual Interpretation Unavailable
        ↓
Original Source Image Still Available
        ↓
Text-Based Learning May Continue
```

only if the lost visual interpretation is not essential to the requested
activity.

------------------------------------------------------------------------

# 32. Security Boundary

Security should be enforced at the backend.

Key boundaries:

-   User authentication
-   Ownership validation
-   Material authorization
-   Retrieval scope
-   Upload validation
-   AI request sanitization
-   Prompt injection containment
-   Data minimization
-   Secret management

Detailed controls belong to 22.

------------------------------------------------------------------------

# 33. Cross-User Isolation

Every user-scoped operation should enforce ownership or authorization
before accessing:

-   Topic
-   Material
-   MaterialVersion
-   Chunk
-   Visual
-   Mission
-   Attempt
-   Learning evidence
-   Review
-   Generated artifact

No prompt should be relied upon for data isolation.

------------------------------------------------------------------------

# 34. Frontend Architectural Role

The frontend should remain primarily a presentation and interaction
layer.

It may manage:

-   Local UI state
-   Form state
-   Optimistic interaction where safe
-   Mission rendering
-   Upload progress
-   Streaming display

It should not own:

-   Mastery rules
-   Review rules
-   Learning Engine
-   Source authorization
-   Prompt construction
-   AI safety decisions

------------------------------------------------------------------------

# 35. Backend Architectural Role

The backend is the system-of-record and decision authority for:

-   Learning state
-   Educational transitions
-   AI task selection
-   RAG scope
-   Review state
-   User authorization
-   Source provenance
-   Persistence integrity

------------------------------------------------------------------------

# 36. Learning Engine Placement

The Learning Engine should remain inside the backend application.

``` text
API
 ↓
Application Service
 ↓
Learning Engine
 ↓
RAG / AI / Progress / Review
```

This keeps educational policy server-side and consistent.

------------------------------------------------------------------------

# 37. AI Request Manager Placement

The AI Request Manager should also live backend-side.

Responsibilities:

-   Priority
-   Concurrency
-   Queue
-   Cancellation
-   Timeout
-   Retry
-   Diagnostics

------------------------------------------------------------------------

# 38. RAG Placement

RAG logic should live server-side.

The frontend may request:

> "Explain according to my selected material."

But the backend determines:

-   Allowed material
-   Retrieval scope
-   Grounding mode
-   Evidence Package
-   Source references

------------------------------------------------------------------------

# 39. Caching Architecture

Potential cache layers:

## Application Cache

Examples:

-   Stable material metadata
-   Topic summaries
-   Prompt templates

## Retrieval Cache

Examples:

-   Stable non-personalized retrieval results
-   Source hierarchy lookup

## AI Result Cache

Only where safe.

Examples:

-   Grounded generic explanation for same source version/objective

Avoid caching:

-   Personalized feedback
-   Current answer evaluation
-   Learner-specific next action

------------------------------------------------------------------------

# 40. Cache Correctness Rule

Cache keys must include sufficient context.

Example:

``` text
sourceVersion
concept
taskType
promptVersion
modelVersion
groundingMode
```

Incorrect caching can be worse than no caching.

------------------------------------------------------------------------

# 41. Observability Architecture

All major subsystems should emit structured operational information.

Track:

-   Request latency
-   AI/provider latency
-   Retrieval latency
-   Queue time
-   Background job state
-   Material processing progress
-   Validation failures
-   AI task type
-   Prompt version
-   Model version
-   Retrieval quality
-   Failure category

Detailed tooling belongs to 24.

------------------------------------------------------------------------

# 42. Auditability

Important generated educational artifacts should be traceable to:

``` text
Prompt Version
+
Model Version
+
Source References
+
Grounding Mode
+
Validation Status
```

This supports AI evaluation and debugging.

------------------------------------------------------------------------

# 43. Deployment Shape --- MVP

A reasonable initial deployment shape is:

``` text
┌─────────────────────────────┐
│        Web Frontend         │
└─────────────┬───────────────┘
              │
              ▼
┌─────────────────────────────┐
│     Spring Boot Backend     │
│                             │
│ API                         │
│ Learning Engine             │
│ AI Orchestration            │
│ RAG                         │
│ Background Jobs             │
└──────┬────────┬────────┬─────┘
       │        │        │
       ▼        ▼        ▼
      DB      Files    Search
                │
                ▼
              Ollama
```

This may run on one or a small number of hosts initially.

------------------------------------------------------------------------

# 44. Do Not Start With Kubernetes

For the approximate 40-user MVP target, avoid:

-   Kubernetes
-   Service mesh
-   Distributed tracing infrastructure that outweighs the product
-   Multiple AI microservices
-   Complex message-broker topology
-   Multi-region deployment

unless actual requirements prove necessary.

------------------------------------------------------------------------

# 45. Scale-Up Path

If usage grows:

``` text
Single Backend / Shared Host
        ↓
Dedicated Database
        ↓
Dedicated Background Worker
        ↓
Higher Provider Quotas / Improved Routing
        ↓
Separate Search Infrastructure
        ↓
Horizontal Application Scaling
```

The modular monolith should allow selective extraction later.

------------------------------------------------------------------------

# 46. Scalability Boundary

The first scaling bottleneck is expected to be:

``` text
External AI quota / rate limits / inference latency
```

not:

``` text
Subject CRUD
```

Therefore optimization effort should prioritize:

-   AI concurrency
-   Context size
-   Retrieval efficiency
-   Background-job scheduling
-   Caching
-   Provider/model/quota selection

before introducing broad distributed architecture.

------------------------------------------------------------------------

# 47. Availability Boundary

MVP should prioritize recoverable operation rather than enterprise-grade
high availability.

Critical requirements:

-   No silent data loss
-   Recoverable background jobs
-   Transparent AI failure
-   Persistent learning state
-   Safe retry behavior

------------------------------------------------------------------------

# 48. Transaction Boundaries

Persistent state changes should be transactional where consistency
matters.

Examples:

-   Submit attempt + create evidence event
-   Update mission state + activity completion
-   MaterialVersion activation + index generation metadata
-   Review completion + evidence update

Exact transaction design belongs to Backend/Database Architecture.

------------------------------------------------------------------------

# 49. Idempotency

Asynchronous and retryable workflows should be idempotent where
possible.

Examples:

-   Material ingestion
-   Embedding generation
-   Re-indexing
-   Generated artifact persistence

The system must avoid duplicate evidence or duplicate indexing due to
retries.

------------------------------------------------------------------------

# 50. Internal Contracts

Modules should communicate through explicit application/domain contracts
rather than arbitrary cross-module database access.

Example:

``` text
Learning Module
   ↓
RetrievalService
   ↓
EvidencePackage
```

Not:

``` text
Learning Module directly queries vector tables everywhere
```

------------------------------------------------------------------------

# 51. Module Dependency Direction

Recommended conceptual direction:

``` text
API / Presentation
        ↓
Application Services
        ↓
Domain Logic
        ↓
Ports / Interfaces
        ↓
Infrastructure Adapters
```

This enables:

-   Testing
-   Model replacement
-   Storage replacement
-   Clear ownership

Exact clean/hexagonal architecture detail belongs to Backend
Architecture.

------------------------------------------------------------------------

# 52. External Dependency Isolation

External technologies should be behind adapters.

Examples:

``` text
Ollama
Vector Search
File Storage
Email / future notifications
Authentication provider
```

This reduces vendor/runtime lock-in.

------------------------------------------------------------------------

# 53. Configuration

Environment-specific values should be externalized.

Examples:

-   AI model
-   Embedding model
-   Concurrency
-   Queue limits
-   File limits
-   Storage paths
-   Search settings
-   Timeouts

Source code should not hard-code environment deployment details.

------------------------------------------------------------------------

# 54. Development Environment

Developers should be able to run a practical local environment with:

``` text
Frontend
Spring Boot
Database
Local/File Storage
Vector/Search capability
External Ollama API + Gemini API development credentials
```

without requiring cloud infrastructure.

Local self-hosted Ollama is not required for v1 development.

------------------------------------------------------------------------

# 55. Data Flow --- Study Mission

``` mermaid
flowchart TD

A[Student Opens Topic]
--> B[Backend Loads Topic Context]

B --> C[Learning Engine]
C --> D[Mission State + Learning Evidence]

D --> E{Need AI?}

E -->|No| F[Deterministic Activity]
E -->|Yes| G[RAG Evidence]

G --> H[Prompt Builder]
H --> I[Ollama]
I --> J[Output Validator]
J --> K[Learning Engine]

F --> L[Return Activity]
K --> L

L --> M[Student Responds]
M --> N[Persist Attempt]
N --> O[Update Evidence]
O --> C
```

------------------------------------------------------------------------

# 56. Data Flow --- Material to Learning

``` mermaid
flowchart LR

A[Original Material]
--> B[MaterialVersion]

B --> C[Document Structure]
C --> D[Chunks]
C --> E[Visual Assets]

D --> F[Embeddings]
F --> G[Search Index]

D --> H[Evidence Package]
E --> H

H --> I[Prompt]
I --> J[Generated Educational Artifact]

J --> K[Learning Activity]
K --> L[Student Attempt]
L --> M[Learning Evidence]
```

------------------------------------------------------------------------

# 57. Runtime Component Diagram

``` mermaid
flowchart TB

subgraph Client
    UI[Web Frontend]
end

subgraph Backend["Spring Boot Application"]
    API[API Layer]
    Learn[Learning Module]
    Progress[Progress Module]
    Review[Review Module]
    Materials[Materials Module]
    RAG[RAG Module]
    AI[AI Module]
    Jobs[Background Job Layer]
end

DB[(Relational DB)]
Files[(File Storage)]
Search[(Vector/Search)]
Ollama[Ollama]

UI --> API

API --> Learn
API --> Materials
API --> Progress
API --> Review

Learn --> Progress
Learn --> Review
Learn --> RAG
Learn --> AI

Materials --> Jobs
Jobs --> DB
Jobs --> Files
Jobs --> Search
Jobs --> Ollama

RAG --> Search
RAG --> DB

AI --> Ollama

Progress --> DB
Review --> DB
Materials --> DB
Materials --> Files
```

------------------------------------------------------------------------

# 58. MVP Performance Shape

The system should support approximately 40 users by separating:

``` text
Application Concurrency
```

from:

``` text
AI Inference Concurrency
```

Most product interactions should remain cheap.

AI work is bounded and queued.

------------------------------------------------------------------------

# 59. Capacity Principle

> **The application can support more concurrent users than the AI
> runtime can support simultaneous generations.**

This is possible because:

-   Navigation is deterministic
-   Progress retrieval is deterministic
-   Review scheduling is deterministic
-   Existing content can be reused
-   Source material is processed once
-   Embeddings are reused
-   AI is invoked selectively

------------------------------------------------------------------------

# 60. Resource Priority

When constrained:

``` text
Active Student Feedback
        >
Interactive Explanation
        >
Next Activity Preparation
        >
Background Embedding
        >
Maintenance
```

------------------------------------------------------------------------

# 61. Security Zones

Conceptually:

``` text
Public Browser
   ↓
Authenticated API Boundary
   ↓
Application Domain
   ↓
Protected Persistence
   ↓
Protected Provider Integration Boundary
```

Database and vector storage must not be directly internet-exposed.
External AI providers are accessed only through protected backend
integrations.

------------------------------------------------------------------------

# 62. Privacy Boundary

Student source materials and learning evidence must remain private by
default.

The architecture should minimize replication of private source content
across unnecessary systems.

------------------------------------------------------------------------

# 63. File Lifecycle

Original material:

``` text
Upload
 ↓
Store
 ↓
Process
 ↓
Reference
 ↓
Delete when user/policy requires
```

Derived artifacts should follow source lifecycle rules where
appropriate.

------------------------------------------------------------------------

# 64. Search Lifecycle

``` text
MaterialVersion Ready
 ↓
Index Generation
 ↓
Active Retrieval
 ↓
Material Replaced/Deleted
 ↓
Deactivate / Remove Index Records
```

No active orphan search records.

------------------------------------------------------------------------

# 65. AI Model Lifecycle

``` text
Model Candidate
 ↓
Evaluate
 ↓
Approve
 ↓
Configure
 ↓
Monitor
 ↓
Replace / Upgrade
```

Model replacement should not require changing Learning Engine behavior.

------------------------------------------------------------------------

# 66. Prompt Lifecycle

``` text
Prompt V1
 ↓
Evaluate
 ↓
Release
 ↓
Monitor
 ↓
Prompt V2
 ↓
Regression Test
 ↓
Promote / Roll Back
```

Prompt versions are configuration/artifact concerns, not scattered
hard-coded strings.

------------------------------------------------------------------------

# 67. Architecture Quality Goals

The system architecture must support:

-   Testability
-   Replaceable AI/runtime
-   Replaceable storage
-   Clear domain ownership
-   Recoverable background jobs
-   Traceable AI outputs
-   Deterministic learning rules
-   User data isolation
-   Simple local development
-   Controlled scale-up

------------------------------------------------------------------------

# 68. Architecture Anti-Patterns

Avoid:

## 68.1 Generic AI-Centric Backend

Where every request becomes:

``` text
Controller → LLM
```

## 68.2 Frontend-Owned Pedagogy

Where React decides review, mastery, or mission logic.

## 68.3 Direct Browser-to-Ollama

This bypasses security, prompt control, and validation.

## 68.4 Vector Database as Source of Truth

Search indexes must remain rebuildable.

## 68.5 Microservices Too Early

Do not split domains before operational need exists.

## 68.6 AI-Driven Persistent State

Do not let model prose directly define progress/review/mastery.

## 68.7 Synchronous Large-File Processing

Do not process huge PDFs inside one blocking request.

## 68.8 Shared Retrieval Without Ownership Filters

Cross-user leakage is unacceptable.

------------------------------------------------------------------------

# 69. MVP Architecture Boundary

v1 architecture includes:

-   Web frontend
-   Spring Boot modular monolith
-   Authentication/authorization boundary
-   Relational persistence
-   File storage
-   Vector/search capability
-   Ollama
-   Background processing
-   Learning Engine
-   RAG
-   Prompt system
-   AI output validation
-   Learning evidence
-   Review
-   Observability basics

------------------------------------------------------------------------

# 70. Deferred Architecture Complexity

Not required for v1:

-   Microservices
-   Kubernetes
-   Distributed event streaming
-   Multi-region failover
-   Service mesh
-   Dedicated graph database
-   Separate AI orchestration service
-   Separate RAG microservice
-   Large-scale distributed vector database
-   Complex API gateway topology
-   Event sourcing
-   CQRS

These may be considered only if later evidence justifies them.

------------------------------------------------------------------------

# 71. Locked v1 System Architecture Decisions

The following are approved for v1:

1.  Hippocampus uses a modular monolith for the primary backend.
2.  Spring Boot is the central backend application boundary.
3.  The frontend communicates only with the backend.
4.  v1 uses two external AI providers: Ollama API and Google Gemini API.
5.  Ollama is not self-hosted locally for v1.
6.  Provider API credentials remain server-side only.
7.  External AI integrations are hidden behind provider adapters.
8.  A backend Provider Router selects an approved provider/model for
    typed AI tasks.
9.  Provider routing is configurable and evaluation-driven.
10. Provider fallback must preserve grounding, Evidence Package, output
    schema, validation, and safety behavior.
11. Learning Engine is server-side and application-owned.
12. RAG is server-side and application-owned.
13. AI orchestration is server-side and application-owned.
14. Structured relational data, binary source files, and search/vector
    data remain distinct persistence concerns.
15. The search/vector index is not the authoritative source of truth.
16. Large ingestion work is asynchronous.
17. Interactive learning takes priority over background work.
18. AI requests are bounded and queued application-side.
19. Application concurrency and provider concurrency are separate
    capacity dimensions.
20. Approximately 40 users is the initial user-capacity target.
21. Exact AI concurrency is benchmarked against provider quotas, rate
    limits, latency, and selected models.
22. Cross-user isolation is enforced before retrieval.
23. Deterministic features remain available when AI providers are
    unavailable where safe.
24. AI failures degrade transparently.
25. External dependencies are isolated behind adapters.
26. Prompt, provider, and model versions remain observable.
27. Material ingestion is retryable and idempotent where practical.
28. Search indexes are rebuildable.
29. Topic reorganization does not require source reprocessing.
30. The application stack supports local development without locally
    hosted AI inference.
31. Infrastructure should remain simple for MVP.
32. Kubernetes and microservices are explicitly deferred.
33. Future scaling should address actual provider quotas and bottlenecks
    selectively.
34. AI providers may change without redefining learning logic.
35. The database remains authoritative for learning state.
36. Free-tier provider capacity is treated as limited and changeable.
37. Provider usage, rate limits, failures, latency, and cost must be
    observable.
38. The architecture must preserve the educational product defined in
    Documents 00--15.

# 72. Out of Scope

This document does not define:

-   Exact framework versions
-   Exact database choice
-   Exact vector search technology
-   Exact storage provider
-   Exact authentication provider
-   Exact package names
-   Exact REST endpoints
-   Exact deployment host
-   Exact job implementation
-   Exact monitoring stack
-   Exact CI/CD
-   Exact schema
-   Exact frontend framework details
-   Exact hardware

Those decisions belong to subsequent documents.

------------------------------------------------------------------------

# 73. Related Documents

-   00 - Project Vision
-   01 - Guiding Principles
-   02 - Problem Statement
-   03 - Educational Foundation
-   04 - Product Requirements
-   05 - User Personas
-   06 - User Journey & Learning Flow
-   07 - Feature Specifications
-   08 - Non-Functional Requirements
-   09 - MVP Scope & Roadmap
-   10 - AI Architecture
-   11 - AI Learning Engine
-   12 - Prompt Engineering Strategy
-   13 - RAG Architecture
-   14 - Knowledge Base Design
-   15 - AI Evaluation Strategy
-   17 - Technology Stack & ADR Baseline
-   18 - Domain Model & Database Design
-   19 - Backend Architecture
-   20 - Frontend Architecture
-   21 - File Processing & Ingestion Architecture
-   22 - Security & Privacy Architecture
-   23 - Deployment & Infrastructure
-   24 - Observability & Operations
-   25 - Testing Strategy
-   26 - Development Roadmap & Implementation Phases
-   27 - Decision Log / ADR Index

------------------------------------------------------------------------

# 74. Next Document

**17 - Technology Stack & ADR Baseline**

The next document should formally select and justify the implementation
stack.

It should cover decisions such as:

-   Java version
-   Spring Boot version
-   Spring AI
-   Frontend framework
-   Database
-   Vector capability
-   File storage
-   AI provider integration (Ollama API + Google Gemini API)
-   Embedding provider/runtime
-   Background job mechanism
-   Authentication strategy
-   Build tooling
-   Testing stack
-   Local-development tooling

Each important selection should be captured as an architectural decision
with rationale and tradeoffs.

------------------------------------------------------------------------

# 75. Revision History

  ------------------------------------------------------------------------
  Version           Date              Author            Changes
  ----------------- ----------------- ----------------- ------------------
  1.1.1             2026-08-24        Project           Final consistency
                                      Hippocampus Team  terminology patch
                                                        for provider and
                                                        embedding runtime.
  1.1.0             2026-08-23        Project           Revised system
                                      Hippocampus Team  architecture from
                                                        local Ollama
                                                        runtime to
                                                        external
                                                        dual-provider AI
                                                        gateway using
                                                        Ollama API +
                                                        Google Gemini API;
                                                        added Provider
                                                        Router,
                                                        server-side
                                                        credential
                                                        boundary,
                                                        quota/rate-limit
                                                        capacity model,
                                                        and provider
                                                        fallback
                                                        invariants.

  1.0.0             2026-08-23        Project           Initial finalized
                                      Hippocampus Team  System
                                                        Architecture
                                                        establishing
                                                        modular-monolith
                                                        boundaries,
                                                        backend learning
                                                        authority, local
                                                        Ollama
                                                        integration,
                                                        RAG/persistence
                                                        separation,
                                                        background
                                                        processing,
                                                        failure
                                                        containment, and
                                                        approximately
                                                        40-user MVP
                                                        architecture
  ------------------------------------------------------------------------

------------------------------------------------------------------------

# 76. Approval

**Status:** Final

**Reviewed By:** Project Hippocampus Team

**Approved By:** Project Hippocampus Team
