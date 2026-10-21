---
ADR: ADR-0016
Title: Assessment-Unavailable Study Mission Continuation
Status: ACCEPTED
Date: 2026-10-16
Decision Owners: Project Hippocampus Team
Categories: EDUCATION, DOMAIN, BACKEND, FRONTEND, SECURITY, TESTING
Affected Documents: 06, 07, 11, 15, 19, 20, 26, 27, IMPLEMENTATION-TRACKER, ADR-0014, ADR-0015
Supersedes: None
Superseded By: None
---

# Context

The P7-09 interim SEC-004 containment defers free-text grading without an
independently approved runtime verification basis. A validated UNCERTAIN
submission persists as UNGRADED without correctness, failure or mastery
evidence. Owner-reported 104/104 focused tests and scoped reviews support
offline containment, not medical qualification or student-facing rollout.

The existing Engine can select RETRY after UNGRADED. Materialization can copy
the same artifact or generate another free-text activity still lacking a basis.
Continue also sends non-materializable actions to the materializer, while its
HTTP 204 response and frontend assume success means a new activity.

ADR-0016 is unused in the local index and ADR directory as of this proposal.
This separate decision preserves accepted ADR-0012 without amendment.
ADR-0014 and ADR-0015 remain PROPOSED; their design approvals neither accept
this decision nor authorize P7-16 implementation.

# Decision

Accepted on 2026-10-16 after the role-specific owner approvals and independent
requirements reviews recorded below. Implementation remains unauthorized.

## 1. Exact continuation rule

After a saved UNGRADED attempt, prevent reuse **and regeneration** of a
response-bearing activity that application-owned evidence establishes is
still unassessable. Preserve existing higher-priority lifecycle, source,
safety, time and anti-repetition decisions. When no existing applicable policy
selects a safe alternative or terminal action, use existing
COMMUNICATE_LIMITATION with proposed rationale ASSESSMENT_UNAVAILABLE.

Keep learner-controlled Pause. Add no Engine action, mission status, automatic
Pause/Stop/completion, grading exception or new activity-selection sequence.
An independently selected COMPLETE remains an existing action; only explicit
Continue/Finish may commit it. Assessment unavailability alone cannot select it.

Generic UNGRADED is insufficient. Application assembly must establish the
owned saved attempt, current/relevant activity contract, authorized frozen
source scope and server-known missing verification basis. Distinguish known
unavailability, unknown eligibility, response-specific uncertainty, insufficient
sources and infrastructure failure. No client flag, model label, validated
artifact, new UUID or generated answer establishes grading authority.

Under current blanket containment, the application can identify affected
free-text generation targets before generation. Resolve reuse against the
selected owned artifact and its immutable ADR-0017 origin, not just the current
artifact or a later linked activity. Pass minimal typed facts
to the Engine; no repositories/providers in domain policy. Unknown target
classification is a stop condition, not permission to generate. This task must
not establish a selective eligibility catalog or enable grading.

## 2. Compatibility-safe API contract

Use explicit query opt-in `continuationContract=1` on existing mission GET and
Continue POST routes. This versions only the continuation representation; it
is not an authorization/eligibility flag. Omitted opt-in retains legacy shape.
Unsupported explicit values return sanitized HTTP 400. Request processing must
not let omission or tampering bypass the Engine rule.
Parse the raw query as zero or exactly one value: omission is legacy; one exact
`1` opts in. Empty, repeated (including identical), mixed or other values return
400 before any presentation completion, materialization or lifecycle write.

Legacy admission must occur before the current presentation-completion write.
For an unfinished presentation, require opt-in and return the same upgrade 409
without completing it. This intentionally narrows legacy continuation support:
only already-completed activities can use legacy dispatch. It avoids committing
presentation completion before discovering an unrepresentable result. Updated
clients retain existing explicit presentation completion; the new unavailable
rule concerns saved response attempts, not presentation evidence.

| Caller | Mission GET | Continue POST |
| --- | --- | --- |
| Legacy, no opt-in | Exact existing shape; no new field in strict DTO | Existing HTTP 204 only after actual activity creation. For a valid non-materializable result, HTTP 409 Problem Detail with `CONTINUATION_CONTRACT_UPGRADE_REQUIRED`, before any completion/write/provider call. |
| Opt-in v1 | Existing fields plus required nullable `continuation` | HTTP 200 JSON discriminated result, only after successful dispatch/commit. |

Opt-in success variants:

```json
{"outcome":"ACTIVITY_CREATED","activityId":"<new-current-activity-uuid>"}
```

```json
{"outcome":"NO_ACTIVITY","actionType":"COMMUNICATE_LIMITATION","rationaleCode":"ASSESSMENT_UNAVAILABLE"}
```

NO_ACTIVITY carries no dummy activity ID and only an applicable existing
non-materializable action/rationale. COMPLETE returns NO_ACTIVITY only after
its lifecycle transaction commits. GET's continuation view is
`{actionType,rationaleCode,canMaterialize}` or null for no current decision;
it never writes, calls a provider, completes a mission or changes a receipt.
Read projection is available only for an appropriate ACTIVE completed activity;
paused/terminal states retain lifecycle controls, not reconstructed ACTIVE input.

The opt-in frontend requires this read field and validates both POST variants.
It must reject empty/204 or malformed opt-in success; no blind legacy fallback.
Use a distinct query-cache key for the opt-in read contract. A missing current
decision disables response-bearing continuation until explicit recovery, not Pause.
An unfinished, owned presentation-only activity is the explicit exception:
preserve its existing learner-invoked presentation completion control, identified
from the current activity/response contract and status. Its null continuation
metadata must not disable that control. The opt-in POST performs the existing
presentation completion and then computes/dispatches the Engine decision;
GET does not simulate completion, choose a next action or write evidence.
Unknown/malformed activity contracts do not qualify for this exception. Only
ACTIVITY_CREATED sets activity-created confirmation. NO_ACTIVITY renders the
limitation/action and refetches without latching a false creation state.
COMMUNICATE_LIMITATION offers Pause; COMPLETE offers explicit Finish; existing
source/dependency actions retain their authorized views/manual recovery.

Preserve existing authorization/CSRF, owned-current-activity admission and
sanitized infrastructure errors. Source authorization failures remain failures,
not NO_ACTIVITY. Actual stale snapshots/lifecycle conflicts remain HTTP 409
with existing codes; new clients refetch, keep controls disabled on failed
recovery, and do not retry a mutation automatically. After an ambiguous network
result, refetch before any learner-initiated retry.

Cached legacy clients ignore a JSON POST body and reject additional GET fields;
therefore replacing 204 with 200 globally is unsafe. Their existing 409 handler
refetches the unchanged legacy read without automatically resubmitting. It may
still display a generic conflict and Continue button; this is safe denial, not
a satisfactory limitation experience. It cannot be repaired by an API payload
alone. Require client reload for the full experience; do not claim old clients
display new copy. Repeated legacy requests remain write-free denials.

Deployment sequence, **not deployment authorization**: compatibility backend
first, verify legacy/opt-in mixed versions, then opt-in frontend; any student
activation requires separate approval. An old backend with a new frontend must
fail closed on missing required read metadata/POST result. Do not roll back to
a backend lacking the containment/continuation guard while affected clients
are active. Preserve legacy representation and safe denial until explicit
client-retirement approval; no invented cache-expiry guarantee. Reads containing
owned dynamic state must not enter shared caches; separate opt-in/client cache
entries and test existing private/no-store transport policy. Do not change the
shared API client's controlled Accept headers just for negotiation.

## 3. Completion transaction owner

Confirmed existing owner: `learning/application/ChangeStudyMissionStatusUseCase`.
Its transactional execute already obtains the authenticated owner and locks the
owned mission through `StudyMissionRepository.findOwnedByIdForUpdate`.
`SpringDataStudyMissionRepository` uses PESSIMISTIC_WRITE; repository save joins
the application transaction. MissionStateMachinePolicy already permits
ACTIVE -> COMPLETED. **No Engine-completion operation exists there today.**

Extend this owner with a separate application-only completion operation, invoked
through the injected transactional bean from Continue. Do not add COMPLETE to
the public Pause/Resume/Stop command enum, use a self-invoked transactional
method, or introduce another transaction mechanism. Keep decision computation
in the existing Engine/assembly responsibility, not lifecycle policy.

Before mutation, the short transaction must authenticate ownership, lock the
mission, check ACTIVE, expected current activity ID, mission updatedAt, activity
COMPLETED status/completedAt and the saved-attempt identities/count/version
facts used in selection. Reconstruct/revalidate the current authorized decision
inputs and require Engine COMPLETE using the current clock. All revalidation
must be local and provider-free; if required source/context facts cannot be
safely revalidated without external work, stop for a scoped decision. A DTO or
previous GET action is never completion authority. Relevant concurrent writers
must use the same mission-lock discipline; integration evidence must verify it.

Save status COMPLETED, completedAt/updatedAt and only the stage already required
by the existing Engine COMPLETE contract. Preserve activity, attempt, receipt,
frozen scope and all evidence dimensions; lifecycle completion is not mastery.
No attempt/artifact/activity is appended. Concurrent Pause/Stop, new activity
or changed attempt snapshot causes the existing conflict/refetch behavior.
Duplicate terminal Continue returns lifecycle conflict, not a second timestamp
write or success claiming another activity. This is at-most-once mutation with
read recovery, not a new durable idempotency protocol. Preserve response POST
idempotency independently.

### 3.1 Source-revocation ordering — requirements resolution, 2026-10-16

SEC-016-REQ-001 is **resolved at architecture/security requirements level** by
the separately reviewed [ordering design and test matrix](../testing/p7-16-source-revocation-ordering-resolution-2026-10-16.md).
Use commit-time source validity, not an earlier preparation snapshot:

1. In the existing mission-status application boundary, require effective
   READ COMMITTED and the authenticated owned-mission write lock; validate
   current completed activity, lifecycle and saved-attempt/mission snapshots.
2. Enumerate only the finite server-owned dependency set actually used by
   completion authorization/Engine: current activity references and consulted
   frozen scope facts, their frozen scope document nodes and nonnull targets.
3. Acquire individual, deduplicated source rows FOR SHARE NOWAIT, ascending
   PostgreSQL UUID order within fixed table order:
   `material_versions -> materials -> source_references -> document_nodes -> chunks -> visual_assets`.
   Do not use a nullable outer-join lock, aggregate lock, SKIP LOCKED or rely
   on a planner's multi-table acquisition order.
4. Freshly re-read the full locked identities/mappings and authorization
   predicates from the database. A changed/missing/unexpected dependency
   aborts; do not expand/restart under partial locks. Preserve exact frozen
   semantics, not current active-version/topic-link or new readiness rules.
   Source-required empty references cannot pass vacuously; preserve only
   explicitly authorized existing source-free contracts separately.
5. Recompute Engine COMPLETE using current local locked facts/time, save only
   lifecycle completion and hold every lock through atomic commit. No provider
   call, source mutation, attempt/activity/artifact/evidence or receipt change.

Soft deletion's existing PostgreSQL UPDATE conflicts automatically with the
material share lock. Locking the other positive dependencies also protects
page-count, chunk activity and referenced identity predicates. Do not add a
shared revision, new transaction mechanism or speculative revocation feature.
The isolation/transaction must actually take effect: no self-invocation,
incompatible outer transaction or stale JPA-managed source values as authority.

Revocation committed first causes authorization denial; completion obtaining
all locks first holds a conflicting revocation until completion ends. An
in-flight revoker that already holds a required row makes completion roll back.
Translate only acquisition SQLSTATE 55P03 after full rollback to existing
sanitized stale 409; authorization failures retain existing source errors and
unrelated database failures retain infrastructure semantics. No automatic
retry or global deadlock-free claim: NOWAIT applies to source row acquisition,
not all database/table waits. The existing mission lock retains lifecycle race
and terminal duplicate protections. No historical completion or receipt is
rewritten when a source is later revoked; future access remains denied.

The linked design records actual invalidating paths, ordinary non-revoking
version/lifecycle changes, exact failure outcomes and future PostgreSQL tests.
It is a design resolution, not evidence that the operation exists or is verified.

Do not reuse PersistActivityResponse for this purpose: it requires an unfinished
activity and appends an attempt. PersistPresentationCompletion completes only
an unfinished presentation and preserves mission status. PersistMaterializedActivity
creates an activity. Weakening any of those guards would fabricate activity or
evidence. Their existing transactions remain unchanged.

## 4. Minimal implementation and phase ownership

P7-16 is registered Not Started, separate from P7-09 grading containment and P7-15 graded
follow-up. Scope: typed availability facts and narrow Engine rule; shared pure
read/Continue decision; explicit dispatch; lifecycle-owner extension; opt-in
DTO/controller/schema/hook/UI and limitation copy; focused tests. Accepted
ADR-0017 authorizes the narrow forward-only artifact-origin binding migration
and same-objective/same-mission reuse checks needed for trustworthy target
facts. The earlier no-schema assumption does not apply to that scoped change.
No other infrastructure, dependencies, providers, grading profiles,
generation/prompt change, new learning mode or Phase 8 evidence/progression
feature is authorized.

Dependencies: approved ADR-0016 and aligned SOT; existing P6 policy contracts
(P6-01/02/07/08/09/10/11); P7-04/05/06/09/13 surfaces, with actual validation
gates preserved. P7-15 stays a sibling and ADR-0013 is unchanged. The owner
explicitly authorized administrative P7-16 registration on 2026-10-16 with
Not Started status. P7-14 depends explicitly on it, not vice versa. The subsequent
owner-role replies complete policy acceptance; registration and acceptance are
not implementation authority. The versioned roadmap amendment now records
16 Phase 7 items including the gate. P7-14 still
requires all existing P7 work and medically qualified Golden evidence; no gate
waiver or tracker completion follows from this proposal.

# Rationale

An unavailable assessment must not repeatedly demand a response that cannot
produce trustworthy learning evidence. Existing limitation and Pause behavior
can communicate this honestly without another Engine action. Explicit transport
outcomes fix a demonstrated client/server mismatch; the existing lifecycle
owner supplies the needed transaction foundation without another mechanism.

# Alternatives Considered

- Continue retry/regeneration: rejected; a different UUID or question does not
  restore assessability and can indefinitely repeat unavailable assessment.
- Automatic Pause/Stop/MCQ/formative substitution: rejected for this task; adds
  unapproved educational behavior or overrides learner/Engine authority.
- Globally change 204/GET fields: rejected; cached strict clients break or
  falsely confirm creation. Explicit opt-in adds bounded compatibility tests.
- New transaction service or idempotency store: unnecessary now; reuse the
  existing lifecycle owner and locks. The demonstrated artifact-origin blocker
  is resolved by accepted ADR-0017's narrow schema exception.

# Consequences

## Positive

Safe limitation preserves saved work and honest learning evidence; explicit
dispatch avoids non-materializable-action failures and false creation signals.

## Negative / Tradeoffs

Safe limitation preserves work and honest evidence but reduces usable grading
and can halt a mission. It does not establish adequate adaptive learning or
medical correctness. No grading-availability percentage is known. Historical
free-text grading exposure remains a separate authorized read-only audit.

# Security & Privacy Impact

Availability and completion authority remain server-owned and scoped to the
authenticated mission and frozen sources. No source/student contents are added
to transport or logs. Stale clients are denied safely; versions cannot grant
eligibility. Concurrency, source revalidation and no-write behavior require
implementation evidence before approval for rollout.

# Educational/Product Impact

Preserve saved work and explicit Pause/Finish choices without treating a
limitation as learner failure, success or mastery. Legacy unfinished
presentation users need a client reload. Bounded-generation coverage and
historical evidence remediation remain separate decisions.

# Cost / Infrastructure Impact

No new infrastructure, dependencies or provider work. ADR-0017 adds only the
scoped origin schema, local decision reads and compatibility tests.

# Migration Impact

ADR-0017 adds a forward-only origin schema migration without speculative
legacy backfill; no receipt migration. Backend-first opt-in compatibility and safe
legacy denial precede any separately authorized client activation. Retiring
legacy handling requires an explicit decision, not assumed browser cache expiry.

# Testing Impact

No tests were run for this documentation proposal. Later required evidence:

1. Saved UNGRADED plus independently known target unavailability blocks RETRY,
   REUSE and generation; response-specific uncertainty alone does not trigger it.
2. Lifecycle/source/safety/time/anti-repetition precedence and legitimate
   presentation support survive. No provider, activity, attempt, evidence or
   timestamps on limitation, GET, refresh or repeated limitation Continue.
3. Pause/resume reconstructs facts; receipts/replay remain byte-for-byte stable;
   no correctness/failure/mastery evidence or grading-guard changes.
4. Legacy GET strict parsing and real 204 creation, legacy no-activity 409
   denial, opt-in 200 variants, absent/unknown version, new-client/old-server,
   cache isolation and network/conflict recovery have focused API/UI tests.
   Legacy unfinished-presentation denial occurs before its completion write.
5. PostgreSQL integration proves completion atomicity, lock participation,
   attempt snapshot checks, duplicate Continue, Pause/Stop/submit races and no
   writes on rejection. Pure mocks alone cannot establish durability.
6. Cross-user mission/activity/source/attempt references, client-invented action
   or availability, CSRF and source revocation cannot bypass controls. Preserve
   sanitized errors and distinguish failure from validated limitation.
7. Preserve MCQ, submission, SEC-004 and reuse regressions, including ADR-0017
   origin and unbound-legacy fail-closed cases; do not rerun SEC-005
   absent a direct dependency. P7-14 covers the learner journey. User/CI evidence,
   external general review then independent security gate are required. Apply
   ASVS 5.0.0 and project threat lenses; unresolved High findings block release.

# Documentation Changes Required

The [P7-16 packet](../testing/p7-16-assessment-unavailable-continuation-proposal-2026-10-16.md)
specifies clause-level edits: Document 06 §§19,24,27.4/6/7; 07 §§8 F-05,19
F-16,24; 11 §§4.2,6–7,25,34–35,45; 15 §§31,49–50,61–62; 19 §§15,17,25,71,82;
20 §§84–87; 26 §16 and Phase 7 dependency table; 27 index/governance; tracker
new P7-16 and P7-14 dependency. Record ADR-0014/0015 continuation cross-references
without accepting their broader decisions. Preserve ADR-0012 in full. Document
12 and P7-08 generation need no change. Accepted ADR-0017 subsequently adds
the scoped origin prerequisite through Document 27's versioned amendment
process; it does not alter this ADR's educational continuation rule.

# Approval

- 2026-10-16 owner review: prior packet technically suitable subject to separate
  ADR and explicit API compatibility/transaction ownership. This is **not**
  formal approval of ADR-0016 or P7-16 implementation.
- 2026-10-16 subsequent product-owner approval, recorded in conversation:
  approved the P7-16 no-repeat policy for known-unassessable response-bearing
  activities; existing COMMUNICATE_LIMITATION and learner-controlled Pause;
  explicit application-owned assessment availability facts; the compatibility-safe
  `continuationContract=1` API proposal; the planned snapshot-checked completion
  operation within the existing mission-status application boundary; and
  registration of P7-16 and its dependency relationship with P7-14.
  This records product-owner approval of those items, not independent reviewer
  approval, formal ADR acceptance, implementation, medical grading qualification,
  security-risk acceptance or rollout authorization. The earlier suitability
  review above is retained as historical context.
- Product-owner approval of the enumerated items above is obtained. The distinct
  education-owner sign-off required by this proposal is not evidenced, nor is
  an explicit statement that the approving product owner holds that role.
  Confirm educational limitation/Pause/Finish and degraded legacy experience
  approval; do not invent role equivalence. No medical profile approval is requested.
- Architecture/API owner: approve opt-in/legacy plan, deployment/rollback and
  client-retirement constraints, lifecycle owner extension and snapshot contract.
- Security reviewer: approve fact/source/ownership derivation and required
  no-write/concurrency/compatibility verification scope; implementation review
  and independent security verification remain future gates.
- Decision/roadmap owner: formal acceptance, SOT/version changes, P7-16
  normative roadmap alignment, then explicit implementation authorization.
  Administrative registration/P7-14 dependency are separately authorized above.

## Independent review disposition, 2026-10-16

Architecture/API contract review was performed separately from proposal authorship.
It confirms suitability of the existing mission-status transaction foundation,
not an implemented completion capability or architecture-owner approval. The
presentation/null-metadata deadlock was corrected and independently rechecked.
Required version parsing and presentation admission tests are explicit above.
The independent security requirements reviewer rechecked the actual cardinality
clause and closed SEC-016-REQ-002 at documentation level only. The original
SECURITY FAIL — REMEDIATION REQUIRED verdict for SEC-016-REQ-001 is retained
as historical evidence; its requirements disposition is superseded below.

Subsequent independent architecture review approved section 3.1's ordering
design, followed by independent security requirements approval of the same
finite dependency locking, frozen semantics and rollback/error rules.
SEC-016-REQ-001 is requirements-resolved, not risk-accepted. The original
unlocked source reads remain unchanged in code; the completion operation is
unimplemented. Real PostgreSQL concurrency, authorization and idempotency
evidence remains mandatory before implementation completion/security approval.
This is not human architecture/API-owner acceptance, formal ADR acceptance
or implementation SECURITY PASS. Other approval/SOT gates above remain open.

Independent review evidence and remaining requirements are recorded in the
[governance review](../testing/p7-16-governance-review-2026-10-16.md).

### Earlier governance closure check, 2026-10-16 — historical

Existing product approval and independent technical review decisions were
consolidated without repeating reviews. No explicit architecture/API-owner
acceptance of the final opt-in transport, mixed-version plan and transaction
ownership, or education-owner identity/assignment and sign-off, was found in
the relevant approval records or supplied conversation. Both were requested
explicitly; pending replies are not approval. The product owner's identity or
technical reviewer role must not be silently substituted for either owner role.

The targeted mutation inventory remains covered by the approved source dependency
set; no escaping checked-in application revocation path was identified. This is
a static design-coverage check, not runtime transaction evidence. Both security
requirements have recorded design/documentation closure; no new review is claimed.

Formal acceptance and normative SOT version/count changes are deferred under
Document 27 sections 15 and 49. P7-16 remains registered Not Started with P7-14
depending on it; its stale source-ordering blocker note is synchronized to the
approved requirements resolution. The roadmap's approved count of 15 versus
16 registered tracker items remains explicitly pending governance alignment.
The existing PROPOSED ADR index entry is accurate and requires no status change.

### Owner replies and formal acceptance, 2026-10-16

The current project owner supplied direct replies to the outstanding questions:

- Architecture/API decision owner: the user explicitly identified themselves
  and approved, answering **"me, and approved"** to the question covering the
  final `continuationContract=1` transport, mixed-version compatibility and
  completion transaction ownership.
- Earlier education reply: **"approved"** expressed approval but did not
  explicitly establish the education decision-owner role. The earlier record
  inferred that assignment and prematurely treated acceptance as complete.
  That provenance error is superseded by the explicit declaration below; it
  must not be used as evidence that the earlier acceptance gate was satisfied.

The architecture/API reply supplies that role's approval; the earlier education
reply alone did not supersede the missing-role finding. The later explicit
declaration below closes that remaining gate.
Independent architecture/API and security requirements decisions are retained;
SEC-016-REQ-001 is requirements-resolved and SEC-016-REQ-002 documentation-closed.
The source mutation coverage check found no escaping checked-in application
revocation path; it is not runtime verification.

### Explicit education-owner declaration and corrected acceptance provenance

On 2026-10-16 the user stated in conversation:

> I am the education decision owner for Project Hippocampus P7-16. In that capacity, I approve ADR-0016's no-repeat assessment policy, assessment-limitation messaging, learner-controlled Pause and Finish experience, and temporary reduction in automatic free-text grading availability.

This explicitly identifies/assigns the approving education decision owner and
supplies actual role-specific sign-off. No product-owner or model recommendation
is substituted for it. The existing architecture/API-owner reply and separate
independent requirements reviews retain their original, limited provenance.

The owner's conditional governance authorization is satisfied only following
this declaration. ADR-0016's ACCEPTED status is now reaffirmed with corrected
provenance under Document 27 sections 15/49. The earlier acceptance and SOT
patches were premature; their unchanged scoped content now has the completed
owner approvals, rather than being represented as correctly authorized earlier.
The status is not backdated to imply that the missing role was established by
the first education reply. Earlier blocked/correction records remain historical.

P7-16 remains **Not Started**. A subsequent explicit implementation authorization
and approved execution packet are still required. No production implementation,
runtime test, rollout, routing, deployment, publication, medical qualification
or Phase 8 behavior is authorized by this acceptance.


## ADR-0018 scoped eligibility clarification ? 2026-10-19

Accepted ADR-0018 adds runtime authority and historical evidence eligibility without altering this record's original approval provenance. Safely graded means independently admitted under the current trusted contract, not a persisted grade label or model agreement. Preserve original attempts/receipts; current unqualified free-text history cannot drive competence, failure, prerequisite or graded follow-up. A historical ineligible evaluation is distinct from a real saved UNGRADED attempt. Preserve existing safety/source/time/stop/terminal precedence, immutable audit and independent qualification requirements. No new medical grading authority or schema is approved.
