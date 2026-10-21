---
ADR: ADR-0018
Title: Runtime Response-Evaluation Authority and Historical Evidence Eligibility
Status: ACCEPTED
Date: 2026-10-19
Decision Owners: Project owner (explicit conversational approval, 2026-10-19); independent medical/security/data claims remain separately reviewed
Categories: EDUCATION, DOMAIN, AI, SECURITY, DATA, BACKEND, TESTING
Affected Documents: 11, 15, 18, 27, IMPLEMENTATION-TRACKER; scoped clarifications to ADR-0012, ADR-0013 and ADR-0016
Supersedes: None
Superseded By: None
---

# Context

The recovered October 15 SEC-004 report requires independently evidenced student meaning for relationship credit across all linked assertions; unsupported or uncheckable attribution must become UNCERTAIN. It requires plain-response, multi-assertion, negation and contradiction regressions while retaining legitimate paraphrase and constituent PARTIAL capability when independently qualified. Its stated checkpoint is `1261587be7150bed981481f56fd494daaab9e9e4`. The authentic recovered message and provenance are preserved in `.validation/sec004-recovery-assessment-20261019-v1/`; this proposal does not rewrite that report.

The separate October 19 assessment reports two High surfaces: unsupported full-relation credit, including within PARTIAL, and consumption of persisted unqualified free-text grades as active competence evidence. New free-text submissions are currently doubly contained, but grade strings can still affect state assembly and direct follow-up lifecycle predicates. No affected deployed records have been demonstrated; no authorized deployed data source has been examined in this work.

Reviewed source is the read-only `.validation/phase7-gate1-remediated-candidate-20261019`, manifest SHA-256 `0de8c917647a76d5d9d300589373a0c651632968bc577dedac06ea911bb99941`, combined patch SHA-256 `199662341e75e04efa8a9d9016d5dfa8c24cd79c8f40183387e1bbec347726af`. All 1,192 manifest-listed files were rechecked before preparation. This document is outside that candidate. ADR-0018 is the next unused identifier in the inspected ADR directory and Document 27 index.

Documents 11 and 18 require application-owned traceable evidence, not model-owned learning state; Document 15 separates structural checks, human medical qualification and operational availability. Accepted ADR-0010 protects presentation-only progression without synthetic evidence; ADR-0011 defines layered qualification; ADR-0012 defines bounded attribution and non-scoring uncertainty; ADR-0013 applies to safely graded follow-up; ADR-0016 defines assessment-unavailable continuation; ADR-0017 protects artifact origin and historical records. None establishes qualification of old free-text evaluations. Proposed ADR-0014/0015 are not accepted grading authority.

This is a containment and eligibility decision. It does not define a new medical semantic recognizer or enable free-text grading.

# Decision proposed for owner approval

## 1. Runtime authority

1. Keep both adapter and submission containment for every currently unqualified free-text response, regardless of provider, prompt, VALIDATED flag, exact spans, source support, confidence, two-model agreement, or alternate evaluator implementation. An accepted unresolved submission remains a real neutral UNGRADED attempt with empty scoring claims; infrastructure, authorization and malformed-input errors retain their reject/no-write behavior.
2. Continue deterministic MCQ grading under the existing owned, validated response contract and source/mission authorization. Option comparison is trusted deterministic evaluation only within that contract; it is not a new attestation of medical answer-key correctness. Do not infer MCQ status from activity type, nonblank correctOption alone, payload text, client flags or a forged deterministicResult marker.
3. Semantic validation and aggregation helpers cannot independently confer trusted CORRECT, PARTIAL, INCORRECT, correctConcepts or sufficiency. In this bounded correction the production `VerifiedResponseEvaluationAggregator.aggregate` must also fail closed to the existing neutral UNCERTAIN result; no caller-supplied boolean/token/label may unlock grading. Preserve structural attribution diagnostics and adversarial fixtures without calling them medical proof. Any future observational semantic calculation belongs in explicitly isolated qualification tooling and cannot be wired into submission/persistence.
4. A future free-text exception requires a separately approved runtime verification basis, exact qualification identity, per-answer evidence rules and independent medical/security verification. No such basis is admitted by this proposal, and no grandfather allowlist is introduced. Qualified paraphrase and independently demonstrated constituent credit remain requirements for that future capability, rather than being declared satisfied by blanket abstention.

## 2. Historical eligibility, separate from historical truth

Retain original attempts, response payloads, grades, feedback, receipts, source/audit provenance and completed lifecycle history. Exact authorized replay keeps its original outcome. Never delete, relabel, fabricate or backfill an evaluation to make it eligible.

Before any persisted grade affects current understanding, recall, connection/application evidence, failure/scaffolding counts, difficulty, prerequisite restoration, follow-up origin/selection, completion rationale or future mastery inputs, require one application-owned eligibility decision using the owned activity, attempt and trusted contract facts. Raw evaluationStatus alone is never sufficient.

For this correction:

- Free-text evaluations, including historical CORRECT/PARTIAL/INCORRECT, are ineligible for all active grade-derived decisions. No existing free-text provenance establishes the required qualification basis. Missing or ambiguous evaluation method is ineligible, not guessed from timestamps, model identity, feedback or apparent answer quality.
- Deterministic historical MCQ evidence remains eligible only when the server can establish the owned attempt/activity/artifact/mission binding, the existing trusted validated MCQ contract, allowed selected option, CORRECT/INCORRECT outcome, matching deterministicResult, and a consistent recomputation of that outcome from the stored selected response under that same contract. PARTIAL is not a deterministic option-comparison result. Do not manufacture a missing marker or use a current conflicting/unverifiable contract to attest historical truth. Ambiguity is ineligible. Do not impose ADR-0017 reuse-origin requirements on ordinary historical display or MCQ evidence merely because they apply to new artifact reuse.
- An unknown but well-formed provenance is a non-scoring eligibility result. An invalid outcome enum, cross-owner/activity binding, corrupt receipt or structurally inconsistent stored object remains a sanitized data/authorization error. Do not conceal corruption by converting it into a successful UNGRADED submission.
- Preserve real activity chronology, source restrictions and anti-repetition facts. An ineligible response is not a completed presentation and not a failed attempt. Pass explicit effective eligibility/non-scoring facts to policies; do not omit the response in a way that makes the previous favorable grade appear latest.

Read/display history and trusted decision history are separate projections of the same immutable records. No outward replay outcome is rewritten to the effective policy disposition.

## 3. Continuation and existing follow-up obligations

Use the same eligibility decision in state reconstruction, availability, all direct CORRECT predicates, intent origin checks, prerequisite restoration, intent creation and final continuation/materialization admission. Include existing nonterminal intents, even those whose JSON basis says DETERMINISTIC_MCQ; that string is not independent proof.

GET must suppress ineligible grade-derived intent/action projections without writes, provider calls or receipt mutation. On an authorized write under the existing mission lock, an ineligible nonterminal intent is canceled using existing CANCELED/SAFETY semantics, with bounded diagnostic reason `EVIDENCE_INELIGIBLE`; its immutable grade, basis, origin and scope remain unchanged. Do not call it SATISFIED, mark a student wrong, or create a replacement graded intent. Terminal intent audit rows remain untouched. A previously materialized activity remains part of history; its future use/submission requires independent current admission, not trust inherited from the invalid origin.

Distinguish a real saved UNGRADED attempt from a saved graded attempt whose evidence is now ineligible. Extend typed continuation facts to represent that distinction; never set savedUngraded=true for a stored CORRECT/PARTIAL/INCORRECT receipt. Both can justify a non-scoring continuation guard when a real response exists. Preserve ADR-0016 safety/source/time/stop/terminal precedence: known unassessable free-text targets produce the existing limitation/Pause behavior without another futile response activity; unknown target authorization/eligibility fails closed through existing sanitized recovery. A later independently eligible MCQ or presentation remains possible only if the existing Engine selects it. No synthetic response, grade, failure, automatic mission reset or reopening of terminal missions is permitted.

Revalidate eligibility and consulted contract/source/intent facts in the final short write transaction, preserving existing canonical source locking, mission snapshot checks and duplicate request handling. Do not trust an earlier read or a grade/next-stage supplied directly to internal persistence. No AI/network calls occur inside that transaction.

## 4. Persisted provenance and schema decision

Existing `student_attempts` fields (V21/V25) record grade, optional evaluation artifact, deterministic result, receipt and limitation/request identity. `deterministic_result` contains the outcome name, not a signed evaluation-method receipt. Generated-artifact provider/model/prompt/VALIDATED/source fields describe content provenance; follow-up basis is derivative JSON. None binds a historical free-text evaluation to an approved rubric, complete execution identity, independent qualification attestation and per-answer semantic authority.

**Existing provenance is insufficient to admit historical free-text grades. It is sufficient to implement conservative exclusion without a new schema.** The recommended bounded implementation uses no migration, no StudentAttempt field addition and no historical backfill. Eligible MCQ is a positive intersection of existing corroborated facts; unresolved old MCQ can lose active eligibility rather than being guessed trustworthy. New deterministic submissions are verified at both application and final persistence boundaries.

If later work needs to admit independently requalified historical free-text results, a separate proposal must define an append-only qualification binding to the exact attempt, owner/activity, immutable contract/source/output digests and authentic qualification decision/version. A nullable evaluator-name column or JSON flag cannot establish trust. A minimal compatible design would be one separate binding table keyed by attempt ID and constrained to its owner/activity, leaving StudentAttempt/receipt bytes unchanged; it would require authentic decision references, immutability, revocation/eligibility semantics and final-transaction checks. This future migration is **not necessary, numbered, accepted or authorized here**. Do not build a general qualification framework speculatively.

# Rationale

A small shared eligibility projection closes both evidence aggregation and lifecycle bypasses without altering what the student actually did. Unknown identity fails closed and legitimate deterministic behavior retains a positive admission path. Removing standalone aggregation authority closes the helper surface without pretending lexical heuristics solve arbitrary medical entailment. No new provider, service, event system, dependency or Phase 8 persistence is needed.

Composition is sufficient: one cohesive application eligibility capability returns typed decisions; the existing domain policies consume trusted facts and remain repository/framework independent. Reuse it across consumers rather than adding one independent filter per use case. Do not introduce strategy registries or a pluggable grading-authority framework while only deterministic admission and free-text denial exist.

# Alternatives considered

## Preserve old grades as automatically grandfathered evidence

Rejected as preferred policy. Immutable preservation does not establish medical correctness or qualification. It leaves the fresh historical-consumption surface open.

## Trust artifact/model metadata, spans, model consensus, or PARTIAL labels

Rejected. These facts cannot establish that the student expressed a complete relation, polarity or qualifier; PARTIAL can still contain invented full-relation credit. An evaluator name is not authentic qualification provenance.

## Filter only the state assembler or only new submissions

Rejected. Follow-up origin, prerequisite restoration, availability and internal writes contain separate grade consumers. Both runtime and persisted-evidence boundaries must agree.

## Rewrite/delete/regrade old attempts or add a backfilled trusted flag

Rejected. This destroys or fabricates audit truth and can convert an unqualified judgment into apparent evidence. No historical regrading/provider run is authorized.

## Add qualification schema now

Deferred. A future qualified exception needs richer authenticated evidence, but no qualifying basis currently exists. A default-ineligible projection closes the current bounded surface with less schema and no invented backfill. Extra attestation storage would not by itself solve semantic correctness.

## Disable all grades, including trustworthy deterministic MCQ

Rejected as unnecessarily broad. The positive deterministic contract can remain, with ambiguous history excluded and medical key qualification kept separate.

# Consequences

Positive: no active competence/failure credit from unqualified history; exact audit replay survives; one auditable decision covers every consumer; no migration or backfill required for the bounded correction.

Tradeoffs: some historical progress signals and grade-derived follow-ups cease to drive current policy; ambiguous MCQ can become ineligible; learners may see an unchanged historical grade alongside current assessment limitation. There is no automatic rollback of prior lifecycle decisions, erasure of old feedback, or claim of restored graded free-text availability. Deployed exposure and any previously materialized derived summaries remain unknown until authorized audit. Repair of any demonstrated durable projection needs separate scoped authorization; never edit underlying attempts as a shortcut.

# Security and privacy impact

Preserve authorization below transport, immutable mission/objective binding, frozen source scope/revocation, sanitized errors, CSRF/session behavior and current lock order. Deny client/model influence over eligibility and prevent internal direct persistence from accepting free-text grades or fabricated deterministic markers. Limit diagnostics to IDs/reason codes; do not copy response/source text into logs. No deployed record absence assertion is made. Any data audit is read-only, operator-controlled and separately scoped to an authorized data source.

# Educational/product impact

Completion is not mastery; presentation is not response evidence. Excluding unqualified CORRECT, PARTIAL and INCORRECT avoids both invented competence and invented struggle. Genuine constituent/paraphrase capability, Case 3/5 medical adjudication and availability remain open in the preserved P7-09 qualification packet. Deterministic MCQ admission does not enlarge medical key authority.

# Cost and infrastructure impact

No new infrastructure, dependency or required provider call. Reuse owned attempt/contract facts within each use case; avoid repeated queries per policy predicate. Preserve short transactions and existing source-lock order; test contention and contract/source changes rather than assuming caches are safe.

# Migration impact

None for the recommended bounded correction. No SQL migration is to be created under this approval. A later qualification binding or audit-discovered projection repair requires a separate explicit packet/decision.

# Testing impact and comparison with original SEC-004

The original acceptance requirements remain: plain denervation response, multi-assertion, negated/contradictory relationships, legitimate alternative wording and true constituent meaning. Add the fresh full-relation-under-CONSTITUENT/PARTIAL example and malicious verifier agreement. Under the bounded correction all free-text scoring entry points and the production aggregator return non-scoring results, irrespective of simulated labels. This proves authority containment only; it does **not** satisfy the original future graded paraphrase/constituent capability or medical qualification requirements.

Additional controls beyond the original report: historical CORRECT/PARTIAL/INCORRECT eligibility, ambiguous method/provenance, forged MCQ marker, follow-up basis/origin and prerequisite predicates, non-scoring availability facts, safe immutable replay, GET/Pause/resume/restart and final concurrent continuation/persistence revalidation. Preserve supported deterministic MCQ and negative authorization/source/CSRF cases. Required unit, PostgreSQL and broader operator validation are specified in `docs/testing/sec-004-adr-0018-remediation-packet-v1-2026-10-19.md`.

SEC-004 remains High throughout proposal, implementation and testing. Only fresh independent general review followed by separate independent security verification can support closure of the explicitly scoped containment/eligibility finding. The reviewers must state whether original graded-capability criteria remain open under a distinct tracked qualification blocker; no automatic full historical SEC-004 closure is authorized. A containment SECURITY PASS cannot enable free-text grading, waive Golden medical qualification, approve final Gate 2 globally or complete P7-14 M3.

# Documentation changes required after approval

Version-aligned patches in the **new derivative**: Document 11 sections 4/25-29 and continuation/follow-up facts; Document 15 sections 31-35 and qualification separation; Document 18 sections 32/33/57 and integrated follow-up semantics (no schema change); Document 27 accepted index/summary; narrowly scoped accepted ADR-0012/0013/0016 clarifications linking this decision without rewriting their historical approval; tracker evidence/blocker references without Done transitions. Preserve candidate-specific supplements in Documents 11/18; do not replace them with the shorter working-checkout copies. ADR-0017 origin policy and proposed ADR-0014/0015 statuses remain unchanged.

# Approval and execution boundary

ACCEPTED on 2026-10-19 by explicit conversational project-owner approval titled ?HIPPOCAMPUS ? ADR-0018 V1 OWNER APPROVAL?. The user asserted project ownership; no independent identity authentication or additional stakeholder approval is claimed. The owner approved this v1 policy and bounded remediation packet, including no schema migration/backfill, positively verified deterministic MCQ, immutable history, shared eligibility, distinct historical non-scoring facts, SAFETY cancellation and a new isolated Git-backed derivative.

Implementation and specified focused validation are authorized. Required operator broader validation, independent implementation review and separate security review remain evidence gates. This acceptance does not approve free-text grading, Golden thresholds, SEC-004 closure, a deployed-data audit, final Gate 2/P7-14 or publication. No commit, push, PR, merge, deploy or tracker Done is authorized. A new schema, different grading authority or broader architectural deviation requires another decision.
