# P7-16 governance alignment and independent requirements reviews

Date: 2026-10-16. **READY FOR IMPLEMENTATION AUTHORIZATION.** Documentation only.
Earlier blocked dispositions below are historical and are superseded by the
final owner-reply acceptance record at the end of this document.

## Approval and independence

The product owner approved in conversation the no-repeat policy, existing
COMMUNICATE_LIMITATION and learner-controlled Pause, explicit application-owned
availability facts, `continuationContract=1`, the planned snapshot-checked
operation in the mission-status application boundary, and administrative
P7-16 registration/P7-14 dependency. The exact approval is recorded in
[ADR-0016](../adr/ADR-0016-assessment-unavailable-mission-continuation.md).

Separate read-only agents performed architecture/API review, then security
requirements review. Neither authored the original proposal or changed files.
Their findings are technical review evidence, not human owner-role approvals,
medical qualification, implementation security verification or risk acceptance.
The coordinating agent recorded findings and corrected documentation only.

## Architecture/API review

Verdict: suitable design direction with unresolved approval/source-consistency
conditions. The mission-status boundary is suitable to extend, not already
capable of Engine-authorized completion.

Evidence:

- `ChangeStudyMissionStatusUseCase.java:40–46`: transactional authenticated
  owner and owned-mission lock.
- `SpringDataStudyMissionRepository.java:17–20`: PESSIMISTIC_WRITE.
- `MissionStateMachinePolicy.java:37–44`: ACTIVE permits COMPLETED.
- Existing response/presentation/materialization persistence operations lock
  the mission; this does not establish source-row consistency.
- `studyMissionContracts.ts:94` is strict; `useStudyMission.ts:113` confirms
  continuation without examining a result. Opt-in/legacy separation is necessary.

Corrected and independently rechecked: null continuation metadata must not
disable the existing explicit control for an unfinished owned presentation-only
activity. Unknown contracts and pending response activities cannot claim that
exception. Legacy unfinished presentations are denied before completion writes.

Compatibility recommendation remains backend-first opt-in support, unchanged
legacy GET/real-creation 204, write-free upgrade 409 for unrepresentable legacy
requests, strict opt-in 200 variants and isolated client query-cache entries.
No automatic mutation retry; refetch on conflicts or ambiguous outcomes.
Actual rollout/rollback/retirement approval remains separate.

## Independent security requirements review

Verdict: **SECURITY FAIL — REMEDIATION REQUIRED**, scoped to proposed requirements.
This is not a finding that changes the reviewed P7-09 containment verdict.
Baseline: applicable ASVS 5.0.0 requirements, OWASP Top 10:2025 A01/A06/A08,
API authorization/flow threats and project source/evidence integrity rules.
No broad OWASP compliance claim or runtime control verification is made.

### SEC-016-REQ-001 — MEDIUM — BLOCKING

**Historical finding; superseded by the requirements resolution below.**

Source-revocation/completion ordering is not defined; CWE-367.

`JdbcActivitySourceReferenceAuthorization.java:40–59` and
`AuthorizedStudyMissionSourcePresentationRepository.java:61–100` read source
authorization without source-row locks. `DeleteMaterial.java:26–35` and
`SpringDataMaterialRepository.java:20–33` change material authorization without
the mission lock. Source deletion may commit after authorization revalidation
and before completion commit. No cross-user disclosure was demonstrated.

Required architecture/security decision: specify whether authorization is valid
at a defined decision snapshot or must remain valid through completion commit.
For commit-time validity, identify actual source rows/writers, enforceable
ordering and deadlock handling within existing transaction boundaries. A mission
snapshot alone does not cover material deletion or chunk deactivation.
No option is silently selected and no Medium risk is accepted. Until resolved,
the completion design and formal ADR acceptance remain blocked.

Future required evidence: PostgreSQL interleavings for source revocation and
completion in both orders, failed authorization rollback/no-write behavior,
snapshot conflicts and deadlock handling if additional locks are approved.
Do not introduce source locks or another transaction mechanism speculatively.

### SEC-016-REQ-002 — LOW — CLOSED at documentation requirements level

Version-parameter cardinality was ambiguous. ADR now requires omission or exactly
one decoded `1`; empty, repeated (including identical), mixed and unsupported
values return sanitized 400 before writes/provider work. Runtime verification
is deferred to the future implementation tests. This does not itself establish
a grading bypass. The independent security reviewer rechecked the actual ADR
and closed this documentation finding; no runtime verification is claimed.

Other requirements supported by the design: application-owned availability and
Engine authority; ownership/frozen scope below transport; CSRF preservation;
immutable receipts; no false learning evidence; no-write limitation; no public
COMPLETE; private/no-store reads and cache isolation; sanitized failures and
explicit network/conflict recovery. These are requirements, not proven runtime
controls for unimplemented P7-16. No implementation SECURITY PASS is issued.

## Governance alignment disposition

- ADR-0016 and its index remain PROPOSED. ADR-0012 is unchanged; ADR-0014/0015
  remain PROPOSED. The owner approval does not supply the missing education-owner
  role sign-off, architecture/API-owner approval or security requirements closure.
- P7-16 is registered Not Started per the explicit owner instruction, with scope,
  prerequisites, tests, Definition of Done and blockers. P7-14 explicitly depends
  on it; neither task is implemented/completed by registration.
- Document 27 sections 15 and 49 require acceptance before normative SOT patches.
  Documents 06/07/11/15/19/20/26 are therefore not amended or version-bumped.
  The approved roadmap count remains 15; the tracker explicitly identifies the
  additional administrative registration as pending normative alignment. This
  is a disclosed staged governance state, not completed cross-document alignment.
- On acceptance, use the packet's clause-level amendment map, increment affected
  versions/add revision history, update Document 27 index/baseline records and
  roadmap count to 16. Then verify all contracts and authorize implementation
  planning. Do not broaden P7-15 or implement Phase 8.

Remaining approvals at the original review: education owner (or explicit evidence that the product
owner holds that role), architecture/API owner including source ordering,
security requirements closure, formal decision acceptance and versioned SOT
alignment. Administrative registration is obtained; implementation is not.

No production files, schema, dependencies, tests, services, provider calls,
student records, deployment, routing, commits, pushes or merges were used.

## Subsequent SEC-016-REQ-001 resolution, 2026-10-16

Current finding disposition: **RESOLVED at architecture/security requirements
level**, not risk-accepted or implemented. Separate independent architecture
review approved the ordering design, then independent security requirements
review approved that same rule. The earlier blocking verdict is historical.
See the [exact ordering resolution and future test matrix](p7-16-source-revocation-ordering-resolution-2026-10-16.md)
and ADR-0016 section 3.1.

Selected rule: effective READ COMMITTED, existing owned mission write lock,
individual ordered FOR SHARE NOWAIT locks on the exact finite source dependency
closure, fresh full locked revalidation and Engine authorization, held through
atomic completion commit. Preserve frozen semantics, existing source-free cases,
non-vacuous required-source validation and narrow rollback-before-409 behavior.
No source/attempt/receipt/evidence mutation or new revocation mechanism.

Current source code is unchanged. PostgreSQL concurrency tests remain future
implementation evidence, not a new prerequisite to this documentation-only
requirements resolution. No implementation SECURITY PASS is claimed.
Education-owner/role confirmation, architecture/API-owner acceptance of the
whole proposal, formal ADR acceptance, versioned SOT alignment and implementation
authorization remain open. ADR-0016 remains PROPOSED; P7-16 remains Not Started;
the overall governance state remains GOVERNANCE BLOCKED.

## Final governance closure audit, 2026-10-16

No completed architecture/API or security review was repeated. This audit
consolidates their recorded results and checks current owner provenance and
mutation coverage; it supplies no new owner/reviewer approval.

| Decision | Current provenance/disposition |
| --- | --- |
| Product policy, availability facts, opt-in API direction, transaction direction and registration | Explicit product-owner conversation approval, 2026-10-16, recorded in ADR-0016. |
| Architecture/API technical design | Separate independent review; presentation exception corrected/rechecked. Ordering design approved and written record rechecked. Not an architecture/API-owner sign-off. |
| Architecture/API-owner acceptance | No explicit owner acceptance of final opt-in transport, mixed-version compatibility and transaction ownership found in relevant records/conversation. Requested explicitly; unresolved until received. |
| Education decision owner | No named owner or explicit role assignment/sign-off found in relevant records/conversation. Requested explicitly; product approval does not establish education approval. |
| SEC-016-REQ-001 | Independently approved requirements resolution and recording recheck; no runtime verification. |
| SEC-016-REQ-002 | Independently rechecked documentation closure; runtime parsing tests remain future work. |
| Formal acceptance and SOT amendments | Blocked by missing owner decisions; Document 27 sections 15/49 still require acceptance first. |

### Revocation coverage check

Targeted source writes and their application call sites were inspected. No
escaping checked-in application revocation operation was identified:

| Mutation/call path | Approved dependency coverage |
| --- | --- |
| DeleteMaterial -> JpaMaterialRepository -> SpringDataMaterialRepository soft-delete UPDATE | Exact parent materials row FOR SHARE conflicts with deletion's UPDATE. No new revocation writer protocol is needed. |
| JdbcPdfExtractionPersistence page_count/root bounds finalization | Relevant material_versions and document_nodes rows are in the dependency set. No arbitrary replacement of an established page count is allowed by this writer. |
| JdbcMaterialReadinessRepository status derivation | Relevant version/material rows covered. Processing/readiness updates do not silently add a new frozen-authorization rule. |
| JdbcSourceReferenceRepository canonical upsert | Relevant source_references row covered; current update is display_label only, not identity/ownership transfer. |
| JdbcVisualContextRepository context filling | Relevant visual_assets row covered; current update changes caption/nearby text, not source identity. |
| Hypothetical referenced chunk deactivation/target deletion/ownership transfer | Positive dependency rows are protected, but no checked-in application mutation endpoint was found. Do not invent one. Existing FKs restrict referenced deletion and are not a non-key authorization lock. |
| FileSystemBinaryObjectStore.delete / UploadMaterial.compensate | The inspected business caller deletes a newly allocated random upload key after persistence failure; it is not an existing frozen-source revocation path. Temporary PDF cleanup is likewise not deletion of a persisted frozen source. No claim about external/private storage administration is made. |
| Active-version replacement or topic-link lifecycle | Not automatically revocation under existing frozen-source validation. Do not substitute current catalog/resolver predicates. No new lifecycle behavior approved. |
| Mission status/current activity/attempt/source-association changes | Existing owned mission lock and snapshot discipline govern these paths. The proposed completion must revalidate actual locked state; this audit does not prove its future implementation. |

Coverage is limited to checked-in application paths and the reviewed positive
authorization predicates. It does not cover future mutation paths, privileged
out-of-band maintenance or prove PostgreSQL runtime behavior. Future changes
to those predicates/writers require renewed dependency coverage review.

### Alignment disposition

ADR-0016 and index remain PROPOSED. ADR-0012 is unchanged; ADR-0014/0015 remain
PROPOSED. Documents 06/07/11/15/19/20/26 are not normatively patched or versioned
before missing approvals and formal acceptance. P7-16 remains Not Started; its
tracker notes/evidence are synchronized to requirements closure, and P7-14's
explicit dependency remains. The 15-item approved roadmap versus 16 registered
tracker items is a disclosed pending governance amendment, not completed
normative consistency. No P7-15 or Phase 8 scope change is made.

Owner approval requests remain unanswered in the available record; elapsed
time is not approval. Verdict: **GOVERNANCE BLOCKED** pending architecture/API-
owner acceptance and education-owner assignment/sign-off, then formal acceptance
and versioned SOT alignment. No production implementation is authorized.

### Documentation check evidence for final closure

The focused read-only consistency script completed with exit 0:

- ADR-0012 ACCEPTED; ADR-0014/0015/0016 PROPOSED.
- Exactly one ADR-0016 index entry, status PROPOSED.
- 16 registered Phase 7 entries, 16 unique identifiers; P7-16 Not Started.
- P7-14 explicitly depends on P7-16.
- Approved roadmap still states 15 items; pending count alignment is disclosed
  in tracker notes. This unresolved normative alignment is a governance finding,
  not a mechanical-check failure or a claim of completed synchronization.
- Eight local link targets checked across ADR-0016 and its three governance/
  resolution documents; zero broken targets or other mechanical failures.

`git diff --check` completed with exit 0 and no whitespace errors. It reported
four existing-working-copy LF-to-CRLF conversion warnings for
`CapturedCase3AttributionReplayTests.java`, `GoldenResponseAdapterLiveCollector.java`,
`GoldenResponseAdapterLiveCollectorTests.java` and
`GoldenResponseAdapterQualificationTests.java`. None of those files was changed
by this governance task; no line-ending normalization or tests were performed.
These warnings are not test failures or new security findings.

## Subsequent owner replies and formal governance closure, 2026-10-16

The architecture/API owner explicitly identified themselves as the responding
user and answered "me, and approved" to the final opt-in transport, mixed-version
compatibility and completion-transaction question. The same user answered
"approved" to the separate education-owner assignment/sign-off question covering
the no-repeat policy, limitation/Pause/Finish experience and reduced assessment
availability. This is a direct education-specific approval, not inference from
the earlier product approval; no personal name is invented. ADR-0016 records
the exact provenance. These replies supersede the missing-owner findings above.

All scoped decision gates are satisfied: existing product approval, explicit
role-specific owner replies, independent architecture/API design review and
security requirements closure/rechecks. No completed review was repeated and
no technical review is represented as implementation verification.

Under the owner's conditional authorization and Document 27 sections 15/49,
ADR-0016 is ACCEPTED and indexed accordingly. Versioned amendments were then
applied, with dated revision-history entries:

| Document | Version |
| --- | --- |
| 06 User Journey | 1.0.2 |
| 07 Features | 1.0.2 |
| 11 Learning Engine | 1.0.6 |
| 15 AI Evaluation | 1.0.6 |
| 19 Backend | 1.0.3 |
| 20 Frontend | 1.0.4 |
| 26 Roadmap | 1.0.4 |
| 27 Decision Log | 1.0.13 |

The packet's clause map is applied. Roadmap and tracker both record 16 Phase 7
items including the gate; P7-14 explicitly depends on P7-16. P7-16 remains Not
Started, with implementation/tests/DoD unchanged in intent and no completion
evidence invented. ADR-0012 is unchanged. ADR-0014/0015 remain PROPOSED with
only continuation cross-references to the accepted decision.

SEC-016-REQ-001 remains resolved at requirements level only; source mutation
coverage is the previously recorded static check, not a PostgreSQL result.
No security requirement is knowingly open within this decision scope. Runtime
authorization/concurrency/rollback/idempotency qualification, external general
review and independent implementation security review remain future gates.

Verdict: **READY FOR IMPLEMENTATION AUTHORIZATION**. The next required decision
is explicit authorization of a scoped execution packet. No implementation,
provider calls, schema changes, runtime tests, deployment, routing activation,
publication, tracker completion or Phase 8 behavior is authorized here.

### Post-acceptance documentation consistency evidence

Focused checks completed with exit 0: 15 documentation files, 29 amended SOT
clauses and 50 local link targets; zero mechanical failures or broken links.
All eight SOT versions, updated dates and revision entries match the table above.
ADR/index statuses agree: ADR-0012 ACCEPTED, ADR-0014/0015 PROPOSED, ADR-0016
ACCEPTED. Phase 7 has 16 unique registered identifiers, roadmap count 16,
P7-16 Not Started and the explicit P7-14 dependency. The former count mismatch
and missing-owner findings are resolved, not carried as current blockers.

`git diff --check` completed with exit 0 and no whitespace errors. The same four
LF-to-CRLF warnings listed in the earlier check remain in existing test files;
none was changed by this governance work. No implementation tests were run.

## Corrected education provenance before execution planning, 2026-10-16

The preliminary execution-plan check correctly rejected the earlier inference
of education-owner assignment from the single word "approved". The earlier
claim that every approval gate had already been satisfied was premature.

The user subsequently explicitly declared: "I am the education decision owner
for Project Hippocampus P7-16" and, in that capacity, approved ADR-0016's
no-repeat assessment policy, limitation messaging, learner-controlled Pause and
Finish experience and temporary reduction in automatic free-text grading
availability. ADR-0016 records the full declaration. This is actual separate
role-specific approval, not product approval or a model recommendation used as
a substitute. The current user is the identified owner; no personal name is inferred.

This declaration closes the provenance gap. Acceptance is reaffirmed only after
that explicit declaration; the existing scoped SOT content now has completed
owner authority. Earlier acceptance/amendment timing is not retrospectively
represented as valid. No architectural decision or completed review is repeated.
Architecture/API-owner approval remains the explicit "me, and approved" reply.
The separate requirements reviews remain design evidence only; no implementation
SECURITY PASS or medical qualification is inferred. P7-16 remains Not Started.

Execution planning can now proceed. Explicit implementation authorization,
user/CI evidence, external general review and independent implementation security
review are still required at their proper stages. This correction changes no
production code, normative policy, test, schema, routing or tracker status.
