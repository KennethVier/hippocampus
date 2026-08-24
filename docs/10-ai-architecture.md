---
Audience: Product, AI, backend, architecture, DevOps, QA, security, and
  medical-education contributors.
Authors: Project Hippocampus Team
Created: 2026-08-23
Document ID: 10
Last Updated: 2026-08-24
Owner: Project Hippocampus Team
Prerequisites:
- README
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
Purpose: Define how artificial intelligence supports Hippocampus through
  a provider-abstracted, cost-conscious, grounded, safe, replaceable
  architecture using external Ollama API and Google Gemini API providers
  for an initial deployment serving approximately 40 medical-student
  users.
Related Documents:
- 11 - AI Learning Engine
- 12 - Prompt Engineering Strategy
- 13 - RAG Architecture
- 14 - Knowledge Base Design
- 15 - AI Evaluation Strategy
Scope: AI responsibilities, application-vs-AI boundaries, dual-provider
  strategy, provider routing, model roles, task orchestration,
  structured outputs, validation, RAG interaction, context management,
  quotas, rate limiting, queueing, caching, failure handling, multimodal
  boundaries, cost strategy, and scale-up path.
Status: Final
Title: AI Architecture
Version: 1.1.1
---

# 10 - AI Architecture

## 1. Purpose

This document defines how AI participates in Hippocampus.

It answers:

> **How should AI support the Hippocampus learning experience while
> remaining provider-abstracted, cost-conscious, grounded, safe, and
> capable of supporting an initial deployment of approximately 40
> medical-student users?**

The architecture must preserve the product identity established in
documents 00--09.

AI is an enabling subsystem.

AI is not the product.

------------------------------------------------------------------------

# 2. Locked AI Architecture Principle

> **The application owns the learning logic. AI generates educational
> content within constraints defined by the application.**

The architecture must avoid:

``` text
Student
   ↓
LLM
   ↓
Whatever the model decides
```

The intended architecture is:

``` text
Student
   ↓
Hippocampus Learning System
   ↓
Educational Rules
   ↓
Learning Evidence
   ↓
Relevant Source Material
   ↓
AI Task
   ↓
Provider Router
   ↓
Validated Structured Result
   ↓
Application Decision
   ↓
Student
```

This gives us a permanent rule:

> **AI generates. Hippocampus decides.**

------------------------------------------------------------------------

# 3. AI Is Supportive, Not Continuously Required

Hippocampus should not require an LLM call for every student
interaction.

A significant portion of the product can operate deterministically or
from persisted information.

## Application-Owned / Non-Generative Capabilities

Examples include:

-   Subject organization
-   Topic organization
-   Study Mission state
-   Session state
-   Timer
-   Navigation
-   Progress display
-   Existing learning evidence
-   Review eligibility
-   Review priority
-   Previously generated activities
-   Previously generated explanations where reuse is appropriate
-   Source references
-   Deterministic educational rules
-   Duplicate prevention
-   Persistence
-   Session resume

## AI-Assisted Capabilities

AI should be invoked when generative or language reasoning capability
provides meaningful educational value.

Examples include:

-   Alternative explanation
-   Question generation
-   Open-ended response interpretation
-   Concept connection generation
-   Contextualized scenario generation
-   Guided reasoning feedback
-   Material-aware summarization where educationally appropriate
-   Mission-planning assistance

This distinction is important for both educational quality and scale.

> **Forty students using Hippocampus does not imply forty continuously
> active LLM generations.**

------------------------------------------------------------------------

# 4. Initial Capacity Target

The initial product target is:

> **Approximately 40 medical-student users in an early deployment or
> pilot.**

This is a user-capacity target, not a guarantee of forty simultaneous
heavy AI generations.

The architecture should support:

-   Approximately 40 registered/active early users
-   Normal concurrent application usage
-   Bounded concurrent AI inference
-   Queued AI requests during short bursts
-   Transparent busy/failure behavior
-   Future horizontal or vertical AI scaling

The exact sustainable number of simultaneous generations must be
determined through benchmark testing using the selected model, context
size, quantization, and inference hardware.

------------------------------------------------------------------------

# 5. High-Level AI Architecture

``` text
Student
   ↓
Spring Boot Application
   ├── Learning Engine
   ├── AI Orchestrator
   ├── RAG Engine
   └── AI Request Manager
              ↓
        Provider Router
          /       \
         ▼         ▼
   Ollama API   Gemini API
          \       /
           ▼     ▼
       Output Validator
              ↓
       Learning Engine
```

Spring Boot remains the application and educational orchestrator.

The external AI providers are execution dependencies, not educational
authorities.

Provider selection may differ by task, but the task contract, grounding
mode, source evidence, validation rules, and Learning Engine behavior
must remain application-owned.

# 6. Dual External AI Provider Strategy

## 6.1 Approved v1 Providers

Hippocampus v1 will integrate two external AI providers:

``` text
1. Ollama API
2. Google Gemini API
```

Neither provider is hosted locally by Hippocampus for v1.

Both integrations must be accessed only through backend provider
adapters.

## 6.2 Provider-Abstraction Rule

The application must depend on an internal AI contract rather than
provider-specific behavior.

Conceptually:

``` text
TypedAITask
   ↓
AI Orchestrator
   ↓
Provider Router
   ├── OllamaProviderAdapter
   └── GeminiProviderAdapter
```

This allows provider/model changes without rewriting the Learning
Engine.

## 6.3 Provider Routing

Provider selection may consider:

-   Task type
-   Evaluation quality
-   Grounding reliability
-   Structured-output reliability
-   Multimodal capability
-   Latency
-   Current provider availability
-   Rate limits / quotas
-   Token usage
-   Cost

Provider routing must be deterministic/configurable and observable.

Do not hard-code assumptions such as "Gemini always handles cases" until
evaluation data supports them.

## 6.4 Fallback Rule

Fallback is allowed only when the alternate provider can satisfy the
same application contract.

A fallback must preserve:

-   Task type
-   Grounding mode
-   Evidence Package
-   Output schema
-   Validation requirements
-   Safety requirements

> **Provider fallback must never silently change educational policy,
> grounding behavior, or safety behavior.**

## 6.5 Credential Boundary

Provider credentials are server-side secrets.

``` text
Browser
   ↓
Spring Boot
   ↓
Provider Adapter
   ├── Ollama API credential
   └── Gemini API credential
```

Provider API keys must never be returned to the browser, persisted in
learning records, or embedded in frontend code.

## 6.6 Cost Philosophy

The v1 strategy is:

> **Use free-tier or low-cost external inference deliberately, while
> reducing unnecessary AI calls through deterministic logic, retrieval,
> reuse, caching, and task-specific context.**

External inference is not assumed to be unlimited or permanently free.

Quotas and pricing can change, so they are operational constraints
rather than product assumptions.

# 7. Model Architecture

The MVP uses **task roles**, not one permanently fixed model.

Primary roles are:

``` text
Chat / Instruction Generation
+
Embedding Generation
+
Optional Multimodal Interpretation
```

A provider/model combination may satisfy one or more roles.

## 7.1 Chat / Instruction Role

Supports:

-   Explanation
-   Clarification
-   Question generation
-   Open-ended answer evaluation
-   Concept connections
-   Contextualized application
-   Guided feedback
-   Mission-planning assistance

## 7.2 Embedding Role

Supports:

-   Source chunk embeddings
-   Semantic retrieval
-   Topic/source similarity
-   RAG queries

The embedding provider/model is selected separately from the chat
provider if evaluation shows that is preferable.

## 7.3 Multimodal Role

Where a learning task genuinely requires image understanding, the
Provider Router may select a provider/model with validated multimodal
capability.

Original source visuals remain first-class evidence regardless of
provider.

## 7.4 Avoid Provider Proliferation

Do not create separate provider stacks for every feature.

Task specialization should primarily come from:

-   Typed tasks
-   Prompt templates
-   Structured inputs
-   Structured outputs
-   Educational rules
-   RAG grounding
-   Application orchestration

Provider/model diversity is justified only by measured benefit.

# 8. Model Selection Policy

No single model family is permanently locked by this document.

Each provider/model candidate must be evaluated using Document 15.

Evaluation dimensions include:

-   Medical educational correctness
-   Groundedness
-   Instruction following
-   Structured-output reliability
-   Explanation quality
-   Response-evaluation quality
-   Scenario quality
-   Multimodal quality where applicable
-   Latency
-   Rate limits / quotas
-   Context capacity
-   Token efficiency
-   Cost
-   Availability

The selected provider/model may differ by task and may change without
changing the Hippocampus learning architecture.

# 9. AI Task Architecture

AI behavior should be task-oriented rather than implemented as
autonomous agents.

## AI-01 --- Explanation Task

Supports:

-   Normal explanation
-   Simpler explanation
-   Step-by-step mechanism
-   Analogy
-   Prerequisite explanation
-   Comparison
-   Visual-context explanation where supported

Conceptual input:

``` text
Task
+
Learning Objective
+
Learner Context
+
Relevant Source Context
+
Explanation Strategy
```

------------------------------------------------------------------------

## AI-02 --- Retrieval Question Task

Generates appropriate learning activities such as:

-   Recall
-   Short answer
-   Explanation question
-   MCQ
-   Identification
-   Image-related question when supported

Input may include recent question history to reduce accidental
duplication.

------------------------------------------------------------------------

## AI-03 --- Response Evaluation Task

Used when deterministic evaluation is insufficient, particularly for
open-ended student responses.

Conceptual output:

``` text
Correctness
Reasoning Assessment
Detected Gap
Misconceptions
Feedback
Recommended Learning Action
```

The AI does not directly persist mastery.

------------------------------------------------------------------------

## AI-04 --- Concept Connection Task

Generates educationally relevant relationships such as:

``` text
Structure ↔ Function
Mechanism ↔ Effect
Normal ↔ Abnormal
Anatomy ↔ Physiology
Pathology ↔ Clinical Finding
Drug Mechanism ↔ Therapeutic Effect
```

------------------------------------------------------------------------

## AI-05 --- Contextualized Application Task

Generates scaffolded:

-   Concrete examples
-   Mechanism-to-finding questions
-   Practical medical questions
-   Short clinical scenarios
-   Guided case reasoning

The task must respect learner level and clinical-safety boundaries.

------------------------------------------------------------------------

## AI-06 --- Reflection Interpretation Task

May interpret lightweight student reflection when natural-language
interpretation adds value.

Example:

> "I still don't understand why posterior-cord injury causes wrist
> drop."

The task may identify the likely unresolved concept for subsequent
application logic.

------------------------------------------------------------------------

## AI-07 --- Mission Planning Assistance

AI may propose candidate learning activities.

Example:

``` text
Explain
  ↓
Retrieve
  ↓
Connect
  ↓
Apply
```

However:

> **Spring Boot owns the final Study Mission plan and persistent mission
> state.**

------------------------------------------------------------------------

# 10. Application Logic vs AI Logic

## 10.1 Application Owns

Spring Boot / deterministic application logic owns:

-   Users
-   Subjects
-   Topics
-   Materials
-   Study Mission state
-   Session state
-   Current activity
-   Learning-state transitions
-   Learning evidence
-   Question history
-   Duplicate-control rules
-   Time constraints
-   Review timing
-   Review priority
-   Progress state
-   Source references
-   Feature eligibility
-   Safety boundaries
-   Persistence
-   Retry policy
-   Queue priority
-   Rate limits

## 10.2 AI Owns

AI may perform:

-   Natural-language explanation
-   Question generation
-   Scenario generation
-   Open-ended answer interpretation
-   Natural-language feedback
-   Concept-relationship generation
-   Educational transformation of grounded source material

## 10.3 Permanent Boundary

> **Persistent educational state must not exist only inside an LLM
> conversation.**

The application database is the source of truth for learning state.

------------------------------------------------------------------------

# 11. RAG as a Core AI Dependency

Material-specific learning tasks should use Retrieval-Augmented
Generation rather than repeatedly providing complete documents.

Conceptual pipeline:

``` text
Upload Material
      ↓
Extract Content
      ↓
Preserve Structure
      ↓
Chunk
      ↓
Generate Embeddings
      ↓
Index
      ↓
Learning Task
      ↓
Retrieve Relevant Context
      ↓
Prompt
      ↓
Provider Router
      ↓
Structured Result
```

Benefits include:

-   Lower context requirements
-   Better source grounding
-   Lower inference cost
-   Lower latency
-   Better source traceability
-   Reduced irrelevant context
-   Reduced hallucination exposure

Detailed RAG design belongs to **13 - RAG Architecture**.

------------------------------------------------------------------------

# 12. Prompt Context Architecture

AI tasks should receive only context necessary for the current
educational purpose.

Avoid:

``` text
Entire PDF
+
Entire Chat History
+
Entire Study History
+
Every Previous Question
```

Prefer:

``` text
Educational Rules
+
Current Task
+
Relevant Source Chunks
+
Minimal Relevant Learning Evidence
+
Relevant Recent Activity
```

This is especially important when external provider context windows,
token quotas, latency, and cost are constrained.

Larger context can substantially increase token usage, latency, quota
consumption, and cost.

------------------------------------------------------------------------

# 13. Source Priority

For material-grounded learning:

``` text
Student Material
      ↓
Primary Source Context
      ↓
AI Explanation
```

Supplemental general model knowledge may be used only when appropriate
and should be distinguishable where relevant.

Example:

Student:

> "According to my professor's slides, what causes this?"

If the answer cannot be supported from those slides:

> Hippocampus should state that the information was not found reliably
> in the selected material.

The product may separately offer a general explanation when allowed by
the learning task.

------------------------------------------------------------------------

# 14. Structured AI Outputs

Machine-consumed AI results should use explicit structured schemas.

Example question result:

``` json
{
  "type": "SHORT_ANSWER",
  "concept": "posterior cord",
  "question": "...",
  "expectedAnswer": "...",
  "explanation": "...",
  "sourceReferences": ["chunk-12", "chunk-16"],
  "difficulty": "FOUNDATIONAL"
}
```

Example response evaluation:

``` json
{
  "correctness": "PARTIAL",
  "identifiedConcepts": [],
  "missingConcepts": [],
  "misconceptions": [],
  "feedback": "...",
  "recommendedAction": "TARGETED_EXPLANATION"
}
```

The exact schema belongs to later AI Learning Engine and API
specifications.

------------------------------------------------------------------------

# 15. AI Output Validation

LLM output must not automatically become trusted application state.

Conceptual pipeline:

``` text
Provider Router
  ↓
Structured Response
  ↓
Schema Validation
  ↓
Grounding / Safety Validation
  ↓
Application Rules
  ↓
Persistent State Update
```

Invalid outputs should follow a bounded recovery path:

``` text
Invalid Output
    ↓
Repair / Retry
    ↓
Still Invalid?
    ↓
Safe Fallback
```

The Study Mission should not crash merely because a generated response
is malformed.

------------------------------------------------------------------------

# 16. Learning Evidence Architecture

Learning evidence must remain application-owned.

Avoid:

``` text
LLM:
"The student is 87% mastered."
```

Instead:

``` text
Student Response
+
Evaluation Result
+
Activity Type
+
Prior Evidence
+
Review History
+
Confidence
        ↓
Application Rules
        ↓
Updated Learning Evidence
```

AI may help classify an open-ended response.

Application logic determines what that classification means for stored
learning evidence.

------------------------------------------------------------------------

# 17. Review Architecture

Review scheduling must not be controlled solely by free-form LLM output.

Avoid:

> "The model says review this in three days."

Prefer:

``` text
Learning Evidence
      ↓
Deterministic / Explicit Review Rules
      ↓
Review Priority / Timing
```

AI may generate review content.

The application decides:

-   Whether review is needed
-   Why review is needed
-   When review becomes eligible
-   Which concept is prioritized

This keeps review:

-   Explainable
-   Testable
-   Stable
-   Cost-efficient

------------------------------------------------------------------------

# 18. AI Invocation Reduction Strategy

To support approximately 40 users economically, Hippocampus should
intentionally reduce unnecessary inference.

## 18.1 Reuse Existing Results

When safe and educationally appropriate, reusable content may include:

-   Previously generated grounded explanation
-   Existing source summary
-   Existing concept metadata
-   Previously generated question pool
-   Existing source references

Reuse must not cause stale or contextually inappropriate
personalization.

## 18.2 Pre-Generate Select Activities

Background processing may prepare limited reusable material after a
source becomes ready, for example:

-   Foundational concept candidates
-   Retrieval-question candidates
-   Key concept relationships

Only where doing so provides clear latency/resource benefit.

## 18.3 Deterministic First

Do not invoke an LLM for tasks that are better handled
deterministically.

Examples:

-   Timer countdown
-   Review eligibility calculation
-   Session resume
-   Subject organization
-   Progress retrieval
-   Duplicate lookup
-   Basic multiple-choice scoring
-   Source mapping
-   Saved question reuse

## 18.4 AI Only When Educationally Valuable

This creates the architecture:

``` text
Student Action
     ↓
Does this require generative intelligence?
     ├── No → Application Logic
     └── Yes → AI Task Queue
```

------------------------------------------------------------------------

# 19. AI Request Manager

Spring Boot should own an application-level AI Request Manager.

Responsibilities:

-   Classify AI task
-   Assign priority
-   Apply per-user/request limits
-   Apply concurrency limit
-   Queue work
-   Handle timeout
-   Retry safely
-   Cancel obsolete work
-   Record diagnostics

This is distinct from any provider-side queue or throttling.

------------------------------------------------------------------------

# 20. AI Request Priority

Suggested conceptual priority:

## Priority 1 --- Interactive Feedback

Student is actively waiting.

Examples:

-   Answer evaluation
-   Targeted corrective feedback
-   Clarification

## Priority 2 --- Interactive Generation

Examples:

-   New explanation
-   Retrieval question
-   Application scenario

## Priority 3 --- Mission Preparation

Examples:

-   Upcoming activity generation
-   Next-step preparation

## Priority 4 --- Background Work

Examples:

-   Embeddings
-   Optional pre-generation
-   Non-urgent source enrichment

This keeps scarce inference capacity aligned with the active learning
experience.

------------------------------------------------------------------------

# 21. Forty-User Concurrency Model

The v1 target remains approximately 40 medical-student users.

The architecture must distinguish registered/active users from
simultaneous external AI calls.

``` text
~40 Users
    ↓
Many Deterministic Interactions
    +
Selective AI Requests
    ↓
AI Request Manager
    ↓
Provider Router
    ↓
Provider Rate Limits / Quotas
    ↓
Ollama API or Gemini API
```

The system must not assume forty simultaneous generations.

Application-side concurrency, rate limits, queue limits, and
per-provider quotas must be configurable and benchmarked against the
actual API plans used during deployment.

# 22. Why 40 Users Is Feasible

The product does not continuously depend on generation.

Many actions remain deterministic or reuse existing artifacts:

-   Navigation
-   Reading uploaded material
-   Viewing progress
-   Timers
-   Review scheduling
-   Answering pre-generated questions
-   Viewing saved explanations
-   Session resume
-   Learning evidence display

Therefore:

> **User count and simultaneous external AI request count are separate
> capacity dimensions.**

Approximately 40 users can be feasible when AI calls are selective,
bounded, reusable where safe, and distributed across configured provider
capacity.

Actual feasibility must still be proven through quota and load testing.

# 23. Provider Concurrency, Quota, and Rate-Limit Rules

External provider capacity is constrained by provider-specific policies
rather than local RAM/VRAM.

The architecture must account for:

-   Requests per minute
-   Tokens per minute
-   Daily/monthly quotas
-   Concurrent request limits
-   Model-specific limits
-   Retry-after responses
-   Provider outages
-   Free-tier exhaustion

The AI Request Manager must provide:

-   Per-provider concurrency gates
-   Rate limiting
-   Bounded queueing
-   Timeout
-   Backoff
-   Retry only when safe
-   Circuit-breaker behavior where appropriate
-   Quota-aware routing

> **Provider limits must be treated as runtime constraints, not hidden
> assumptions.**

# 24. Provider Portfolio Strategy

For v1, prefer a small evaluated provider/model portfolio.

``` text
Ollama API
+
Gemini API
+
One selected embedding configuration
```

Avoid constantly switching among many models without evidence.

Benefits:

-   Easier regression testing
-   Predictable prompts
-   Simpler routing
-   Better quota monitoring
-   Easier failure attribution
-   Lower operational complexity

Provider/model changes must remain configuration-driven and versioned.

# 25. Streaming Strategy

Interactive generation should support progressive display where safe.

Example:

``` text
Student Requests Explanation
       ↓
AI Request Starts
       ↓
First Tokens Available
       ↓
Student Sees Response Progressively
```

However, machine-consumed structured outputs may need to complete and
validate before being used for application decisions.

Therefore:

-   Student-facing explanatory prose may stream.
-   Persistent-state decisions must use validated complete output.

------------------------------------------------------------------------

# 26. Caching Strategy

Caching should reduce unnecessary model calls without undermining
personalization.

## Appropriate Cache Candidates

-   Source extraction result
-   Chunk embeddings
-   Source metadata
-   Stable source-derived concept summaries
-   Reusable grounded definitions
-   Non-personalized foundational question candidates

## Poor Cache Candidates

-   Personalized feedback
-   Current-session reasoning evaluation
-   Learner-specific next-action decisions
-   Context-sensitive clinical scenarios where prior evidence matters

Caching policy must respect source versioning and user privacy.

------------------------------------------------------------------------

# 27. Multimodal / Image Architecture

MVP visual support should remain intentionally bounded.

Conceptual pipeline:

``` text
PDF / Image
      ↓
Text + Image Extraction
      ↓
Image Metadata
+
Nearby Text
+
Caption
+
Page Context
      ↓
Associate With Topic / Chunks
      ↓
Learning Mission Determines Relevance
      ↓
Display Visual
+
Generate Supported Visual Activity
```

A multimodal model may be used only when the selected model/hardware
provides sufficient reliability.

The system should preserve the source visual even when AI visual
interpretation is unavailable.

------------------------------------------------------------------------

# 28. Deferred Advanced Visual AI

The following are not required in AI Architecture v1:

-   Anatomical segmentation
-   Precision bounding-box labeling
-   Advanced radiology interpretation
-   Automated pathology diagnosis
-   Complex visual overlays
-   Arbitrary medical-image reasoning

These remain future capabilities subject to evidence, safety, and
technical validation.

------------------------------------------------------------------------

# 29. AI Safety Pipeline

Generated educational output should conceptually pass through:

``` text
Model Output
    ↓
Schema Validation
    ↓
Grounding Check
    ↓
Educational / Safety Rules
    ↓
Source Attribution
    ↓
Application Decision
    ↓
Student
```

Required behaviors include:

-   No fabricated citations
-   No fabricated source claims
-   No unsupported certainty
-   No silent source failure
-   No patient-specific clinical decision support
-   No representation of virtual cases as clinical competence
-   Clear supplemental-content distinction where relevant

------------------------------------------------------------------------

# 30. AI Failure Strategy

Every AI-dependent feature requires explicit failure behavior.

## Timeout

``` text
Timeout
  ↓
Retry If Safe
  ↓
Still Failing
  ↓
Transparent Fallback
```

## Malformed Output

``` text
Malformed
   ↓
Repair / Retry
   ↓
Still Invalid
   ↓
Do Not Persist Result
```

## Insufficient Source Context

``` text
Insufficient Context
   ↓
Explain Limitation
   ↓
Optionally Offer General Explanation
```

## Provider Busy / Rate Limited

``` text
Capacity Busy
   ↓
Application Queue
   ↓
Process When Capacity Available
```

## Queue Capacity Exceeded

The application should provide a meaningful temporary-unavailability
response rather than silently dropping the request.

------------------------------------------------------------------------

# 31. AI Sequence Diagram

``` mermaid
sequenceDiagram
    actor Student
    participant API as Hippocampus Application
    participant Learning as Learning Engine
    participant RAG as Retrieval Engine
    participant Manager as AI Request Manager
    participant Router as Provider Router
    participant Validator as Output Validator
    participant Evidence as Learning Evidence

    Student->>API: Submit learning interaction
    API->>Learning: Determine required learning action

    alt AI not required
        Learning-->>API: Deterministic next action
        API-->>Student: Response / activity
    else AI required
        Learning->>RAG: Retrieve relevant source context
        RAG-->>Learning: Chunks + source references

        Learning->>Manager: Submit typed AI task
        Manager->>Manager: Prioritize + enforce concurrency
        Manager->>Router: Route typed task when capacity available

        Router-->>Manager: Normalized structured/generated result
        Manager-->>Validator: Validate result

        alt Valid
            Validator-->>Learning: Validated AI result
            Learning->>Evidence: Update evidence if appropriate
            Learning-->>API: Application-approved next action
            API-->>Student: Explanation / question / feedback
        else Invalid
            Validator-->>Learning: Validation failure
            Learning->>Manager: Bounded repair / retry
        end
    end
```

------------------------------------------------------------------------

# 32. RAG Interaction Diagram

``` mermaid
flowchart TD

A[Uploaded Material]
--> B[Content Extraction]

B --> C[Structure Preservation]
C --> D[Chunking]

D --> E[Embedding Model]
E --> F[Vector Index]

G[Learning Task]
--> H[Semantic Query]

H --> F
F --> I[Relevant Chunks]

I --> J[Prompt Builder]

K[Educational Rules] --> J
L[Relevant Learning Evidence] --> J
M[Current Task] --> J

J --> N[Selected Provider / Model]

N --> O[Structured Output]
O --> P[Validation]
P --> Q[Learning Engine]
```

------------------------------------------------------------------------

# 33. Capacity Flow Diagram

``` mermaid
flowchart TD

A[Approximately 40 Medical-Student Users]
--> B[Hippocampus Application]

B --> C{Does Action Need AI?}

C -->|No| D[Application Logic / Stored Data]
D --> E[Immediate Product Response]

C -->|Yes| F[AI Request Manager]

F --> G{Inference Capacity Available?}

G -->|Yes| H[Selected Provider / Model]
G -->|No| I[Bounded Priority Queue]

I --> G

H --> J[Validated AI Result]
J --> K[Learning Engine]
K --> E

B --> L[Background Work Queue]
L --> M[Embeddings / Material Processing / Safe Pre-Generation]
```

This diagram captures the principal scalability decision:

> **The product can serve more users than it can generate for
> simultaneously because most interactions do not require live AI
> inference.**

------------------------------------------------------------------------

# 34. Cost Strategy

The v1 cost priority is:

``` text
1. Deterministic application logic where AI is unnecessary
2. Retrieval and source reuse
3. Safe caching / reusable generated artifacts
4. Compact task-specific prompts
5. Free-tier / low-cost external AI usage
6. Provider routing based on measured quality, quota, latency, and cost
7. Paid usage only when required and explicitly budgeted
```

Hippocampus should not assume that either provider will remain free
indefinitely.

Usage must be measurable per provider and task.

# 35. Free-Tier / External Provider Boundary

The MVP may rely on external AI APIs, but product behavior must not
assume unlimited free-tier capacity.

Free-tier constraints must be treated explicitly:

-   Quotas
-   Rate limits
-   Model availability
-   Terms/pricing changes
-   Regional availability
-   Provider outages

If a free tier is exhausted, Hippocampus should degrade transparently,
route to another approved provider when allowed, or reject/defer the
AI-dependent action safely.

It must never bypass grounding or validation merely to obtain a
response.

# 36. Initial Deployment Shape

``` text
                 Hippocampus Deployment
                           │
          ┌────────────────┼─────────────────┐
          │                │                 │
      Application       Database        File Storage
          │
          ▼
    AI Request Manager
          │
          ▼
     Provider Router
       /          \
      ▼            ▼
Ollama API     Gemini API
```

The backend stores provider credentials securely and is the only
application component allowed to call these APIs.

The operational architecture should remain simple initially.

Avoid premature introduction of Kubernetes, many AI microservices, or
provider-specific business logic.

# 37. Scale-Up Path

If the 40-user deployment validates the product and usage grows:

``` text
Initial Backend
      ↓
Stronger Provider Quota Management
      ↓
Task-Based Provider Routing
      ↓
Dedicated Background Workers
      ↓
Horizontal Backend Scaling
      ↓
Optional Additional Approved Providers / Self-Hosted Inference
```

Self-hosted inference remains a future option, not a v1 requirement.

The educational architecture must remain unchanged when
infrastructure/provider strategy changes.

# 38. AI Architecture Decisions --- Locked for v1

The following decisions are approved for Hippocampus v1:

1.  Hippocampus uses two external AI providers: Ollama API and Google
    Gemini API.
2.  Ollama is not self-hosted locally for v1.
3.  Both provider credentials remain server-side only.
4.  Spring Boot/application logic owns educational and business state.
5.  AI providers execute typed tasks; they do not own learning
    decisions.
6.  AI integrations are hidden behind provider adapters and an internal
    task contract.
7.  A Provider Router selects an approved provider/model per task.
8.  Provider selection is configuration/evaluation driven rather than
    hard-coded by feature.
9.  Provider fallback must preserve grounding, task schema, validation,
    and safety behavior.
10. Initial capacity planning targets approximately 40 medical-student
    users.
11. Forty users does not imply forty simultaneous generations.
12. AI request concurrency is bounded application-side.
13. Provider quotas and rate limits are first-class operational
    constraints.
14. RAG grounds material-specific learning tasks.
15. The embedding configuration is selected independently through
    evaluation.
16. AI capabilities remain task-oriented rather than autonomous
    multi-agent systems.
17. Structured outputs are required for machine-consumed AI responses.
18. AI output must be validated before affecting persistent state.
19. Review timing remains application-owned.
20. Learning evidence remains application-owned.
21. Prompt context should be task-specific and minimal.
22. Deterministic logic should be preferred whenever generative AI is
    unnecessary.
23. Safe caching/reuse should reduce avoidable API usage.
24. Background material work must not unnecessarily compete with active
    student interactions.
25. Visual AI may use an approved provider/model only after
    task-specific evaluation.
26. Every AI failure requires transparent fallback behavior.
27. Provider/model replacement must remain possible without rewriting
    the educational system.
28. Free-tier availability is not assumed to be permanent or unlimited.
29. Provider usage, latency, failures, and token consumption must be
    observable.
30. Self-hosted inference is deferred and may be reconsidered later if
    evidence justifies it.

# 39. Validation Requirements for the 40-User Target

Before claiming support for approximately 40 users, performance testing
should simulate realistic workload rather than forty permanent
simultaneous generations.

Test profiles should include:

## Profile A --- Normal Mixed Usage

-   Many non-AI interactions
-   A small number of simultaneous explanation/question requests
-   Background embedding work

## Profile B --- Class Break / Burst

-   Multiple students start Study Missions within a short window
-   Queue behavior tested
-   Interactive-request priority validated

## Profile C --- Heavy AI Usage

-   Several simultaneous open-ended evaluations
-   Long explanation requests
-   Case generation

## Profile D --- Background Processing Load

-   Material uploads
-   Embedding generation
-   Active student sessions simultaneously

Acceptance thresholds for latency, queue length, timeout rate, provider
rate-limit events, quota consumption, and cost should be defined after
the provider/model routes and deployment plan are selected.

------------------------------------------------------------------------

# 40. Architecture Constraint

The system must not claim:

> **"Hippocampus supports 40 simultaneous AI generations."**

unless this has been demonstrated through benchmark and quota testing
against the actual production provider/model configuration.

The approved claim is:

> **Hippocampus v1 is architected to support an initial population of
> approximately 40 medical-student users through deterministic
> application logic, bounded AI concurrency, queueing, caching/reuse,
> and controlled external-provider usage.**

------------------------------------------------------------------------

# 41. Out of Scope

This document does not yet define:

-   Exact chat model
-   Exact embedding model
-   Exact provider plan/quota
-   Exact provider/model routing
-   Exact concurrency number
-   Exact queue capacity
-   Exact context window
-   Exact AI timeout
-   Exact prompt templates
-   Exact chunking algorithm
-   Exact vector store
-   Exact RAG ranking algorithm
-   Exact Spring Boot classes
-   Exact database schema
-   Exact deployment provider

Those decisions belong to subsequent AI and software architecture
documents.

------------------------------------------------------------------------

# 42. Related Documents

-   README - Documentation Guide
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
-   11 - AI Learning Engine
-   12 - Prompt Engineering Strategy
-   13 - RAG Architecture
-   14 - Knowledge Base Design
-   15 - AI Evaluation Strategy

------------------------------------------------------------------------

# 43. Next Document

**11 - AI Learning Engine**

The next document should define how the Learning Engine uses
deterministic educational rules, learner state, Study Mission state, RAG
context, and typed AI tasks to determine the next educational action.

It should answer:

> **How does Hippocampus decide what the student should do next?**

The Learning Engine must remain the educational decision layer above the
LLM.

------------------------------------------------------------------------

# 44. Revision History

  ------------------------------------------------------------------------
  Version          Date             Author           Changes
  ---------------- ---------------- ---------------- ---------------------
  1.1.0            2026-08-23       Project          Revised AI
                                    Hippocampus Team architecture from
                                                     local/self-hosted
                                                     Ollama to
                                                     provider-abstracted
                                                     external Ollama API +
                                                     Google Gemini API;
                                                     added provider
                                                     routing, fallback
                                                     invariants,
                                                     credential
                                                     boundaries,
                                                     quota/rate-limit
                                                     controls, and
                                                     external-provider
                                                     cost strategy.

  1.0.0            2026-08-23       Project          Initial finalized AI
                                    Hippocampus Team Architecture using
                                                     local-first Ollama
                                                     inference,
                                                     application-owned
                                                     learning logic,
                                                     bounded AI
                                                     concurrency, RAG,
                                                     structured outputs,
                                                     and architecture for
                                                     approximately 40
                                                     early medical-student
                                                     users
  ------------------------------------------------------------------------

------------------------------------------------------------------------

# 45. Approval

**Status:** Final

**Reviewed By:** Project Hippocampus Team

**Approved By:** Project Hippocampus Team
