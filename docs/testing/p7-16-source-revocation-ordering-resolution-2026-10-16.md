# P7-16 — SEC-016-REQ-001 source ordering resolution

Date: 2026-10-16. **SECURITY REQUIREMENT RESOLVED at design level.**
ADR-0016 is now ACCEPTED after the subsequent owner replies; P7-16 remains Not Started. No implementation,
runtime/concurrency test, medical qualification or rollout is authorized.

## 1. Root cause and actual paths

Paths below are relative to `backend/src/main/java/com/hippocampus/`.

| Existing path | Observed behavior and significance |
| --- | --- |
| `materials/api/MaterialController.java:90`; `materials/application/DeleteMaterial.java:26`; `materials/infrastructure/persistence/JpaMaterialRepository.java:46`; `SpringDataMaterialRepository.java:20` | Owned DELETE API invokes transactional soft deletion: update material status to DELETED, clear activeVersionId, update timestamp. Its normal PostgreSQL UPDATE locks the material row, not the mission row. This is the checked-in source revocation operation. Repeated delete is idempotent. |
| `learning/infrastructure/persistence/JdbcActivitySourceReferenceAuthorization.java:40` | Plain SELECT checks owner, non-DELETED material and frozen material/version/node mapping. It does not lock source rows or check all target eligibility predicates. It cannot alone supply comprehensive completion authorization. |
| `materials/infrastructure/persistence/AuthorizedStudyMissionSourcePresentationRepository.java:61` | Plain SELECT validates frozen material/version, owner, non-DELETED status, target existence, active referenced chunk, page bound and optional frozen document node. No active-version equality is required. These frozen semantics, not the generic current-version resolver, define the source-validation baseline here. |
| `learning/application/ChangeStudyMissionStatusUseCase.java:40`; `learning/infrastructure/persistence/SpringDataStudyMissionRepository.java:17` | Application transaction authenticates owner and obtains mission PESSIMISTIC_WRITE. This protects mission lifecycle/snapshots, not independently mutable source rows. The application-only Engine-completion operation is still proposed. |
| `learning/application/PersistActivityResponse.java:34`; `PersistPresentationCompletion.java:39`; `PersistMaterializedActivity.java:39` | Existing mission mutations share owned-mission locking. Keep their guards and transactions; do not create an attempt or activity to commit lifecycle completion. |

Counterexample to the old requirement, a logical schedule **not an executed test**:

1. Completion C locks its owned mission; plain source query observes material M
   non-DELETED and otherwise eligible.
2. Deletion D updates M to DELETED in its separate transaction and commits.
3. C saves COMPLETED and commits using the earlier source observation.

Both writes can commit because they lock different rows. Mission updatedAt,
activity/attempt snapshots and repeating an unlocked check do not close the last
check-to-commit gap. No cross-user disclosure or deployed P7-16 exploit is claimed.

## 2. Invalidation inventory and boundaries

| Change | Effect on existing frozen-source authorization |
| --- | --- |
| Owned material soft deletion | Actual revocation: non-DELETED predicate becomes false; every relevant frozen source under that material becomes unauthorized. |
| Referenced chunk set inactive, source retargeted, referenced target/version physically removed, or material ownership changed | Can invalidate positive authorization predicates. No checked-in application operation for chunk deactivation, source retargeting, source hard deletion or ownership transfer was found in the targeted persistence/API scan. Material owner mapping is non-updatable through its entity. Do not invent such APIs. Exact rows are nevertheless protected against ordinary conflicting SQL updates/deletes; privileged out-of-band maintenance is outside this application guarantee. |
| PDF page-count finalization | `JdbcPdfExtractionPersistence.java:76–89,120–154` sets previously null page_count or verifies the same count, then finalizes root bounds. It does not replace an established page count arbitrarily. A newly established page bound can invalidate an otherwise accepted reference, so lock its version and recheck the existing page predicate. |
| Existing source-reference upsert | `JdbcSourceReferenceRepository.java:185–194` updates display_label on canonical conflict, not reference identity. No medical authority or ownership is transferred. Protect reference rows used by the final check. |
| Readiness/processing status, visual caption/context filling, title or source-label changes | Not by themselves source revocation under existing frozen validation. Some touch rows protected by this design and may create brief contention. `JdbcMaterialReadinessRepository.java:17–31,110–119` already locks version then parent and preserves DELETED; `JdbcVisualContextRepository.java:27–41` fills context, not source identity. Do not add a new READY-only requirement. |
| Active-version replacement or topic-link change | New retrieval/materialization may use current active-version/link policy. Existing frozen-source validation deliberately does not require current active-version equality or a current topic link. Do not substitute `JdbcSourceReferenceRepository.resolveAuthorized()` or `JdbcStudyMissionSourceCatalog` for frozen validation. No replacement/retirement API is introduced or assumed here. |
| Mission Pause/Stop/new current activity or saved-attempt change | Mission/snapshot concurrency, not source revocation. Existing mission locking and completion snapshot checks govern it. Topic/subject lifecycle and authentication controls retain their existing authority; this decision does not redefine them. |

V18 source-reference foreign keys restrict deletion of referenced version/node/
chunk/visual rows. V20 retains frozen mission material/version/node references
and activity source references. These constraints protect provenance identities;
they do not prevent non-key status/is_active changes and do not replace access
authorization. No relevant `@Version` field/shared source revision was found in
mission/material/version entities. Ordinary timestamps are not a shared CAS.

## 3. Selected rule: validate under transaction-held dependency locks

**Completion may commit only if its existing source-authorization predicates are
true on the complete locked dependency set and remain protected until commit.**
Choose commit-time validity, not acceptance of an earlier preparation/read snapshot.
Once completion has all required locks and passes final validation, a conflicting
revocation cannot commit until completion ends. Deletion that already committed
is observed and denied. Operations that overlap may conflict rather than both
succeeding; learner-controlled read recovery is sufficient, without auto-retry.

This uses existing PostgreSQL/JDBC/Spring transaction capabilities. It adds no
transaction manager, advisory lock, table-wide lock, queue, event, schema or
dependency. It does not require changing the existing soft-delete writer:
its UPDATE automatically conflicts with the completion's material-row lock.

### Transaction owner, scope and order

1. Enter the proposed application-only operation through the injected
   `ChangeStudyMissionStatusUseCase` transactional bean. Require an actual
   effective READ COMMITTED transaction on the shared application database.
   Do not rely on self-invocation or silently join an incompatible outer
   transaction; establish/check that boundary in the implementation packet.
   No global isolation change or new transaction mechanism is proposed.
2. Authenticate owner, acquire existing owned mission write lock, then check
   ACTIVE, expected current activity, completed status/time, mission updatedAt
   and saved-attempt identities/counts needed by selection. No completion
   mutation or provider call has occurred.
3. From server-owned current activity/contracts and frozen mission scope,
   enumerate the exact finite authorization dependency set: current activity
   source references, frozen scope facts actually consulted by completion
   authorization/Engine, and their referenced targets. Include a consulted
   frozen scope's document node even if it differs from a source's other target.
   No client-selected IDs, unrelated historical sources, all-user materials,
   every chunk/visual in a version, or broader new readiness query is admitted.
4. Acquire individual, deduplicated row locks in this fixed table order, with
   ascending PostgreSQL UUID order within each table:

   `material_versions → materials → source_references → document_nodes → chunks → visual_assets`

   Use **FOR SHARE NOWAIT** for source dependencies. Lock each exact scoped
   identity explicitly, verifying ownership/frozen membership in the selection
   and again during final validation. Do not depend on a joined query planner's
   row acquisition order, aggregate/EXISTS locking, or lock a nullable outer-join
   side. Missing or unauthorized rows are rejection, not skipped locks.
5. Re-read locked source references/mappings and all dependency facts from the
   database, not earlier preparation objects or stale JPA-managed source values.
   Reconcile exact identity sets; changed mappings, missing dependencies or an
   unexpectedly broader decision dependency abort the whole operation. Do not
   enlarge/restart the lock set under partial locks or use SKIP LOCKED.
6. Run comprehensive existing frozen authorization predicates on those rows:
   authenticated owner, exact material/version/node scope, non-DELETED parent,
   correct version/target membership/existence, referenced chunk is_active and
   applicable page bound. Source-required activity cannot pass on an empty
   set or vacuous allMatch. Preserve only existing explicitly source-free
   contracts/modes: no source-free fallback when authorization fails.
7. Reconstruct local decision inputs and current time, require existing Engine
   COMPLETE, then save only the existing lifecycle completion fields/stage.
   Hold mission and dependency locks through the same atomic commit. No source
   mutation, attempt, artifact, activity, receipt rewrite or learning evidence.

Version-before-parent follows an existing ingestion pressure; the remainder is
a deterministic order for this operation, not a claim that every existing writer
has been globally reordered. Locking is infrastructure behavior behind a narrow
existing source-authorization persistence capability; the domain consumes facts.
Do not turn presentation projection into a transaction owner or weaken existing
submission/materialization/presentation persistence guards.

### Why this is the smallest sufficient boundary

Parent-only locks serialize soft deletion but miss page-count, referenced chunk
activity and target/mapping predicates. FOR KEY SHARE does not block non-key
updates. The selected locks cover exactly the positive rows already needed by
authorization, without a new shared revision or changes to every writer.
Optimistic timestamps cannot compare source revisions that do not exist;
adding such a revision requires schema/writer changes. Serializable isolation
across independently unchanged writers, snapshot-only authorization and advisory
locks were not selected as substitutes for the explicit row conflict.

### Contention, failure and deadlock handling

Source NOWAIT makes this operation fail rather than wait while holding a partial
source lock set. Keep locks shared; do not upgrade source locks or mutate source
rows inside completion. Relevant ingestion lock orders therefore do not require
a speculative global refactor. Ordinary conflicting source writers may wait
for a short completion transaction; no remote calls or learner interaction may
occur inside it. NOWAIT concerns row locks only, not table/DDL waits. This is
not a promise of universal deadlock freedom or bounded database response time.

Only SQLSTATE 55P03 attributable to this dependency acquisition becomes the
existing sanitized stale/conflict response, HTTP 409
`ACTIVITY_MATERIALIZATION_STALE_MISSION`, after the entire transaction rolls back.
Release all partial locks; no queries/writes on an aborted transaction and no
catch-and-continue/automatic retry. A changed dependency set also causes stale
conflict and rollback. Do not broadly convert database errors: deadlock 40P01,
serialization 40001, timeout, connection/permission and other infrastructure
failures retain existing sanitized infrastructure semantics, never NO_ACTIVITY
or a graded response. Future recovery must refetch before learner-initiated retry.

Unknown mission owner retains not-found behavior. Failed source authorization
uses existing sanitized invalid-source failure (currently 422
`ACTIVITY_MATERIALIZATION_INVALID_SOURCE_REFERENCES`), without foreign existence
or content disclosure. Do not return assessment limitation for an authorization
failure. Repeated terminal completion retains existing lifecycle conflict/read
recovery, with no second write/timestamp. Material delete idempotency is unchanged.

## 4. Observable ordering outcomes

| Schedule | Required outcome |
| --- | --- |
| Revocation committed before completion's final lock/check | Revalidation denies completion; mission/receipt/attempts unchanged. |
| Completion acquired its full dependency set and passed final checks first | Revocation UPDATE waits on its conflicting row; completion commits first, then revocation may commit. Later reads deny revoked source access. Existing completion/history is not undone. |
| Revocation holds a conflicting row while completion tries to acquire it | Source NOWAIT fails, completion rolls back, sanitized conflict; revocation can proceed. No automatic retry or invented activity/evidence. |
| Preparation observed eligibility but revocation commits before locks | Preparation carries no authorization authority; final locked validation rejects. If revocation is still uncommitted, NOWAIT conflicts instead. |
| Other eligibility/binding fact changes before lock-set completion | Final locked reread rejects stale mapping/invalid predicate; do not extend a partially acquired closure. A conflicting in-flight writer yields conflict. |
| Completion rolls back while revocation waits | All completion locks/writes roll back; revocation may proceed. No success response is issued for the failed completion. |
| Ownership, source validity, transaction membership or required dependency cannot be established | Fail closed with applicable existing authorization/conflict/infrastructure error; no NO_ACTIVITY success or evidence. |

No priority is promised merely because a request arrived first. Shared row locks
and final validation determine successful ordering. Revocation after completion
does not revoke the historical fact that an authorized lifecycle transition
occurred, nor does that history grant future source access or medical credit.

## 5. Proposed focused regression matrix — no execution in this task

Use the existing PostgreSQL integration facility, currently
`pgvector/pgvector:0.8.6-pg18` in `PostgresIntegrationTestSupport.java:20–25`.
Use distinct real database connections/transactions, deterministic barriers and
lock observations, bounded test waits/cleanup; do not infer interleavings from
thread sleeps or replace PostgreSQL with mocks/H2. Exercise the actual injected
application transaction, shared JPA/JDBC connection participation and HTTP error
mapping as well as direct SQL conflicts. These are future user/CI tests.

| Case | Required assertions |
| --- | --- |
| Unprotected two-transaction counterexample | Demonstrate logical race in an isolated test setup; do not weaken the production guard to reproduce it. |
| Delete commits first | Final authorization fails; completion fields/attempts/receipts unchanged. |
| Completion all-locks-first | Prove DELETE is blocked, completion commits, DELETE then commits; later source reads deny access and completed history remains intact. |
| Delete in flight first | Dependency acquisition produces 55P03, entire completion rollback precedes 409, deletion finishes; partial locks are released. |
| Revocation rollback / completion rollback | Each failed side leaves no partial mutation; the other side can subsequently proceed under fresh checks. |
| Eligibility changes during preparation | Final check catches committed deletion, stale mapping or invalid page predicate; prepared objects cannot authorize. |
| Non-key chunk is_active and version page_count update | A conflicting update cannot slip between final check and commit; reverse order either conflicts or final validation denies. Direct test SQL models predicate mutation, not an invented runtime API. |
| Changed/deleted target or source mapping | Exact-set validation fails or row lock blocks writer; respect actual FK RESTRICT errors without disabling constraints. Exercise stale preparation using permitted fixture setup, not impossible successful deletes. |
| Active-version replacement; ordinary readiness/title/context change | Valid frozen version remains eligible under existing predicates; contention may conflict but must not be reported as revocation or learner failure. No new active-version/topic-link/READY gate. |
| Multiple references/materials, opposite caller input order | Same deduplicated table/UUID order; no skipped rows. Source NOWAIT fails safely under contention and releases all partial locks. |
| Readiness version→parent and other ingestion contention | Demonstrate safe conflict/release, no completion source-lock upgrade/wait cycle; preserve failure semantics on actual deadlock/serialization/DB error. |
| Foreign owner/source, absent target, empty source-required set | Sanitized denial, no foreign disclosure, provider calls or rows/evidence; explicit existing source-free case remains distinct. |
| Concurrent Pause/Stop/submission/materialization; stale snapshot | Existing mission-lock discipline selects at most one valid mutation; completion cannot resurrect a changed mission/activity or overwrite attempts. |
| Duplicate completion, response replay and material DELETE replay | Stable terminal conflict and refetch; one lifecycle write at most; original submission receipt/idempotency and delete idempotency preserved. |
| Runtime exception, 55P03, 40P01, 40001, connection failure | Correct rollback and narrowly mapped conflict versus infrastructure errors; no catch-all success, NO_ACTIVITY or automatic retry. |
| Legacy/opt-in, pending presentation and SEC-004 regression | No contract-version bypass; admission precedes writes; no graded/failure/mastery evidence from unassessable free text. Do not rerun unrelated SEC-005 validation. |

Do not introduce a new hard source deletion/ownership-transfer endpoint to test
this design. Cover permitted transitions and database predicates with synthetic
owned fixtures; expose no student/source contents in reports. No pass counts or
performance/availability rates exist for the unimplemented ordering mechanism.

## 6. Independent review and remaining gates

Independent architecture requirements review: **APPROVED at design level**.
It confirmed the transaction owner and required frozen semantics, full positive
dependency coverage, effective transaction/isolation, fresh locked reads, precise
conflict translation and real PostgreSQL qualification.

Subsequent independent security requirements review: **APPROVED at requirements/
design level; SEC-016-REQ-001 requirements-resolved**, contingent on recording the
exact reviewed rules above. No additional blocking design correction was found.
Both independent reviewers subsequently re-read ADR-0016 section 3.1 and this
record and confirmed that the written rules accurately reflect their approvals;
no substantive discrepancies were found.
Scope: ASVS 5.0.0 authorization/data-integrity/error requirements, OWASP A01/A06/
A08 and API object/flow authorization, including CWE-367. No implementation
SECURITY PASS, human owner-role approval or risk acceptance is implied.

At the ordering review, ADR-0016 remained PROPOSED. Remaining governance then included education-owner sign-off
or confirmed role ownership, formal architecture/API-owner acceptance of the
whole proposal, formal ADR acceptance/versioned SOT alignment and an approved
implementation packet. The ordering requirement is resolved; its implementation
and PostgreSQL evidence remain future blockers to implementation completion.
P7-16 tracker status is unchanged; earlier tracker notes refer to the historical
finding, with this resolution providing the new decision evidence for later
governance alignment. No tracker edit is made in this task.

## PostgreSQL semantic references

PostgreSQL documents the row-lock conflicts, transaction-held locks and
[FOR SHARE versus FOR KEY SHARE](https://www.postgresql.org/docs/18/explicit-locking.html#LOCKING-ROWS).
[SELECT locking clauses](https://www.postgresql.org/docs/18/sql-select.html#SQL-FOR-UPDATE-SHARE)
define NOWAIT, including its row-lock-only limit. The effective isolation rule
uses [READ COMMITTED](https://www.postgresql.org/docs/18/transaction-iso.html#XACT-READ-COMMITTED).
These sources support the design; they are not observations of repository
runtime behavior or a substitute for the proposed integration evidence.


Subsequent governance closure, 2026-10-16: explicit architecture/API-owner and
education-specific replies are recorded in ADR-0016, which is now ACCEPTED.
Versioned SOT and roadmap/tracker alignment are complete. The requirements-level
ordering approval is unchanged; no runtime evidence or production authorization
is supplied by governance acceptance. P7-16 remains Not Started pending separate
implementation authorization and an approved execution packet.
