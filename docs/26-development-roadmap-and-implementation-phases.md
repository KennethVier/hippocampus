---
Audience: Architecture, backend, frontend, AI/RAG, DevOps, security, QA,
  product contributors, and coding agents.
Authors: Project Hippocampus Team
Created: 2026-08-24
Document ID: 26
Last Updated: 2026-08-24
Owner: Project Hippocampus Team
Prerequisites:
- README
- 00 through 25 approved Hippocampus v1 documents
Purpose: Convert the approved Hippocampus v1 product, educational, AI,
  data, security, infrastructure, and testing architecture into an
  executable engineering sequence with explicit phase scope,
  dependencies, milestones, exit criteria, and implementation
  governance.
Related Documents:
- 27 - Decision Log / ADR Index
Scope: Documentation freeze, implementation authority, phase sequencing,
  engineering foundation, identity, learning organization, ingestion,
  RAG, AI infrastructure, Learning Engine, Study Missions, learning
  evidence and review, multimodal learning, complete student UX,
  security hardening, evaluation, performance, pilot readiness,
  milestone releases, phase completion rules, and coding-agent
  governance.
Status: Final
Title: Development Roadmap & Implementation Phases
Version: 1.0.0
---

# 26 - Development Roadmap & Implementation Phases

## 1. Purpose

Documents 00--25 define:

> **What are we building, why are we building it, and how should it
> work?**

Document 26 defines:

> **In exactly what order should Hippocampus v1 be built?**

This roadmap converts the approved Source of Truth into an
implementation sequence.

------------------------------------------------------------------------

# 2. Locked Roadmap Principle

> **Every major phase must leave Hippocampus in a coherent, testable
> state and move the product closer to a real student learning flow.**

The project should not implement every database table first, then every
backend endpoint, then every frontend screen.

Prefer vertical capabilities while respecting architectural
dependencies.

------------------------------------------------------------------------

# 3. Implementation Authority

The implementation authority hierarchy is:

``` text
00–15
Product + Educational Authority
        ↓
16–25
Technical Authority
        ↓
26
Implementation Order
        ↓
Approved ADRs
        ↓
Code
```

Code does not override approved architecture.

------------------------------------------------------------------------

# 4. Coding-Agent Governance

Coding agents, including Codex/Cursor-style agents, must not:

-   redesign Hippocampus;
-   introduce undocumented features;
-   expand MVP scope;
-   invent domain entities;
-   create new architectural patterns without approval;
-   bypass security requirements;
-   replace deterministic learning policy with LLM decisions;
-   change technology decisions because another implementation appears
    easier.

If implementation exposes a genuine architectural problem:

``` text
Stop
↓
Document issue
↓
Review authoritative documents
↓
Create/update ADR if necessary
↓
Approve decision
↓
Continue implementation
```

------------------------------------------------------------------------

# 5. Phase Execution Protocol

Every phase follows:

``` text
Read authoritative documents
        ↓
Identify exact phase scope
        ↓
Write implementation plan
        ↓
Implement
        ↓
Test
        ↓
Review against Source of Truth
        ↓
Update tracker
        ↓
Commit
        ↓
Proceed to next phase
```

No phase is considered complete solely because code compiles.

------------------------------------------------------------------------

# 6. Roadmap Overview

``` text
DOCUMENTATION FREEZE
        │
        ▼
PHASE 0
Engineering Foundation
        │
        ▼
PHASE 1
Identity + Core Student Workspace
        │
        ▼
PHASE 2
Subjects + Topics + Learning Materials
        │
        ▼
PHASE 3
File Processing & Ingestion
        │
        ▼
PHASE 4
Knowledge Base + RAG Foundation
        │
        ▼
PHASE 5
AI Provider & Prompt Infrastructure
        │
        ▼
PHASE 6
AI Learning Engine
        │
        ▼
PHASE 7
Study Missions
        │
        ▼
PHASE 8
Learning Evidence + Review System
        │
        ▼
PHASE 9
Visual & Multimodal Learning
        │
        ▼
PHASE 10
Complete Student Learning Experience
        │
        ▼
PHASE 11
Security + Reliability Hardening
        │
        ▼
PHASE 12
Evaluation + Performance + Pilot Readiness
        │
        ▼
CONTROLLED MEDICAL-STUDENT PILOT
```

------------------------------------------------------------------------

# 7. Cross-Cutting Rule

Testing, security, observability, accessibility, and documentation are
not postponed until later phases.

They are implemented continuously.

Phases 11 and 12 represent comprehensive hardening and validation, not
the first time these concerns are addressed.

------------------------------------------------------------------------

# 8. Documentation Freeze Gate

Before Phase 0:

1.  Complete Document 27.
2.  Run final consistency audit across README and Documents 00--27.
3.  Resolve contradictions.
4.  Update version metadata where required.
5.  Freeze the approved set as:

``` text
Hippocampus v1.0 Source of Truth
```

6.  Create the implementation tracker.

------------------------------------------------------------------------

# 9. Phase 0 --- Engineering Foundation

## Goal

Create the technical foundation on which every later capability depends.

## Backend Foundation

Establish the approved stack and architecture, including:

``` text
Java
Spring Boot
Spring Security foundation
Spring Data JPA
PostgreSQL
pgvector
Flyway
Maven
Docker
Testcontainers
ArchUnit
```

Implement module/package boundaries defined by Document 19.

## Frontend Foundation

Establish:

``` text
React
Vite
TypeScript
Tailwind
React Router
TanStack Query
Zustand where appropriate
React Hook Form
Zod
Vitest
React Testing Library
Playwright
```

Implement:

-   application shell;
-   routing;
-   API client;
-   design tokens;
-   reusable core components;
-   loading state;
-   empty state;
-   error state;
-   environment configuration.

## Local Infrastructure

Create local:

``` text
PostgreSQL + pgvector
```

using Docker.

Local file/object-storage abstraction must remain compatible with later
R2 integration.

## CI Foundation

Configure:

``` text
build
lint
format checks
unit tests
architecture tests
dependency scanning
secret scanning
```

## Exit Criteria

-   frontend builds;
-   backend builds;
-   local PostgreSQL works;
-   pgvector works;
-   Flyway works;
-   Testcontainers works;
-   CI passes;
-   architecture tests pass;
-   baseline security scans run.

## Milestone

**M0 --- Engineering Skeleton**

------------------------------------------------------------------------

# 10. Phase 1 --- Identity + Core Student Workspace

## Goal

Establish secure student identity and ownership.

Implement:

``` text
User
Authentication
Session
Authorization
Student Workspace
```

## Backend

Implement:

-   Spring Security;
-   server-side sessions;
-   user identity;
-   login/logout;
-   authorization policies;
-   ownership foundations;
-   CSRF;
-   CORS;
-   secure session configuration.

## Frontend

Implement:

-   login;
-   logout;
-   authenticated route handling;
-   session expiry;
-   authenticated application layout.

## Security

Apply from the beginning:

``` text
HttpOnly
Secure
SameSite
CSRF
CORS
server-side ownership
```

## Exit Criteria

A student can authenticate and securely enter a private Hippocampus
workspace.

Cross-user access tests pass.

------------------------------------------------------------------------

# 11. Phase 2 --- Subjects, Topics & Learning Materials

## Goal

Allow students to organize medical learning.

Implement the initial organization hierarchy:

``` text
Student
↓
Subject
↓
Topic
↓
Material
```

## Student Capabilities

-   create subject;
-   edit subject;
-   create topic;
-   edit topic;
-   upload material;
-   view material metadata;
-   delete material.

Full ingestion intelligence is not required yet.

## Backend

Implement the relevant domain/application/repository/API boundaries from
Documents 18--19.

## Frontend

Implement organization and material-management flows from Documents 06,
07, and 20.

## Security

Every resource is ownership-scoped.

## Exit Criteria

A student can privately organize subjects, topics, and uploaded medical
learning materials.

------------------------------------------------------------------------

# 12. Phase 3 --- File Processing & Ingestion

## Goal

Transform uploaded learning materials into structured, traceable source
knowledge.

Implement:

``` text
Upload
↓
Validate
↓
Store Original
↓
Extract
↓
Detect Structure
↓
Normalize
↓
Extract Visuals
↓
Chunk
↓
Prepare for Indexing
```

## Core Domain/Data

Implement as defined by Documents 18 and 21:

``` text
MaterialVersion
ProcessingJob
DocumentNode
TextBlock
VisualAsset
Chunk
```

## Supported MVP Inputs

-   native PDFs;
-   scanned PDFs;
-   mixed PDFs;
-   images;
-   transcripts/text sources as defined by the approved MVP.

## Large Document Requirements

Support 600+ page materials through:

``` text
batch processing
bounded memory
progress
heartbeat
retry
resume
idempotency
partial readiness
```

## Storage

Original/derived binaries use the approved object-storage abstraction.

Local development uses local implementation.

PILOT uses R2.

## Exit Criteria

A large medical textbook can be transformed into structured, traceable
learning units without loading the entire document into memory.

## Milestone

**M1 --- Material Workspace**

``` text
Upload
↓
Process
↓
Structure
```

------------------------------------------------------------------------

# 13. Phase 4 --- Knowledge Base + RAG Foundation

## Goal

Make structured source knowledge reliably retrievable before adding
generative AI.

Implement:

``` text
Embeddings
pgvector
PostgreSQL FTS
pg_trgm
Hybrid Retrieval
Reranking
EvidencePackage
SourceReference
```

## Retrieval Pipeline

``` text
Retrieval Intent
↓
Ownership Scope
↓
Lexical Retrieval
+
Vector Retrieval
↓
Merge
↓
Rerank
↓
Evidence Selection
↓
EvidencePackage
```

## Critical Rule

Authorization occurs before semantic ranking.

The LLM never performs ownership filtering.

## Development Diagnostic

Developers should be able to inspect retrieval results directly without
an LLM.

Example:

``` text
Query:
cardiac action potential phases

Result:
relevant authorized chunks
source hierarchy
scores
page references
```

## Evaluation

Introduce the first Golden Retrieval Dataset.

## Exit Criteria

Given a topic/query, Hippocampus reliably retrieves correct authorized
evidence.

Cross-user retrieval leakage:

``` text
0
```

------------------------------------------------------------------------

# 14. Phase 5 --- AI Provider & Prompt Infrastructure

## Goal

Introduce external AI as a replaceable, controlled supporting
capability.

Implement:

``` text
AIProvider
AIProviderRouter
Gemini Adapter
Ollama Adapter
Prompt Registry
Prompt Versioning
Task Contracts
Structured Output Validation
Retry
Timeout
Fallback
Usage Tracking
```

## Initial Task Contracts

Implement individually:

``` text
EXPLANATION
QUESTION_GENERATION
RESPONSE_EVALUATION
CONCEPT_CONNECTION
CONTEXTUAL_APPLICATION
```

Other approved task contracts may follow according to Documents 10--12.

## Critical Rule

AI does not own:

-   authorization;
-   learning state;
-   mastery;
-   review scheduling;
-   mission completion;
-   evidence truth.

## Provider Testing

Both providers must satisfy shared contract tests where supported.

## Exit Criteria

A compatible AI task can execute through Gemini or Ollama without domain
code depending on the concrete provider.

## Milestone

**M2 --- Grounded Intelligence**

``` text
Material
↓
Retrieve
↓
EvidencePackage
↓
AI Task
```

------------------------------------------------------------------------

# 15. Phase 6 --- AI Learning Engine

## Goal

Implement the deterministic pedagogical decision engine.

This phase answers:

> **What should the student do next?**

not:

> **What should the LLM say next?**

## Implement

``` text
LearningState
LearningAction
LearningEngine
LearningPolicy
Scaffolding Policy
Difficulty Policy
Anti-Repetition Policy
Misconception Policy
```

## Inputs

``` text
Mission State
+
Learning Evidence
+
Source Capability
+
Time/Review Context
```

## Output

``` text
NextLearningAction
```

Potential actions include:

``` text
UNDERSTAND
RETRIEVE
CONNECT
APPLY
REFLECT
REVIEW
```

according to approved educational architecture.

## Testing

This phase requires extensive deterministic unit testing.

AI must not determine the learning-policy decision.

## Exit Criteria

Given deterministic student evidence and mission state, Hippocampus
selects an appropriate next learning action without outsourcing
pedagogical control to an LLM.

------------------------------------------------------------------------

# 16. Phase 7 --- Study Missions

## Goal

Combine source knowledge, retrieval, AI support, and the Learning Engine
into the core Hippocampus learning experience.

Implement:

``` text
StudyMission
MissionPlan
LearningActivity
Attempt
Feedback
```

## Core Experience

``` text
Choose Topic
↓
Start Study Mission
↓
Understand
↓
Retrieve
↓
Connect
↓
Apply
↓
Receive Formative Feedback
↓
Learning Engine
↓
Next Learning Action
```

The exact path may adapt according to evidence and policy.

## Product Boundary

The resulting experience must not collapse into:

``` text
Upload PDF → Chat
```

or:

``` text
Upload PDF → Quiz Generator
```

Study Missions remain the organizing learning mechanism.

## Exit Criteria

A student can complete a full source-grounded Study Mission.

## Milestone

**M3 --- First Hippocampus**

This is the first major internal product milestone.

------------------------------------------------------------------------

# 17. Phase 8 --- Learning Evidence + Review System

## Goal

Allow Hippocampus to remember learning evidence over time.

Implement:

``` text
EvidenceEvent
LearningEvidence
ReviewRecord
MisconceptionRecord
```

## Evidence Dimensions

Track approved evidence dimensions such as:

``` text
Recall
Understanding
Connection
Application
```

without reducing learning to a single opaque score.

## Review

Determine:

``` text
what should be reviewed
why
when
what type of activity is appropriate
```

based on learning evidence.

## Longitudinal Behavior

Future Study Missions may respond to prior evidence.

Example:

``` text
Recall = stable
Application = weak
```

should favor appropriate application practice rather than unnecessary
basic recall.

## Exit Criteria

Learning evidence from one session can appropriately influence later
learning/review behavior.

## Milestone

**M4 --- Adaptive Hippocampus**

``` text
Study Mission
↓
Evidence
↓
Review
↓
Future Mission Adapts
```

------------------------------------------------------------------------

# 18. Phase 9 --- Visual & Multimodal Learning

## Goal

Turn medical visuals into active learning evidence rather than
decoration.

Use approved extracted assets such as:

-   anatomy diagrams;
-   histology images;
-   pathology figures;
-   tables;
-   graphs;
-   medical illustrations.

## Linkage

Visuals remain connected to:

``` text
MaterialVersion
DocumentNode
Chunk
Topic
SourceReference
```

## Learning Activities

Examples may include:

``` text
Observe Diagram
↓
Identify Structure
↓
Retrieve Function
↓
Connect Relationship
↓
Apply Scenario
```

according to source capability and learner state.

## Exit Criteria

Visual source material can participate meaningfully in retrieval and
Study Mission activities with preserved provenance.

------------------------------------------------------------------------

# 19. Phase 10 --- Complete Student Learning Experience

## Goal

Integrate all core v1 capabilities into the complete student journey
defined by Documents 05--07.

## Journey

``` text
Dashboard
↓
Subject
↓
Topic
↓
Material
↓
Study Mission
↓
Learning Activities
↓
Feedback
↓
Evidence
↓
Review
↓
Progress
```

## Complete

-   responsive navigation;
-   material processing UX;
-   source exploration;
-   mission experience;
-   activity states;
-   feedback presentation;
-   review experience;
-   progress/evidence representation;
-   loading;
-   empty states;
-   error states;
-   accessibility;
-   tablet/mobile usability;
-   backend cold-start UX.

## Exit Criteria

Hippocampus behaves as a coherent end-to-end medical learning
application rather than a collection of technical capabilities.

------------------------------------------------------------------------

# 20. Phase 11 --- Security & Reliability Hardening

## Goal

Deliberately attack and stress the implemented system.

Security has existed since Phase 0.

This phase performs comprehensive hardening.

## Security Validation

Run the strategy defined by Documents 22, 24, and 25:

``` text
SAST
SCA
Secret Scanning
Container Scanning
DAST
IDOR
CSRF
CORS
XSS
Upload Security
Path Traversal
Parser Resource Attacks
Prompt Injection
RAG Isolation
Source Reference Forgery
Rate Limiting
```

## Resilience Validation

Simulate:

``` text
Gemini unavailable
Ollama unavailable
Both AI providers unavailable
Neon unavailable
R2 unavailable
Backend restart
Processing restart
Provider rate limits
```

## Data Protection

Verify:

-   session security;
-   private object access;
-   deletion;
-   retrieval deactivation;
-   log minimization;
-   secret handling.

## Exit Criteria

No unresolved release-blocking security or reliability issue.

------------------------------------------------------------------------

# 21. Phase 12 --- Evaluation + Performance + Pilot Readiness

## Goal

Generate the final technical evidence required before inviting real
medical students.

## RAG Evaluation

Run the approved Golden Retrieval Dataset.

## AI Evaluation

Run task-specific Golden AI datasets under Document 15.

## Ingestion

Validate:

-   large PDFs;
-   scanned PDFs;
-   mixed PDFs;
-   tables;
-   visuals;
-   restart/recovery.

## Load

Test representative profiles:

``` text
10 students
20 students
40 students
```

## Infrastructure

Measure:

``` text
Neon storage growth
Neon compute behavior
Render responsiveness
Render cold starts
R2 storage/operations
Gemini usage
Ollama usage
processing queue
```

## Backup

``` text
Backup
↓
Restore to isolated environment
↓
Integrity verification
```

## E2E

Run the complete student journey.

## Exit Criteria

All applicable Document 25 controlled-pilot release gates pass.

## Milestone

**M5 --- Pilot Candidate**

``` text
Hippocampus v1.0.0-RC
```

------------------------------------------------------------------------

# 22. Controlled Medical-Student Pilot

After Phase 12, begin a controlled pilot.

The pilot is not merely testing:

> Do students like the interface?

The product hypothesis is:

> **Does Hippocampus help medical students understand, retrieve,
> connect, apply, and retain medical knowledge more effectively?**

Pilot evaluation follows the educational and AI evaluation architecture
already approved.

------------------------------------------------------------------------

# 23. Pilot Scope

Initial infrastructure is designed around approximately:

``` text
up to ~40 invited medical students
```

subject to validated:

-   upload quotas;
-   database capacity;
-   AI provider quotas;
-   Render compute;
-   observed workload.

This is not an unlimited-use guarantee.

------------------------------------------------------------------------

# 24. Milestone Releases

## M0 --- Engineering Skeleton

After Phase 0.

## M1 --- Material Workspace

After Phase 3.

``` text
Upload → Process → Structure
```

## M2 --- Grounded Intelligence

After Phase 5.

``` text
Material → Retrieve → AI
```

## M3 --- First Hippocampus

After Phase 7.

``` text
Source Material
↓
Study Mission
↓
Understand → Retrieve → Connect → Apply
```

## M4 --- Adaptive Hippocampus

After Phase 8.

``` text
Study Mission
↓
Learning Evidence
↓
Review
↓
Future Learning Adapts
```

## M5 --- Pilot Candidate

After Phase 12.

``` text
Hippocampus v1.0.0-RC
```

------------------------------------------------------------------------

# 25. Dependency Map

``` mermaid
flowchart TD

P0[Phase 0<br/>Engineering Foundation]
P1[Phase 1<br/>Identity]
P2[Phase 2<br/>Learning Organization]
P3[Phase 3<br/>Ingestion]
P4[Phase 4<br/>RAG]
P5[Phase 5<br/>AI Infrastructure]
P6[Phase 6<br/>Learning Engine]
P7[Phase 7<br/>Study Missions]
P8[Phase 8<br/>Evidence + Review]
P9[Phase 9<br/>Visual Learning]
P10[Phase 10<br/>Complete UX]
P11[Phase 11<br/>Security + Reliability]
P12[Phase 12<br/>Pilot Validation]
Pilot[Controlled Medical-Student Pilot]

P0 --> P1
P1 --> P2
P2 --> P3
P3 --> P4
P4 --> P5
P5 --> P6
P6 --> P7
P5 --> P7
P7 --> P8
P8 --> P9
P9 --> P10
P10 --> P11
P11 --> P12
P12 --> Pilot
```

------------------------------------------------------------------------

# 26. Phase Completion Definition

A phase is complete only when:

1.  Phase scope is implemented.
2.  Required migrations exist.
3.  Required frontend/backend contracts exist.
4.  Automated tests pass.
5.  Security requirements applicable to the phase pass.
6.  Architecture tests pass.
7.  No undocumented architectural deviation exists.
8.  Documentation remains consistent.
9.  Known limitations are recorded.
10. Tracker is updated.
11. Code is reviewed.
12. Phase commit/release milestone is created as appropriate.

------------------------------------------------------------------------

# 27. Phase Scope Protection

During a phase:

``` text
New idea appears
↓
Is required by current Source of Truth?
```

If yes:

``` text
Implement if within phase dependency/scope
```

If no:

``` text
Record for future consideration
Do not expand MVP automatically
```

------------------------------------------------------------------------

# 28. Architecture Deviation Process

If code cannot reasonably implement an approved architecture:

``` text
Identify conflict
↓
Do not silently work around it
↓
Locate authoritative document
↓
Propose change
↓
Assess downstream impact
↓
Create/update ADR
↓
Approve
↓
Patch affected Source-of-Truth documents
↓
Implement
```

------------------------------------------------------------------------

# 29. Feature Request During Development

A new feature request must answer:

1.  Which documented problem does it solve?
2.  Is it aligned with the educational foundation?
3.  Is it required for MVP?
4.  Does it alter the domain model?
5.  Does it alter AI/RAG architecture?
6.  Does it introduce security/privacy implications?
7.  Does it change roadmap priority?

If not MVP-critical, defer it.

------------------------------------------------------------------------

# 30. Testing Throughout the Roadmap

Testing is incremental.

Example:

## Phase 1

Auth/authorization/security tests.

## Phase 3

Ingestion fixtures/recovery tests.

## Phase 4

Golden RAG + isolation.

## Phase 5

Provider contract/prompt tests.

## Phase 6

Learning Engine deterministic tests.

## Phase 7

Study Mission E2E.

## Phase 8

Evidence/review regression.

## Phase 11

Comprehensive security/resilience.

## Phase 12

Full release suite.

------------------------------------------------------------------------

# 31. Security Throughout the Roadmap

Security is not a Phase 11-only task.

Every phase must apply Document 22.

Phase 11 asks:

> **What security weakness remains after the system is integrated?**

------------------------------------------------------------------------

# 32. Observability Throughout the Roadmap

Each new capability adds its corresponding telemetry from Document 24.

Examples:

``` text
Phase 3
→ ProcessingJob telemetry

Phase 4
→ retrieval telemetry

Phase 5
→ provider telemetry

Phase 7
→ mission lifecycle telemetry

Phase 8
→ evidence/review consistency signals
```

------------------------------------------------------------------------

# 33. Database Migration Discipline

Every persistent schema change uses Flyway.

Never manually mutate PILOT schema as the normal development workflow.

------------------------------------------------------------------------

# 34. Definition of a Vertical Slice

A vertical slice includes the minimum required:

``` text
Domain
Application
Persistence
API
Frontend
Tests
Security
Observability
```

for a coherent capability.

Not every phase is a single slice, but development within phases should
prefer this structure.

------------------------------------------------------------------------

# 35. Commit Strategy

Prefer meaningful commits aligned with completed capabilities.

Example:

``` text
feat(materials): implement material upload lifecycle
```

rather than large unrelated commits.

Phase completion may use a summary commit/tag according to repository
workflow.

------------------------------------------------------------------------

# 36. Documentation During Implementation

The Source of Truth should not be rewritten casually to match
implementation shortcuts.

Documentation changes occur when:

-   requirement genuinely changes;
-   approved ADR changes architecture;
-   ambiguity is resolved;
-   implementation reveals an incorrect assumption.

------------------------------------------------------------------------

# 37. Technical Debt

Technical debt must be explicit.

Record:

``` text
description
reason
risk
affected phase/component
planned resolution
```

Do not disguise unfinished required architecture as technical debt to
close a phase.

------------------------------------------------------------------------

# 38. Free-Tier Development Constraint

PILOT-FREE infrastructure affects implementation priorities.

Developers should avoid unnecessary:

-   cloud AI calls;
-   repeated embeddings;
-   cloud database usage during local tests;
-   cloud storage during local development.

Use local infrastructure and provider mocks whenever practical.

------------------------------------------------------------------------

# 39. AI Cost Discipline

During development:

``` text
unit tests
→ fake provider

integration
→ fake/local contract provider

evaluation
→ selected live provider calls
```

Do not call Gemini/Ollama for every ordinary CI test.

------------------------------------------------------------------------

# 40. Large PDF Development Discipline

Large-PDF fixtures need not run on every local unit-test cycle.

Use:

``` text
small fixture
→ PR validation

large fixture
→ scheduled/pre-release validation
```

while preserving representative processing tests.

------------------------------------------------------------------------

# 41. MVP Boundary During Implementation

The MVP remains:

> **A student-centered medical learning application that transforms
> supported learning materials into guided Study Missions where students
> understand concepts, actively retrieve knowledge, connect related
> ideas, apply knowledge through appropriately scaffolded medical
> scenarios, receive formative feedback, build learning evidence, and
> revisit weak knowledge over time.**

The MVP is not defined by:

-   chat;
-   summarization;
-   flashcards;
-   quizzes;
-   PDF upload;
-   AI generation.

Those may be mechanisms inside the learning system.

------------------------------------------------------------------------

# 42. Development Success Criterion

Engineering success is not:

``` text
all planned endpoints exist
```

It is:

``` text
the documented student learning system works coherently,
safely,
and measurably
```

------------------------------------------------------------------------

# 43. Roadmap Completion

After Phase 12:

``` text
Hippocampus v1.0.0-RC
↓
Controlled Pilot
↓
Educational + Technical Evidence
↓
Review Findings
↓
v1.0 Production Decision
```

A pilot finding may lead to targeted revisions before declaring a
broader production release.

------------------------------------------------------------------------

# 44. Post-v1 Boundary

Features explicitly deferred from v1 remain deferred until pilot
evidence and product review justify them.

v2 should not be planned merely because v1 coding is complete.

------------------------------------------------------------------------

# 45. Locked v1 Roadmap Decisions

The following are approved:

1.  Document 26 defines implementation order.
2.  Documents 00--15 remain product/educational authority.
3.  Documents 16--25 remain technical authority.
4.  Approved ADRs govern intentional deviations.
5.  Code does not override Source-of-Truth documentation.
6.  Coding agents cannot redesign the application independently.
7.  Development proceeds through 13 phases numbered 0--12.
8.  Phase 0 establishes engineering foundation.
9.  Phase 1 establishes identity and private student ownership.
10. Phase 2 establishes subjects/topics/materials.
11. Phase 3 establishes ingestion.
12. Phase 4 establishes RAG before generative AI.
13. Phase 5 establishes provider/prompt infrastructure.
14. Phase 6 establishes deterministic Learning Engine.
15. Phase 7 establishes Study Missions.
16. Phase 8 establishes longitudinal evidence/review.
17. Phase 9 activates multimodal learning.
18. Phase 10 completes the student learning experience.
19. Phase 11 performs comprehensive security/reliability hardening.
20. Phase 12 performs evaluation/performance/pilot readiness.
21. Security is continuous, not deferred to Phase 11.
22. Testing is continuous, not deferred to Phase 12.
23. Observability is added alongside each capability.
24. Vertical slices are preferred within phase boundaries.
25. RAG must work and be inspectable before AI generation is introduced.
26. AI providers remain replaceable adapters.
27. AI does not own pedagogical policy.
28. Study Missions remain the core learning mechanism.
29. Learning evidence becomes longitudinal in Phase 8.
30. Visuals become learning evidence, not decoration.
31. 600+ page processing is validated before pilot.
32. Free-tier constraints are explicitly tested.
33. Approximately 40-user capacity must be benchmarked, not assumed.
34. Milestone releases M0--M5 provide internal checkpoints.
35. M3 is the first complete Hippocampus Study Mission milestone.
36. M5 is the controlled-pilot release candidate.
37. Phase completion requires implementation, tests, security,
    documentation consistency, and tracker update.
38. New feature ideas do not automatically expand MVP.
39. Architectural deviations require explicit review/ADR.
40. Flyway owns persistent schema evolution.
41. Local development should avoid unnecessary cloud quota consumption.
42. AI live tests are targeted and quota-aware.
43. Large expensive tests may run scheduled/pre-release rather than on
    every PR.
44. Documentation changes reflect approved decisions, not coding
    convenience.
45. Technical debt is explicitly recorded.
46. Pilot begins only after Document 25 release gates pass.
47. Pilot validation measures learning outcomes, not merely engagement.
48. v1 completion does not automatically trigger v2 scope expansion.
49. Final documentation audit occurs before Phase 0.
50. This roadmap remains subordinate to the Hippocampus v1 Source of
    Truth.

------------------------------------------------------------------------

# 46. Out of Scope

This document does not define:

-   sprint duration;
-   calendar deadlines;
-   developer staffing;
-   story points;
-   exact ticket breakdown;
-   exact commit count;
-   exact CI implementation;
-   post-v1 feature roadmap.

These can be created after the Source of Truth is frozen.

------------------------------------------------------------------------

# 47. Final Documentation Step

After Document 26:

1.  Create **27 - Decision Log / ADR Index**.
2.  Run final README + 00--27 consistency audit.
3.  Patch contradictions.
4.  Confirm metadata/version relationships.
5.  Freeze documentation as:

``` text
Hippocampus v1.0 Source of Truth
```

6.  Create implementation tracker.
7.  Begin **Phase 0 --- Engineering Foundation**.

------------------------------------------------------------------------

# 48. Revision History

  ------------------------------------------------------------------------
  Version           Date              Author            Changes
  ----------------- ----------------- ----------------- ------------------
  1.0.0             2026-08-24        Project           Initial finalized
                                      Hippocampus Team  Development
                                                        Roadmap &
                                                        Implementation
                                                        Phases defining
                                                        documentation
                                                        freeze, authority
                                                        hierarchy, phases
                                                        0--12, milestone
                                                        releases M0--M5,
                                                        phase gates,
                                                        coding-agent
                                                        governance,
                                                        vertical-slice
                                                        execution,
                                                        security/testing
                                                        continuity, and
                                                        controlled-pilot
                                                        transition

  ------------------------------------------------------------------------

------------------------------------------------------------------------

# 49. Approval

**Status:** Final

**Reviewed By:** Project Hippocampus Team

**Approved By:** Project Hippocampus Team
