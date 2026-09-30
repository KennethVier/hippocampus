---
ADR: ADR-0007
Title: Explicit Retrieval Activity Type Selection
Status: ACCEPTED
Date: 2026-09-30
Decision Owners: Project Hippocampus Team
Categories: DOMAIN, BACKEND, AI, EDUCATION
Affected Documents: 11, 18, 27
---

# Context

The `QUESTION_GENERATION` AI contract supports several retrieval formats, but
the Learning Engine contract did not carry the selected format. Inferring a
format in an AI adapter, provider adapter, prompt, or model would transfer a
pedagogical decision out of the deterministic Learning Engine.

# Decision

The learning domain owns `RetrievalActivityType` with these values:

``` text
SHORT_ANSWER
MCQ
IDENTIFICATION
EXPLANATION
```

`LearningActionConstraints` carries nullable
`retrievalActivityType`. It is present exactly for an AI-backed `RETRIEVE`
action and null for actions that do not generate retrieval questions.
Constraint transformations, including `withoutSourceDependency()` and
`withRepetitionIntent()`, preserve the selected value.

The initial v1 Learning Engine policy is complete and deterministic:

1. a visual-required retrieval selects `IDENTIFICATION`;
2. every other retrieval selects `SHORT_ANSWER`.

The policy does not infer format from difficulty or `questionIntent`, rotate or
randomize formats, select formats by subject, let a model select the format, or
automatically select `MCQ` or `EXPLANATION`. The latter values remain supported
so the existing AI contract can represent an explicit future selection.

The AI integration adapter maps the learning value exactly:

``` text
SHORT_ANSWER   -> ai.domain.ActivityType.SHORT_ANSWER
MCQ            -> ai.domain.ActivityType.MCQ
IDENTIFICATION -> ai.domain.ActivityType.IDENTIFICATION
EXPLANATION    -> ai.domain.ActivityType.EXPLANATION
```

The validated `QuestionGenerationResult.activityType` must equal the requested
type. A mismatch is invalid output. `ActivityAiTaskPort`, AI adapters, and
provider adapters do not provide a fallback or default question type.

# Rationale

An explicit learning-domain value keeps pedagogical selection deterministic,
testable, and independent of the AI module while preserving a provider-neutral
mapping boundary.

# Consequences

Learning policies that create AI-backed retrieval actions must select a type.
AI integration must pass the exact mapped type through the canonical
`QUESTION_GENERATION` request and reject mismatched output.

# Persistence Impact

No migration or new persistent column is required. The generated artifact
payload already stores the generated task structure and its activity type.

# Security & Privacy Impact

No new data, external service, authorization boundary, or provider authority is
introduced. Provider output remains untrusted and validated.

# Educational/Product Impact

Visual retrieval initially uses identification; other retrieval initially uses
short answer. Later adaptive-format policy requires a separate approved change.

# Testing Impact

Validation should cover both initial selections, field preservation across
constraint transformations, all exact adapter mappings, and rejection of a
result whose activity type differs from the request.

# Documentation Changes Required

Documents 11, 18, and 27 are aligned with this accepted decision.

# Approval

**Status:** ACCEPTED

**Approved By:** Project Hippocampus Team
