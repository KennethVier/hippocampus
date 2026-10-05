---
ADR: ADR-0011
Title: Isolated Pre-Gate Phase 8 Development
Status: ACCEPTED
Date: 2026-10-05
Decision Owners: Project Hippocampus Team
Categories: ARCHITECTURE, BACKEND, DATA, OPERATIONS, TESTING
Affected Documents: 26, IMPLEMENTATION-TRACKER
Supersedes:
Superseded By:
---

# Context

Phase 7 implementation is merged, reviewed, and security-reviewed, but Phase 7 is not yet complete because P7-09 still requires successful provider/model qualification for `RESPONSE_EVALUATION`, followed by P7-14 Phase 7 gate evidence.

The remaining blocker is external/provider qualification availability rather than an unresolved Phase 7 application architecture decision. Waiting for that external condition would leave engineering capacity idle even though P8-01 has an already-defined Source-of-Truth contract in Document 18 and depends on persisted P7 attempts rather than on a specific Gemini/Ollama model.

Document 26 remains authoritative for phase completion and integration order. The project therefore needs an explicit rule for whether narrowly scoped Phase 8 implementation may proceed before Phase 7 is formally passed.

# Decision

Permit **P8-01 only** to be implemented before P7-14 passes, subject to all of the following controls:

1. Work occurs only on the isolated branch `phase8/p8-01-learning-evidence-schema` created from the current `main` after PR #220.
2. No Phase 8 implementation from that branch may merge into `main` until:
   - P7-09 provider/model qualification is complete;
   - P7-09 is eligible for `Done` under the tracker;
   - P7-14 passes;
   - Phase 7 is formally recorded as PASS / M3 achieved.
3. P8-01 remains limited to the Document 18 `evidence_events` and `learning_evidence` persistence foundation and its migration/FK validation.
4. P8-02 and later Phase 8 tasks are not authorized by this ADR before P7-14 passes.
5. P8-01 must not depend on a concrete Gemini/Ollama model, provider-specific semantics, or successful `RESPONSE_EVALUATION` qualification.
6. No Phase 7 code, provider routing, prompt, Golden dataset, Learning Engine policy, or Study Mission behavior may be changed as part of P8-01.
7. Before any Phase 8 merge after P7-14 passes, the Phase 8 branch must be reconciled with the then-current `main`, required validation rerun, implementation review completed, and the independent security gate rerun for the final merge candidate.
8. If the final Phase 7 gate reveals a contract or persistence requirement that conflicts with P8-01, the Phase 8 branch must be corrected or replanned before merge. The pre-gate implementation receives no grandfathered merge entitlement.

This decision allows parallel development only. It does not change the authoritative phase-completion or integration sequence.

# Rationale

P8-01 is a bounded persistence task whose schema is already defined by Document 18:

- `EvidenceEvent` is the traceable event-level basis;
- `LearningEvidence` is a recomputable aggregate/projection;
- evidence is application-owned rather than LLM-owned;
- invalid AI evaluation must not update learning evidence.

Because P8-01 does not itself create evidence from live AI output and does not depend on a particular provider/model qualification result, implementing the schema on an isolated non-mergeable branch does not weaken the Phase 7 gate.

The branch restriction preserves the practical value of the roadmap gate: no Phase 8 behavior becomes authoritative in `main` until Phase 7 is proven complete.

# Alternatives Considered

## Wait for Phase 7 to fully pass before any Phase 8 work

Rejected for this specific external-wait condition because it unnecessarily idles implementation capacity while P8-01's schema contract is already authoritative and independent of the outstanding provider qualification.

## Allow all Phase 8 work in parallel

Rejected because later Phase 8 tasks depend on projection policy, attempt-to-evidence integration, review behavior, and broader contracts that have more direct coupling to final Phase 7 behavior. The exception is intentionally limited to P8-01.

## Merge P8-01 into `main` before P7-14

Rejected. This would defeat the Phase 7 gate and make Phase 8 integration authoritative before M3 is proven.

# Consequences

## Positive

- Engineering can make productive progress during external provider qualification delay.
- The Phase 7 gate remains a hard integration boundary.
- P8-01 stays isolated from provider/model qualification.
- Any Phase 7-driven incompatibility can be corrected before merge.

## Negative / Tradeoffs

- The Phase 8 branch may require rebase/reconciliation work once Phase 7 closes.
- Some P8-01 implementation may need revision if P7-14 exposes a relevant persistence contradiction.
- The branch is intentionally not merge-ready while Phase 7 remains incomplete.

# Security & Privacy Impact

No security boundary is relaxed.

- Cross-user evidence isolation remains mandatory.
- `user_id`, topic/activity/attempt relationships, and all foreign keys must preserve existing ownership and data-integrity constraints.
- AI/provider output remains untrusted.
- No production behavior changes until the branch passes final review/security and is merged after Phase 7 PASS.

# Educational/Product Impact

No pedagogical policy changes.

- LearningEvidence remains evidence, not mastery.
- Broad evidence states remain preferred over false precision.
- Evidence must map to real student activity.
- No evidence is created merely because an LLM generated a label.

# Cost / Infrastructure Impact

No new service, infrastructure dependency, cache, broker, or external provider is introduced.

# Migration Impact

P8-01 may add only the Flyway migration(s) and persistence mapping necessary for the Document 18 `evidence_events` and `learning_evidence` schema.

All schema changes remain Flyway-owned. Production Hibernate auto-update remains prohibited.

# Testing Impact

P8-01 must include focused migration/FK/integrity tests required by the tracker. Before eventual merge, the final branch must also satisfy the normal implementation review, CI, and independent security gates against the current `main`.

# Documentation Changes Required

This ADR is the explicit branch-level sequencing exception. It does not change Phase 7/Phase 8 completion or merge order in Document 26; therefore no higher-level product/technical contract is changed.

The implementation tracker remains authoritative for task status. Phase 7 must remain incomplete until its existing completion criteria are satisfied.

# Approval

**Status:** ACCEPTED

**Approved By:** Project Hippocampus Team
