---
ADR: ADR-0013
Title: Bounded Pre-Gate P8-03 Attempt-Evidence Transaction
Status: ACCEPTED
Date: 2026-10-05
Decision Owners: Project Hippocampus Team
Categories: ARCHITECTURE, BACKEND, DATA, SECURITY, TESTING, EDUCATION
Affected Documents: 18, 19, 26, IMPLEMENTATION-TRACKER
Supersedes:
Superseded By:
---

# Context

ADR-0011 authorized isolated pre-gate work through P8-01, and ADR-0012 extended that authorization through P8-02 while explicitly excluding P8-03 and later work. P7-09 provider/model qualification and P7-14 remain incomplete, and Phase 7 has not yet been formally recorded as PASS / M3 achieved.

P8-03 integrates validated response attempts with the traceable evidence history and deterministic projection established by P8-01 and P8-02. The project owner has approved this bounded work on an isolated stacked branch.

# Decision

Extend isolated pre-gate Phase 8 development through **P8-03 only**, subject to all of the following controls:

1. This decision does not authorize P8-04 or any later Phase 8 task.
2. P8-01, P8-02, and P8-03 may not merge into `main` until P7-09 qualification completes, P7-14 passes, and Phase 7 is formally recorded as PASS / M3 achieved.
3. Before eventual merge, the stacked work must be rebased or otherwise reconciled against then-current `main`, and required validation, implementation review, and the independent security review must be completed for the final merge candidate.
4. Any conflict discovered during Phase 7 completion must be resolved before merge; this pre-gate implementation has no grandfathered merge entitlement.
5. No P7 behavior, provider routing, provider configuration, prompt, Golden evaluation, or Learning Engine policy may change as part of P8-03.

## Attempt-to-evidence transaction

Response evaluation and its validation occur before a short database write transaction. No AI, provider, network, or RAG call occurs inside that transaction.

After a valid evaluation, the transaction atomically persists the `StudentAttempt`, an eligible immutable `EvidenceEvent`, the recomputed mutable `LearningEvidence` projection, and the completed activity / updated `StudyMission`. Any required write failure rolls back the complete unit. Invalid, uncertain, malformed, unvalidated, null, or failed AI evaluation persists none of those records and advances no mission state. Presentation-only completion continues to create neither a `StudentAttempt` nor evidence.

`EvidenceEvent` remains traceable event history and excludes learner response text, provider output, prompts, and feedback. `LearningEvidence` remains the deterministic, application-owned projection produced from eligible event history. An LLM never owns or directly sets evidence state.

## Event and dimension mapping

Evidence semantics come from the durable pedagogical activity identity, not provider output:

- `UNDERSTAND`, `HINT`, `PREREQUISITE_SUPPORT`, and `UNDERSTANDING_CHECK` map to `UNDERSTANDING_ATTEMPT` / `UNDERSTANDING`.
- Non-visual `RETRIEVE` maps to `RETRIEVAL_ATTEMPT` / `RETRIEVAL`.
- Visual `RETRIEVE` maps to `VISUAL_IDENTIFICATION` / `VISUAL_IDENTIFICATION`.
- `CONNECT` maps to `CONNECTION_ATTEMPT` / `CONNECTION`.
- `APPLY` maps to `APPLICATION_ATTEMPT` / `APPLICATION`.

Visual rendering does not replace `CONNECT`, `APPLY`, or understanding semantics. P8-03 does not create review-retention, reflection, misconception, or inferred corrective-retry evidence.

## Projection identity and concurrency

The longitudinal projection key is immutable and consists of user, topic, nullable subtopic, nullable concept key, and evidence dimension. The concept key uses `LearningObjective.conceptKey`, falling back to `LearningObjective.objectiveText`; activity ID is not a projection key.

The database enforces one projection row with a `UNIQUE NULLS NOT DISTINCT` natural-key constraint. Each write first inserts the initial row with conflict ignored, then locks the exact projection row before reading event history. The transaction appends the event, reloads committed plus current eligible history, runs the existing deterministic projector, updates the locked row without changing its ID, and finally persists the mission transition. This relational serialization prevents concurrent missions from producing duplicate rows or lost projection updates; no JVM, Redis, in-memory, or advisory lock is introduced.

# Rationale

The transaction preserves the required relationship between legitimate learner activity, traceable evidence, deterministic application-owned state, and mission progression. Locking the natural projection identity before reading history makes recomputation safe across different missions while retaining PostgreSQL as the authoritative store.

# Alternatives Considered

## Persist evidence after the mission transaction

Rejected because partial commits could leave attempts or mission transitions without corresponding evidence and projection state.

## Hold the transaction open during AI evaluation

Rejected because provider/network latency must remain outside short database transactions.

## Use an application-process lock

Rejected because it would not serialize multiple application instances and would not make the database invariant authoritative.

## Continue into later Phase 8 work

Rejected. Misconception, reflection, review, scheduling, and retention behavior remain owned by later tracker tasks.

# Consequences

## Positive

- Legitimate activity updates attempt history, evidence history, projection, and mission state atomically.
- Invalid AI evaluation cannot create learning evidence or advance the mission.
- Projection recomputation is deterministic, traceable, and safe from null-key duplicate rows and lost updates.
- Evidence ownership is derived from the locked owner-scoped mission and verified again by persistence queries.

## Negative / Tradeoffs

- Submission writes now serialize per longitudinal projection key.
- Full history is reloaded for the current projection in P8-03; later optimization requires its own measured and approved decision.
- The stacked branch remains non-mergeable until the Phase 7 gate and reconciliation controls are satisfied.

# Security & Privacy Impact

Evidence writes use owner-scoped mission, activity, attempt, topic, subtopic, and objective relationships and fail closed on inconsistent ownership. No request-supplied evidence identity is accepted, and no evidence repository is exposed through an API. Evidence events contain no learner response or provider content.

# Educational/Product Impact

Only validated, response-bearing pedagogical performance updates future learning evidence. Presentation-only completion remains non-evidence. LearningEvidence remains broad evidence, not mastery or a percentage.

# Cost / Infrastructure Impact

No new service, dependency, provider, queue, cache, or external infrastructure is introduced. PostgreSQL row locking and the existing pgvector-backed database remain the persistence foundation.

# Migration Impact

V26 adds the natural-key `UNIQUE NULLS NOT DISTINCT` constraint to `learning_evidence`. V25 remains unchanged.

# Testing Impact

Focused unit and PostgreSQL integration tests cover invalid evaluation, event mappings, deterministic projection changes, ownership failures, nullable natural-key uniqueness, and rollback across the real Spring transaction boundary. Broader validation, review, and the independent security gate remain required before merge.

# Documentation Changes Required

Add ADR-0013 to the ADR index. Do not rewrite ADR-0011 or ADR-0012; this later explicit decision governs only the bounded P8-03 exception.

# Approval

**Status:** ACCEPTED

**Approved By:** Project Hippocampus Team
