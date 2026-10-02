---
ADR: ADR-0010
Title: Presentation-Only UNDERSTAND Progression and Evidence
Status: PROPOSED
Date: 2026-10-02
Decision Owners: Project Hippocampus Team
Categories: DOMAIN, BACKEND, EDUCATION, FRONTEND
Affected Documents: 06, 11, 18, 19, 20, 27
---

# Context

Hippocampus Study Missions use the Learning Engine to sequence educational activities according to learner state and accumulated learning evidence.

The canonical mission flow includes:

```text
Understand
→ Retrieve
→ Connect
→ Apply
→ Feedback
→ Reflect
```

The current implementation exposes a progression gap for presentation-only `UNDERSTAND` activities.

An explanation may be materialized as a `LearningActivity` represented by `UNDERSTAND` and shown to the learner. Unlike retrieval or application activities, however, an explanatory presentation does not naturally require an evaluated learner response.

The current interaction API allows continuation only after the current activity has been completed.

At the same time, the Learning Engine requires sufficient `UNDERSTANDING` evidence before normal retrieval progression. Current mission learning-state reconstruction derives competence evidence from evaluated `StudentAttempt` outcomes.

This creates an invalid possible shortcut:

```text
Learner reads explanation
→ clicks Continue
→ system treats Continue as successful understanding
→ UNDERSTANDING becomes DEVELOPING
→ progression continues
```

A Continue click demonstrates interaction with the presentation, not understanding of the educational concept.

Hippocampus therefore requires an explicit distinction between:

```text
presentation completion
```

and:

```text
demonstrated learning evidence
```

Learning evidence must continue to map to real learner activity.

# Decision

## Presentation completion is not competence evidence

A learner may explicitly complete a presentation-only activity after viewing or using it.

Presentation completion means only:

```text
The learner finished interacting with this presentation activity.
```

It does not mean:

```text
CORRECT
PARTIAL
mastered
understood
ready for application
```

Completing a presentation-only activity must not by itself create:

```text
StudentAttempt assessment evidence
UNDERSTANDING evidence
RECALL evidence
mastery evidence
```

A Continue interaction must never be converted into a synthetic `CORRECT`, `PARTIAL`, or `INCORRECT` learner outcome.

## Presentation-only activities may have an explicit completion transition

The backend may expose an explicit completion transition for an activity that is genuinely presentation-only and does not require an evaluated learner response.

This transition remains backend-authoritative.

Conceptually:

```text
authenticated learner
→ owner-scoped current presentation
→ validate mission/activity state
→ complete presentation activity
```

The transition must preserve existing ownership, concurrency, stale-state, and non-enumeration requirements.

Frontend state is not authorization or sequencing authority.

## Completion does not directly unlock normal progression

Completion of an `UNDERSTAND` presentation does not satisfy the Learning Engine's `UNDERSTANDING` evidence requirement.

Before the system treats conceptual understanding as demonstrated, the learner must perform an explicit response-bearing activity that provides evidence of understanding.

The intended flow is:

```text
UNDERSTAND presentation
→ learner completes presentation
→ Understanding Check
→ evaluated learner response
→ UNDERSTANDING evidence
→ normal retrieval
```

The following flow is prohibited:

```text
UNDERSTAND presentation
→ Continue
→ fabricated UNDERSTANDING evidence
→ normal retrieval
```

## Understanding Check

The learning domain owns the semantic concept of an **Understanding Check**.

An Understanding Check is a response-bearing learning activity whose purpose is to determine whether the learner has formed a sufficient conceptual model to begin normal retrieval.

It should require active reconstruction appropriate to the objective, for example:

```text
explain a mechanism
describe a relationship
summarize a causal sequence
identify why a concept behaves as described
relate components of the concept
```

An Understanding Check is not merely an acknowledgement interaction.

The Learning Engine owns whether an Understanding Check is required and when it occurs.

Its semantic identity must be explicit in the learning/application boundary rather than inferred from:

```text
frontend state
prompt wording
provider output
generated text
controller-specific logic
magic strings
```

The exact code representation is an implementation decision constrained by this ADR.

## Understanding Check evaluation

An Understanding Check uses the existing validated learner-response evaluation architecture where compatible.

Conceptually:

```text
understanding-check activity
→ learner response
→ validated evaluation
→ StudentAttempt
→ Learning Engine evidence reconstruction
```

A separate uncontrolled evaluation path must not be introduced.

Provider output remains untrusted and cannot directly modify learning state.

## Understanding evidence

Presentation completion alone never establishes `UNDERSTANDING` evidence.

An Understanding Check provides the explicit response-bearing evidence path required after a presentation-only `UNDERSTAND` activity when sufficient understanding evidence does not already exist.

Evaluated response-bearing learning activities that are already owned by the learning model as `UNDERSTAND`, `HINT`, or `PREREQUISITE_SUPPORT` may continue contributing to `EvidenceDimension.UNDERSTANDING` according to their existing semantics.

The existing initial outcome-to-strength mapping remains:

```text
CORRECT
→ DEVELOPING

PARTIAL
→ WEAK

INCORRECT
→ INSUFFICIENT
```

ADR-0010 does not redefine existing `HINT` or `PREREQUISITE_SUPPORT` evidence semantics.

Its purpose is to ensure that passive presentation completion is never treated as competence evidence.

A future material change to these thresholds or evidence-strength rules requires a separate approved decision.

## Recall remains a separate evidence dimension

Understanding and recall represent different educational evidence.

An Understanding Check therefore does not automatically satisfy `RECALL`.

After sufficient `UNDERSTANDING` evidence exists, the Learning Engine may proceed to normal retrieval activities that establish recall evidence.

Conceptually:

```text
Presentation
→ Understanding Check
→ UNDERSTANDING evidence
→ Retrieval
→ RECALL evidence
→ Connection/Application when eligible
```

Normal retrieval must not silently substitute for the understanding dimension merely to bypass this decision.

## Relationship to retrieval activity formats

ADR-0007 defines learning-owned retrieval activity formats including:

```text
SHORT_ANSWER
MCQ
IDENTIFICATION
EXPLANATION
```

ADR-0007 intentionally does not automatically select `EXPLANATION` for normal v1 retrieval.

ADR-0010 does not redefine every `RetrievalActivityType.EXPLANATION` as an Understanding Check.

If existing question-generation or response-evaluation capabilities can be reused for an Understanding Check, the pedagogical ownership must remain explicit.

The system must be able to distinguish:

```text
ordinary RETRIEVE activity
```

from:

```text
UNDERSTANDING CHECK
```

without depending on provider/model inference.

If implementation requires a new learning action, activity intent, represented pedagogical identity, or equivalent typed learning-domain value to preserve this distinction, that is permitted by this ADR.

## Failed or partial Understanding Checks

An attempted Understanding Check does not guarantee advancement.

When an evaluated check is `PARTIAL` or `INCORRECT`, the Learning Engine remains responsible for choosing the next pedagogical action.

Possible actions may include:

```text
targeted UNDERSTAND
HINT
PREREQUISITE_SUPPORT
another Understanding Check
```

depending on existing deterministic policies and learner state.

The system must not advance merely because a check was attempted.

No new fixed retry count is introduced by this ADR.

## Passive interaction signals are not mastery evidence

The following signals must not independently establish conceptual-understanding competence:

```text
Continue click
time spent on page
scroll position
activity viewed
activity opened
source drawer opened
timer elapsed
frontend-local completion state
```

These may be useful interaction or progression signals but do not establish learning evidence.

## Backend and Learning Engine authority

Hippocampus retains backend authority over mission progression.

The conceptual flow remains:

```text
learner action
→ application use case
→ Learning Engine
→ typed NextLearningAction
→ activity materialization
→ evaluated learner activity
→ persisted attempt/evidence
```

The frontend must not decide:

```text
UNDERSTANDING is DEVELOPING
learner is ready for retrieval
next action is RETRIEVE
activity is mastered
```

The browser renders backend state and submits explicit learner actions.

## AI authority

AI may assist with:

```text
generating Understanding Check content
evaluating an allowed free-text response
```

through the existing validated AI architecture.

AI does not decide:

```text
whether presentation completion is evidence
whether Continue means CORRECT
whether evidence dimensions change
whether progression thresholds have been satisfied
```

Those remain Hippocampus and Learning Engine responsibilities.

The governing principle remains:

```text
AI generates.
Hippocampus decides.
```

# Rationale

The distinction between presentation completion and demonstrated understanding preserves the educational integrity of Hippocampus.

Exposure to an explanation is not evidence that the explanation was understood.

At the same time, presentation activities require an explicit lifecycle transition so the learner can move forward without pretending that passive interaction is assessment.

An explicit Understanding Check creates the missing bridge:

```text
instruction
→ active reconstruction
→ evaluated evidence
→ adaptive progression
```

This keeps progression deterministic, testable, provider-neutral, and grounded in actual learner behavior.

# Consequences

Presentation-only learning activities require a lifecycle path that can complete them without creating assessment evidence.

The Learning Engine must be able to select an explicit Understanding Check when understanding evidence remains below the required threshold after presentation.

Mission interaction APIs must preserve the distinction between:

```text
complete presentation
```

and:

```text
submit evaluated response
```

The frontend will require separate user interactions for these semantics.

Existing progression thresholds and existing response-bearing `UNDERSTAND`, `HINT`, and `PREREQUISITE_SUPPORT` evidence behavior remain authoritative unless explicitly changed by a future accepted decision.

# Persistence Impact

Presentation completion must be durable so that mission state survives:

```text
refresh
resume
reconnection
two-tab interaction
stale requests
```

Presentation completion must not be persisted as fabricated assessment evidence.

`StudentAttempt` remains associated with genuine learner-response activity.

If implementation requires an additional persistent representation to distinguish presentation lifecycle completion from evaluated learner attempts, the implementation must follow Document 18 and Flyway ownership.

This ADR does not select a database schema and does not introduce a migration.

# Security & Privacy Impact

No new client-owned authorization or progression authority is introduced.

Presentation completion and Understanding Check operations remain:

```text
authenticated
owner-scoped
backend-authoritative
stale-safe
```

Cross-user resource leakage tolerance remains zero.

Learner-response content remains subject to existing response-size, validation, privacy, AI-boundary, and safe-error controls.

Provider output remains untrusted and cannot create authoritative learning evidence without validated application/domain processing.

No new provider authority is introduced.

# Educational/Product Impact

Study Missions preserve the distinction between:

```text
instruction
practice
assessment evidence
```

A learner may move past an explanation without the application claiming that understanding has already been demonstrated.

Before normal retrieval progression, Hippocampus obtains active learner evidence through an Understanding Check when the presentation itself has not produced sufficient response-bearing understanding evidence.

This maintains the product principle that learning evidence corresponds to real student activity.

# Frontend Impact

The frontend may expose separate interactions such as:

```text
Continue
```

for a presentation-only activity and:

```text
Submit
```

for an Understanding Check.

The frontend waits for backend confirmation before changing critical mission state.

Refresh, resume, and multiple-tab behavior must always reconstruct state from the authoritative backend representation.

The frontend must not manufacture progression or evidence locally.

# Testing Impact

Implementation must include coverage for at least:

1. completing a presentation does not create a `StudentAttempt`;
2. presentation completion does not create `UNDERSTANDING` evidence;
3. the next backend-selected operation can be an Understanding Check when understanding evidence remains insufficient;
4. evaluated Understanding Check outcomes map to the existing understanding evidence strengths;
5. existing response-bearing `UNDERSTAND`, `HINT`, and `PREREQUISITE_SUPPORT` semantics remain valid;
6. `PARTIAL` or `INCORRECT` checks do not automatically advance;
7. ordinary retrieval remains responsible for recall evidence;
8. stale duplicate presentation completion is rejected without duplicate progression;
9. two-tab completion cannot create duplicate next activities;
10. refresh/resume restores the authoritative current activity;
11. cross-user IDs fail closed without enumeration;
12. frontend interaction cannot directly set evidence or next action.

# Alternatives Rejected

## Continue means CORRECT

Rejected.

Viewing or acknowledging an explanation does not demonstrate conceptual understanding.

Recording the action as `CORRECT` would fabricate learning evidence.

## Continue directly creates DEVELOPING understanding

Rejected.

Avoiding a `StudentAttempt` while directly modifying evidence has the same educational-integrity problem.

A passive UI interaction still would be treated as proof of competence.

## Skip understanding evidence and immediately use ordinary retrieval

Rejected as the default progression model.

Hippocampus intentionally represents conceptual understanding and recall as distinct evidence dimensions.

Normal retrieval must not silently absorb the responsibility of the understanding dimension merely to simplify implementation.

## Provider or AI determines readiness

Rejected.

Educational progression belongs to the Learning Engine, not to a model or provider.

## Passive telemetry establishes understanding

Rejected.

Time on page, scrolling, viewing, opening sources, or similar interaction telemetry does not establish conceptual competence.

# Documentation Changes Required

After this ADR is externally reviewed and accepted, align the affected authoritative documents:

```text
06 — User Journey and Learning Flow
11 — AI Learning Engine
18 — Domain Model and Database Design
19 — Backend Architecture
20 — Frontend Architecture
27 — Decision Log / ADR Index
```

Update the ADR index and implementation tracker accordingly.

Production implementation must not precede acceptance of this decision.

# Approval

**Status:** PROPOSED

**Approved By:** _Pending external review_