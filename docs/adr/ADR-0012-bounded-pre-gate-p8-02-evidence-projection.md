---
ADR: ADR-0012
Title: Bounded Pre-Gate P8-02 Evidence Projection
Status: ACCEPTED
Date: 2026-10-05
Decision Owners: Project Hippocampus Team
Categories: EDUCATION, DOMAIN, DATA, BACKEND, TESTING, ARCHITECTURE
Affected Documents: 11, 18, 26, IMPLEMENTATION-TRACKER
Supersedes:
Superseded By:
---

# Context

ADR-0011 permits isolated pre-gate implementation of P8-01 only while Phase 7 remains formally incomplete. P7-09 provider/model qualification and P7-14 are still outstanding, and Phase 7 has not yet been recorded as PASS / M3 achieved.

P8-01 establishes the durable evidence schema. P8-02 can build on that schema as an isolated, pure domain component without integrating attempts, persistence, providers, or live application behavior. The project owner has approved this bounded extension so that the deterministic evidence projection contract can be implemented and tested while the Phase 7 gate remains closed.

# Decision

Extend the isolated pre-gate development authorized by ADR-0011 to **P8-02 only**, subject to all of the following controls:

1. P8-02 remains a pure progress-domain implementation based on the P8-01 evidence schema.
2. This decision does not authorize P8-03 or any later Phase 8 task.
3. Neither P8-01 nor P8-02 may merge into `main` until:
   - P7-09 provider/model qualification completes;
   - P7-14 passes; and
   - Phase 7 is formally recorded as PASS / M3 achieved.
4. Before either task merges, the Phase 8 work must be reconciled with the then-current `main`, required validation rerun, implementation review completed, and the independent security review completed for the final merge candidate.
5. Any conflict discovered during Phase 7 completion must be resolved before merge; this pre-gate implementation has no grandfathered merge entitlement.
6. No Phase 7 code, Learning Engine policy, provider routing, provider configuration, Golden evaluation, or Study Mission behavior may change as part of P8-02.

## V1 deterministic LearningEvidence projection policy

Projection is evaluated independently for one approved evidence dimension over normalized eligible observations with outcomes `CORRECT`, `PARTIAL`, or `INCORRECT`.

Observations are ordered by `occurredAt` ascending and then stable event ID ascending. Caller collection order has no effect. Duplicate event IDs are rejected as data-integrity defects, and observations from a different dimension are rejected.

The current state is determined from the most recent eligible observations:

- no observations: `INSUFFICIENT_EVIDENCE`;
- latest `INCORRECT`: `INSUFFICIENT_EVIDENCE`;
- latest `PARTIAL`: `WEAK`;
- latest `CORRECT`, with no immediately previous `CORRECT`: `DEVELOPING`;
- two most recent observations both `CORRECT`: `STRONG`.

The projection records the complete eligible observation count as `supportingEventCount` and the latest observation timestamp as `lastObservedAt`, or `null` when there are no observations. Older observations remain represented by the supporting count but do not override the recent-state rule.

This is an application-owned evidence state, not mastery or a percentage. It uses no LLM interpretation, weighted scoring, confidence weighting, difficulty weighting, Bayesian confidence, time decay, or review scheduling. Presentation-only `UNDERSTAND` completion is not evidence. Reflection remains secondary evidence and cannot override observed performance.

# Rationale

The policy is deterministic, explainable, and recomputable from traceable EvidenceEvents. It prevents a single successful observation from producing `STRONG`, responds conservatively to recent partial or incorrect performance, and permits strength to be rebuilt through two later consecutive correct observations.

Keeping the projector pure and inside the progress domain preserves the boundary between durable evidence and the Learning Engine's own state. It also avoids coupling the projection rule to persistence, providers, transport, or AI output.

# Alternatives Considered

## Wait for Phase 7 PASS before implementing P8-02

Rejected for this bounded task because the schema dependency and projection policy are explicit, while implementation remains isolated and non-mergeable before the Phase 7 gate.

## Permit further Phase 8 implementation

Rejected. Attempt-to-evidence integration and later review behavior cross application and persistence boundaries and remain unauthorized before the gate.

## Use a weighted or percentage score

Rejected because it would introduce false precision and policy not established by the Source of Truth.

# Consequences

## Positive

- The same eligible history always produces the same projection.
- Evidence state remains explainable and recomputable.
- P8-02 can be tested without infrastructure or provider availability.
- The Phase 7 gate remains a hard integration boundary.

## Negative / Tradeoffs

- Only the two most recent observations determine the state, although all eligible observations remain counted for provenance.
- The branch may require reconciliation if Phase 7 completion changes a relevant contract.
- The implementation is intentionally not merge-ready while Phase 7 remains incomplete.

# Security & Privacy Impact

No authorization boundary is relaxed. The projector has no API, repository, network, logging, telemetry, or global mutable evidence state. It accepts only normalized evidence metadata, rejects duplicated event IDs, and does not contain provider prompts, raw provider output, or learner responses.

# Educational/Product Impact

LearningEvidence remains broad evidence rather than mastery. It must map to real learner activity, and invalid AI evaluation or presentation-only completion cannot update it. Reflection stays secondary to observed performance.

# Cost / Infrastructure Impact

No new dependency, service, database migration, provider, or infrastructure is introduced.

# Migration Impact

None. The P8-01 schema remains the persistence contract.

# Testing Impact

P8-02 requires focused table-driven domain tests covering every approved dimension, deterministic ordering, metadata, and invalid inputs. Broader validation, implementation review, and independent security review remain required before merge.

# Documentation Changes Required

Add ADR-0012 to the ADR index. Do not rewrite ADR-0011; this later explicit decision governs the bounded P8-02 exception.

# Approval

**Status:** ACCEPTED

**Approved By:** Project Hippocampus Team
