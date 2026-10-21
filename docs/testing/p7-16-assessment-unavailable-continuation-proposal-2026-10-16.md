# P7-16 accepted governance packet — Assessment-unavailable mission continuation

Date: 2026-10-16 (Asia/Manila)

**ADR-0016 ACCEPTED — READY FOR IMPLEMENTATION AUTHORIZATION.** Documentation only.
P7-16 is administratively registered at Not Started under the owner's
2026-10-16 instruction. Implementation and rollout remain unauthorized.
ADR-0014/0015 remain PROPOSED. Separate [ADR-0016](../adr/ADR-0016-assessment-unavailable-mission-continuation.md)
is ACCEPTED and its scoped Source-of-Truth amendments are applied. P7-16 remains
Not Started and P7-14 depends on it. The reviewed SEC-004 grading guards are
outside this documentation change.

## 1. Verified scope and proposed owner decision

At original proposal time the tracker/roadmap contained 15 Phase 7 items.
Following explicit owner-role replies and formal ADR acceptance, both now contain
16 including the gate. P7-16 remains Not Started; only governance is complete.
P7-15 is the accepted ADR-0013
**graded-response** follow-up lifecycle and explicitly excludes UNGRADED.
P7-14 depends on all P7 implementation items, explicitly P7-15, and validated
P7-09 Golden checks. The containment's 104/104 focused results and scoped
reviews do not satisfy those Golden/medical or milestone requirements.

Accepted decision with explicit owner-role approval recorded in ADR-0016:

> After a real saved UNGRADED attempt, when application-owned facts establish
> that a relevant proposed free-text activity has no approved grading basis,
> the Learning Engine must not select another response-bearing activity known
> to remain unassessable under containment. Preserve existing higher-priority
> lifecycle, safety, source, time and anti-repetition decisions. Where those
> decisions do not already determine a safe alternative or terminal action,
> select the existing COMMUNICATE_LIMITATION action with the assessment-specific
> rationale ASSESSMENT_UNAVAILABLE. Preserve the saved response, completed
> activity, stage and authorized frozen scope, and offer learner-controlled
> Pause. Do not interpret uncertainty as learner failure or objective success.

`ASSESSMENT_UNAVAILABLE` is a proposed rationale code, not another Engine
action, mission status, correctness level or evidence type. No new source,
assessment profile, medical approval mechanism or grading exception is created.
Elapsed time alone does not restore a verification basis. The policy does not
switch to MCQ, regenerate a different unassessable free-text question, or
introduce formative-only questions as a recovery mechanism.

### Acceptance criteria

1. Generic UNGRADED alone cannot trigger this policy. A valid saved attempt,
   current owned activity/contract, relevant target and server-known absence
   of a verification basis must be established independently.
2. Block both reuse and new generation when the proposed response-bearing
   target is known unassessable. A new LearningActivity ID, VALIDATED artifact,
   approved provider route, model label or changed question does not restore
   assessment eligibility.
3. Preserve existing legitimate lifecycle/source/safety/time/anti-repetition
   outcomes. Do not replace their rationales with an assessment limitation or
   use an assessment limitation to manufacture a source failure.
4. Do not block a presentation-only support/reflection action selected by an
   existing applicable policy merely because past history contains UNGRADED.
   Do not invent a new support/selection sequence.
5. The limitation branch makes no provider call and creates no new artifact,
   LearningActivity, StudentAttempt, grade, misconception or mastery evidence.
6. Repeated limitation checks are non-mutating. Pause is explicit; resume
   revalidates current facts and never silently restores assessment.
7. Immutable submission receipts and exact replay remain unchanged. Current
   continuation metadata is separate and takes precedence for available UI
   actions, without altering what the original receipt confirms.
8. Client and server distinguish an accepted limitation outcome from an
   infrastructure error, stale conflict and successful materialization.

## 2. Facts, classifications and decision ownership

| Situation | Evidence and treatment |
| --- | --- |
| Known assessment unavailable | Server-known absence of grading basis for the relevant owned free-text target under the current containment. The Engine receives this explicit fact; it must not infer it from medical wording or model output. |
| Response-specific UNCERTAIN | An uncertain answer is not proof that its activity is permanently ineligible. When unavailability is not independently known, preserve existing uncertainty handling. This packet does not enable any approved runtime grading basis. |
| Source insufficiency | Existing source policy and authorization determine it. Preserve SOURCE_UNAVAILABLE/SOURCE_LIMITED/visual rationales where applicable; do not claim that new sources repair missing grading authority. |
| Infrastructure/invalid-state failure | Failed ownership/contract/source loading, malformed result, persistence failure or conflict retains the existing error contract. Do not convert it into a successful assessment-limitation response or fabricate an attempt. |

Today all free-text contracts reaching the contained grading path lack an
approved runtime basis. A small application-owned classification can establish
that fact from the owned contract and current containment policy. It must also
identify that the relevant latest attempt was actually saved as UNGRADED.
Do not use only `deterministicResult == null`, a client flag, model annotation,
generic limitation code or artifact validation status as the authority.

The minimal domain addition is explicit input to the existing LearningState
for known assessment unavailability of the relevant response-bearing target;
absence/unknown is distinct from known unavailable. It is not a runtime
grading-enable switch or an eligibility catalog. Application code resolves
owned contracts; domain policy consumes typed facts without repositories or AI.
For RETRY the target is the current artifact; for REUSE_VALIDATED_CONTENT it is
the Engine-selected owned activity. Under blanket containment the application
can also identify the existing free-text generation families as unavailable,
without executing generation. Presentation-only and deterministic contracts
remain distinct. If the implementation cannot resolve a proposed target with
existing contracts, stop for a scoped decision rather than granting eligibility.

Compute the ordinary Engine decision using existing precedence. The new rule
prevents an otherwise selected, known-unassessable response-bearing action;
it does not replace terminal/lifecycle actions, valid source limitations,
time-selected reflection/completion, or existing non-assessment support.
Run/retain the applicable existing policy protections; do not insert a late
RETRY after they have selected a safer alternative. The Engine owns the final
action. The application dispatches it, never substitutes its own pedagogy.

No schema change is proposed. The current blanket policy is reproducibly
derived on refresh/resume. Restoration of grading authority or selective
family eligibility remains ADR-0014/0015 work with its own approval gates;
P7-16 cannot implement it. Add regression coupling so changing containment
cannot silently leave the continuation classification inconsistent.

## 3. Minimal Continue and mission-read contract

Current Continue always invokes materialization, its result requires an
activity UUID, and its controller returns HTTP 204. Non-materializable actions
are rejected by MaterializeLearningActivityUseCase. The client treats success
as confirmed continuation, and MissionInteraction currently enables Continue
for any completed activity even when its receipt flag is false.

Propose two mutually exclusive **transport outcomes**, not Engine actions,
for the existing POST endpoint:

```json
{"outcome":"ACTIVITY_CREATED","activityId":"<new-current-activity-uuid>"}
```

```json
{"outcome":"NO_ACTIVITY","actionType":"COMMUNICATE_LIMITATION","rationaleCode":"ASSESSMENT_UNAVAILABLE"}
```

- Return HTTP 200 for either successfully processed outcome. Validate variants
  explicitly: ACTIVITY_CREATED requires one new activity ID; NO_ACTIVITY has
  no activity ID and carries only an applicable existing non-materializable
  action and safe rationale. Do not expose model/provider/profile/source text.
- Keep the route, authentication, CSRF, owned-current-activity admission and
  stale/lifecycle errors. The request contains no client-selected action,
  assessment availability or grade. Domain failures remain existing sanitized
  error responses, not NO_ACTIVITY.
- A limitation return must not bypass the applicable existing owner, current
  activity and source-scope authorization checks merely because it avoids the
  materializer. Derive facts only from authorized server state; a failed source
  authorization remains an error rather than an assessment rationale.
- Change the use-case Result to the same two alternatives. Call the
  materializer only for an action it can actually materialize. No dummy UUID,
  empty generated question, fabricated activity or exception-as-success.
- The mission GET adds a nullable, dynamically computed `continuation` view:
  `{actionType, rationaleCode, canMaterialize}`. Null means no continuation
  decision is currently available, such as a pending response or a paused/
  terminal mission. This metadata is computed from current authorized state,
  never persisted into the original submission receipt, and makes no provider
  calls or writes. `canMaterialize` describes an action, not medical correctness.
- Compute a read decision only for an appropriate ACTIVE completed activity.
  Do not feed a paused/terminal mission into the current assembler's ACTIVE
  reconstruction and thereby bypass lifecycle. Existing Pause/Resume controls
  follow actual mission status. Re-fetch/revalidate after every mutation.

### Accepted compatibility contract, implementation and validation outstanding

The new POST payloads and GET continuation field above apply only with explicit
query opt-in `continuationContract=1` on both existing routes. ADR-0016 section 2
controls the complete compatibility plan. Without opt-in, retain the exact
legacy GET shape and 204 only after actual activity creation. Return sanitized
409 CONTINUATION_CONTRACT_UPGRADE_REQUIRED for valid non-materializable legacy
requests before any write/provider call. Unknown explicit versions return 400.
Neither omission nor client tampering bypasses Engine or grading controls.

Legacy unfinished presentations require opt-in before the existing presentation
completion write: return upgrade 409 without mutation. Only already-completed
activities use legacy dispatch. Updated clients retain explicit presentation
completion. Test this admission ordering rather than writing then denying.

New clients require opt-in metadata/result variants, reject missing fields/204,
use distinct query cache keys, and only latch creation for ACTIVITY_CREATED. Reject empty, repeated (even identical), mixed or unsupported explicit contract values with 400 before writes; omission alone selects legacy.
Pause/recovery stays available on read failure. Old cached clients follow their
existing 409 refetch without automatically resubmitting; they may still show a
generic conflict and Continue button. They cannot display new limitation copy
without reload. Repeated requests remain safe write-free denials.

Null decision metadata disables response-bearing continuation, not the existing
explicit completion control for a current owned unfinished presentation-only
activity. Identify that exception from its current contract/status; opt-in POST
performs existing presentation completion then Engine dispatch. GET neither
simulates completion nor writes. Unknown contracts cannot use the exception.

Plan compatibility backend first, mixed-version tests, then opt-in frontend.
No activation is authorized. Keep legacy safe denial until explicit retirement
approval; do not assume a cache lifetime. New-client/old-server fails closed on
missing metadata. Do not roll back to a backend missing guards. Verify owned
reads remain private/no-store and isolate contract caches. Refetch after actual
409 conflicts or ambiguous network results before learner-controlled retry;
never automatically retry the mutation. No shared Accept-header change needed.

Retaining only HTTP 204 would leave the caller unable to distinguish activity
creation from a limitation until a second request succeeds. The small explicit
result prevents that false confirmation; GET still supplies recovery after
refresh. `canMaterialize=false` is not a blanket ban on invoking the endpoint:
map the existing action explicitly. COMMUNICATE_LIMITATION offers Pause rather
than another question; COMPLETE offers a learner-visible Finish session control
that invokes the same Continue endpoint to persist the existing Engine decision;
SOURCE_ONLY uses the authorized source view; RETRY_DEPENDENCY permits explicit
Check again without an automatic loop. These are controls for existing actions,
not new pedagogical actions or automatic mission transitions.

## 4. Backend/API/frontend behavior matrix

| Situation | Backend and API | Client and persistence |
| --- | --- | --- |
| Known-unavailable saved UNGRADED; final action COMMUNICATE_LIMITATION | HTTP 200 NO_ACTIVITY / ASSESSMENT_UNAVAILABLE; skip materializer and provider. | Show assessment-specific limitation, keep saved response visible and Pause available; no new rows or evidence. |
| Same Continue repeated, unchanged context | Recompute and return the limitation again without writes. | Do not latch a false activity-created state or auto-retry the request. |
| Time or source context changes between requests | Recompute existing policies; the result may legitimately differ. | Refetch authoritative current state; a prior limitation is not a permanent cached lock. |
| Existing policy selects legitimate materializable support/reflection | Materialize normally and return ACTIVITY_CREATED only after persistence. | Refetch the new current activity; do not count presentation as demonstrated knowledge. |
| Source policy selects SOURCE_ONLY or COMMUNICATE_LIMITATION | NO_ACTIVITY with the original source rationale; no materializer. | Explain source issue and use existing authorized source presentation where available; retain Pause. |
| Existing RETRY_DEPENDENCY action | NO_ACTIVITY with its existing rationale. | Present transparent dependency recovery; no automatic request loop or fake saved response. |
| Existing time policy selects COMPLETE while mission is ACTIVE | On explicit Continue/Finish, persist the already-selected completion in a short owned snapshot-checked transaction; NO_ACTIVITY / COMPLETE; no new attempt or evidence. | Offer Finish session via the existing endpoint, then refetch terminal mission. Completion is not mastery. Do not auto-complete on GET or merely because assessment is unavailable. |
| Explicit Pause, then Resume | Use existing status endpoints; preserve activity/stage/frozen scope. On resume read/re-evaluate before offering another activity. | No automatic Pause, Stop, Continue or generation. If basis is still absent, show limitation again. |
| Continue after pause, stop or completion; stale current activity | Preserve existing lifecycle/stale error semantics rather than rerun materialization. | Existing conflict handling/refetch; no duplicate progress. |
| Exact response POST replay | Return the original submission receipt without new evaluation. | Retain the receipt unchanged; current continuation view governs current buttons. |
| Ownership, source authorization, malformed state or infrastructure failure | Existing sanitized failure; no fake NO_ACTIVITY success. | Show failure/reload/conflict as applicable; no fabricated confirmation. |

### Confirmed completion transaction owner

Use existing `ChangeStudyMissionStatusUseCase`: it authenticates ownership,
locks the mission through `findOwnedByIdForUpdate` (PESSIMISTIC_WRITE), and saves
inside its application transaction. MissionStateMachinePolicy already permits
ACTIVE -> COMPLETED. Its public commands are only Pause/Resume/Stop; an
Engine-completion operation does not exist today.

Extend that owner with an application-only transactional operation invoked
through the injected bean from Continue. Do not add public COMPLETE, use
self-invocation for transaction entry, or introduce a new transaction mechanism.
Under the lock, recheck ACTIVE, current activity ID, mission updatedAt, activity
COMPLETED status/timestamp and saved-attempt snapshot facts. Reconstruct and
revalidate authorized local Engine inputs/current clock and require COMPLETE.
Keep Engine/assembly responsibility separate from lifecycle persistence. A
client action or old GET decision is not authority; no provider under lock.
Stop if necessary source/context revalidation cannot be safely local. Verify
all relevant concurrent writers participate in mission locking.

Commit only completion status/timestamps and the existing Engine-required stage;
preserve receipt, attempts, current activity and frozen scope. No evidence or
new rows. Duplicate terminal requests keep conflict/refetch semantics, without
another timestamp write. Snapshot changes and Pause/Stop races conflict.
PersistActivityResponse appends an attempt, PersistPresentationCompletion only
completes unfinished presentations, and PersistMaterializedActivity creates an
activity: do not reuse them by weakening their guards. ADR-0016 section 3 gives
the exact proposed extension. Atomicity/durability remain later integration
verification requirements, not established by annotations alone.

### SEC-016-REQ-001 ordering resolution, 2026-10-16

The [independently reviewed ordering design](p7-16-source-revocation-ordering-resolution-2026-10-16.md)
and ADR-0016 section 3.1 resolve source ordering at requirements level. Require
effective READ COMMITTED, the existing owned mission write lock, then individual
FOR SHARE NOWAIT locks on the exact server-owned source dependency set in order:
material_versions, materials, source_references, document_nodes, chunks,
visual_assets (ascending PostgreSQL UUID order within each table). Freshly
revalidate all locked facts and exact mappings, require Engine COMPLETE and hold
locks through commit. No source mutation, provider call or source lock upgrade
in completion.

Use frozen authorization semantics, not active-version/topic-link or new READY
requirements. Include consulted frozen scope nodes and every nonnull referenced
target; never lock all version contents or unrelated history. Missing, changed
or unexpectedly broader dependencies abort; no expansion under partial locks.
Only targeted acquisition 55P03 becomes stale 409 after full rollback; source
authorization and unrelated infrastructure failures retain their error semantics.
Source-required empty sets cannot pass; existing explicit source-free cases remain.
No source/attempt/receipt/evidence changes, auto-retry or global deadlock-free claim.
Real PostgreSQL interleaving tests in the linked matrix are required later.

## 5. Source-of-Truth and ADR alignment plan

The clause-level amendments below were applied after formal ADR-0016 acceptance,
with version increments/revision entries under Document 27. This packet remains
subordinate to the amended Source-of-Truth documents.

| Authority clause | Proposed normative addition |
| --- | --- |
| Document 06 §19 Stage 9; §24 Non-Repetition Rule; §§27.4, 27.6, 27.7 | A saved but unassessable response has a clear limitation/learner-Pause next state; avoid repeat pressure. Assessment limitation is not learner difficulty. Resume preserves work and rechecks availability. |
| Document 07 §8 F-05 Guided Learning Player; §19 F-16 Core Behavior/Resume Rules; §24 Feature-Level Failure States | Separate current continuation availability from saved feedback. Do not automatically repeat a known-unassessable response-bearing item. Distinguish assessment, source and infrastructure limitations in learner-visible behavior. |
| Document 11 §4.2 Mission Context; §§6–7 hierarchy/precedence; §25 ADR-0012 disposition; §§34–35 resume/fallback; §45 tests | Add explicit application-owned availability facts, Engine-owned selection of existing limitation action, protected precedence, no-failure-evidence rule and revalidation on resume. Add the acceptance scenarios in this packet. |
| Document 15 §31 Response Evaluation Quality; §49 Mission Flow Evaluation; §50 Timer Evaluation; §§61–62 Failure Taxonomy/Attribution | Qualify repeat-UNGRADED continuation separately from grade correctness. Measure no-write repeated checks, altered time/source decisions, no false evidence and honest limitation recovery. Offline containment is not medical grading qualification. |
| Document 19 §15 transaction boundaries (Submit/Continue presentation subsections); §17 Submit Response Flow; §25 AI Failure Taxonomy; §71 API Versioning; §82 AI Failure Integration | Document two Continue results, current-read continuation metadata, dispatch of non-materializable actions, unchanged receipts, lifecycle-owner completion extension, opt-in/legacy compatibility and rollout constraints, and error/success distinctions. Phase 8 evidence writes remain deferred. |
| Document 20 §84 API Type Generation; §85 Loading/Error State Matrix; §86 UI State Machine; §87 Study Mission Sequence | Distinguish saved response, activity creation, limitation and failure. Drive buttons from current decision/status; remove unconditional completed-activity Continue eligibility; refresh after writes and recover safely after failed reads. No new backend mission status is implied. |
| Document 26 §16 Phase 7 | Add P7-16 as a separately owned Phase 7 continuation task, change Phase 7 item count 15→16 including the gate, and make it explicit in P7-14 dependencies. Preserve Phase 8 boundaries. |
| Document 27 governance/index | Index proposed ADR-0016 and its distinct approval provenance; synchronize document versions/index summaries as required. Do not change either ADR's status by implication. |

ADR-0014: resolve **Repeated UNGRADED and student experience**, **Four distinct
runtime situations**, and the product/Learning Engine continuation sign-off
with the exact rule, contract and limits above. Record the new approval scope
separately from grading-integrity/containment approvals; those approvals did
not approve this continuation policy.

ADR-0015: align **Permanent ineligibility and continuation**, **Consequences
and ownership**, and its Learning Engine approval gate to reference P7-16.
Keep bounded generation, profile approval/durability and eligibility lifecycle
separate. Neither ADR becomes ACCEPTED through this packet. Accepted ADR-0012
already permits safe limitation/support/pause and excludes UNGRADED evidence;
no weakening or historical rewrite is proposed. P7-15/ADR-0013 remain limited
to safely graded follow-up. No prompt/generation change is needed in Document
12, and no P7-08 medical/generation contract change is authorized here.

### Decision authority before implementation

Document 27 sequencing is satisfied: ADR-0016 was accepted after explicit
product/architecture/API/education decisions and independent requirements review;
then Documents 06/07/11/15/19/20/26/27 were version-aligned. ADR-0012 remains
unchanged and ADR-0014/0015 remain PROPOSED. P7-16 is registered Not Started;
execution still requires separate explicit authorization and an approved packet.

## 6. Proposed tracker entry / implementation packet

TASK: **P7-16 — Handle known assessment-unavailable mission continuation**.
Administrative registration and P7-14 dependency were explicitly approved by the owner on 2026-10-16 and are now recorded at Not Started. ADR acceptance and SOT alignment are complete; execution remains prohibited until separate explicit authorization and an approved implementation packet. No implementation/completion evidence is implied.

Workstream: Learning Engine / Application / API / Frontend. Priority: Must
for any Phase 7 student-facing rollout using containment. Goal: preserve saved
learning activity without recurring unassessable assessment or false progress.

Dependencies: the applicable approved P6 policy contracts, including P6-01/02,
P6-07/08/09/10/11; P7-04/05/06/09/13 contract surfaces; approved scoped
continuation decision and aligned Source-of-Truth clauses. Do not imply their
currently incomplete task/release gates have passed. P7-16 does not depend on
P7-14; P7-14 explicitly depends on P7-16. P7-15 remains a separate sibling.

IMPLEMENT, only after approval:

1. Add the explicit owned availability fact and saved-UNGRADED context to the
   existing state assembly and Engine input; implement the narrow selection
   rule with existing actions and precedence. Share the same pure decision
   logic between current-read projection and Continue; no side effects on GET.
2. Dispatch materializable/non-materializable actions explicitly. Preserve
   existing materialization/reuse protections and implement only the existing
   completion transition where Continue lacks its dispatcher.
3. Revise Continue/controller and mission-read DTOs with the minimal outcomes
   above; keep receipt storage and submission idempotency semantics unchanged.
4. Update strict client schemas/API parsing, hook state, interaction buttons,
   limitation copy and Pause/resume/refetch behavior together.
5. Add the focused regressions below, document factual evidence, obtain
   external general and independent security review, and exercise P7-14 before
   any rollout decision. Do not change the reviewed grading guards.

EXPECTED FILES (existing boundaries, not permission to refactor adjacent code):

Backend paths below are relative to `backend/src/main/java/com/hippocampus/`.

- `learning/domain/LearningState.java`, `LearningEngine.java`, and rationale
  codes; `learning/application/StudyMissionLearningStateAssembler.java`.
- `ContinueStudyMissionUseCase.java`, `GetStudyMissionUseCase.java`; only
  necessary input plumbing at submission, without changing grading/receipts.
- `ChangeStudyMissionStatusUseCase.java` for application-only completion;
  existing continuation/current-mission controllers.
- `learning/api/StudyMissionContinuationController.java` and
  `StudyMissionReadController.java` for their coordinated transport contract.
- `frontend/src/features/study-mission/api/studyMissionContracts.ts`,
  `studyMissionApi.ts`, `hooks/useStudyMission.ts`,
  `components/MissionInteraction.tsx`, `pages/StudyMissionPage.tsx`.
- Directly corresponding tests and only the approved documentation patches.

DO NOT: alter grading guards, MCQ selection/key semantics, frozen-source
authorization, P7-08 prompts/generation, profiles, providers/routing, schemas,
original receipts or historical grades; add a review queue, new Engine action,
mandatory structured answers, unscored-practice mode or Phase 8 evidence work.

DEFINITION OF DONE: approved decision/SOT alignment and explicit tracker scope;
all acceptance criteria demonstrated; no known-unassessable automatic repeat;
correct no-activity/current-read/refresh/Pause/resume/conflict behavior; no new
false evidence; required user/CI validation and external general/security
review recorded; no unresolved blocking findings; P7-14 dependency and scenarios
updated. Implementation alone does not make P7-16 or P7-14 Done or authorize
rollout. Normal publication/merge/completion evidence remains required.

## 7. Regression and security matrix

| Scenario | Required result / level |
| --- | --- |
| Saved UNGRADED + server-known unavailable current free-text RETRY; available time and sources | Engine COMMUNICATE_LIMITATION; no provider/materialization/attempt; domain + application. |
| Known-unavailable new free-text action or selected reused artifact | Same protection; no regeneration or VALIDATED-only exemption; domain + application. |
| UNGRADED but availability not independently known; hypothetical response-specific uncertainty | Existing policy retained; generic status cannot classify permanent ineligibility; pure input tests do not activate runtime grading. |
| No saved attempt, pending response, MCQ or presentation-only target | Do not misclassify it as the post-UNGRADED case or grant new grading authority. |
| Source loss, explicit stop/pause, time-selected reflection/completion, anti-repetition, genuine prior graded failures | Preserve existing precedence, rationale and legitimate support behavior; no added learner-failure count. |
| Limitation Continue repeated; repeated refresh | No new rows, timestamps, evidence or provider calls; unchanged receipt; current decision can vary only with actual context. |
| ACTIVITY_CREATED vs NO_ACTIVITY response | Mutually exclusive validated payloads; no dummy UUID; controller/API parser/hook/UI distinguish them. |
| Old receipt says continuationAvailable=true; completed activity; current decision says no activity | Limitation wins; no unconditional Continue, grading prompt or false saved/progress announcement. |
| Pause/resume/restart under unchanged containment | Existing state/frozen versions preserved, same limitation reconstructed without regeneration. |
| Time expires after submission but before Continue | Respect new Engine decision; snapshot-checked existing completion or normal materialization as applicable; no NOT_MATERIALIZABLE loop. |
| Two tabs / duplicate materialization / pause or stop racing Continue | One state transition/activity at most; stale conflict/refetch; no resurrected mission or overwritten receipt. |
| User B mission/activity/attempt/reuse/source IDs; altered client availability/action | Deny with existing ownership policy; zero foreign information or provider calls; application + API authorization negatives. |
| DB/resolver/provider failure; malformed DTO; network failure after accepted command | Existing sanitized errors and recovery; no fake attempt, synthetic success or unsafe duplicate retry. |
| MCQ and 104-case containment regression | Preserve existing deterministic behavior and all free-text neutral UNGRADED invariants; no SEC-005 rerun absent a direct dependency. |

Add legacy strict GET/204 creation, legacy no-activity 409 denial, opt-in
GET/200 variants, unknown version, new-client/old-server, contract cache
isolation and network/conflict recovery cases. Add legacy unfinished-presentation denial before completion. Add lifecycle-owner completion
snapshot, duplicate terminal request and Pause/Stop/submit/materialization races.

Use pure domain/application tests with fake ports for deterministic behavior;
controller/security tests for actual ownership/CSRF contracts; PostgreSQL
integration for locking/atomicity/no-write durability and races; frontend tests
for strict parsing and UI transitions; targeted P7-14 E2E for the full mission.
Do not substitute an in-memory test for database behavior or call synthetic
test labels medical qualification.

AGENT TEST AUTHORIZATION for a later approved packet: only newly created or
modified cheap tests, exact method first/class-file when needed; no Docker,
Testcontainers, external infrastructure or application/browser startup. This
proposal authorizes no test execution. Use `hippocampus-implement-task`, plus
testing/security or frontend engineering guidance where the actual changes
require it. External review remains independent.

USER/CI VALIDATION, proposed commands after implementation (repository root
unless workdir shown; do not execute while planning):

```powershell
# backend workdir: focused pure/application/API classes, no provider requests
mvn -o -q "-Dtest=LearningEngineTests,LearningEngineScenarioTests,ContinueStudyMissionUseCaseTests,ChangeStudyMissionStatusUseCaseTests,StudyMissionContinuationControllerWebTests,GetStudyMissionUseCaseTests,SubmitActivityResponseUseCaseTests,StudyMissionLearningStateAssemblerTests,MaterializeLearningActivityUseCaseTests,AiResponseEvaluationAdapterTests,CapturedCase3AttributionReplayTests,GoldenResponseAdapterQualificationTests,ResponseAttributionValidatorTests" test

# backend workdir: user/CI-owned PostgreSQL/container evidence
mvn -o -q "-Dtest=StudyMissionPersistenceIntegrationTests,ActivityMaterializationPersistenceIntegrationTests" test

# frontend workdir: existing affected frontend suites; add any new dedicated test file
npm test -- src/features/study-mission/api/studyMissionApi.test.ts src/features/study-mission/pages/StudyMissionPage.test.tsx

# repository root: existing user/CI-owned broad gate after focused validation
node scripts/validation/validate.mjs all
git diff --check
```

P7-14 also needs the updated continuation E2E/source/Golden gates. Its exact
new E2E file/command must be fixed in the gate packet after the test is named;
this packet neither invents an existing executable nor authorizes live Golden
qualification. Required new test methods/files must be listed in the eventual
implementation handoff rather than relying only on these existing suite names.
Keep live-provider/qualification opt-ins disabled for these proposed offline
commands; stop if the selected environment cannot establish that boundary.
Any live P7-14 Golden qualification needs its own explicit authorization.

## 8. Historical approval gates, stop conditions and separate exposure audit

### Product-owner approval recorded 2026-10-16

The owner approved in conversation: the P7-16 no-repeat policy for known-
unassessable response-bearing activities; existing COMMUNICATE_LIMITATION and
learner-controlled Pause; explicit application-owned assessment availability
facts; the compatibility-safe `continuationContract=1` API proposal; the planned
snapshot-checked completion operation within the existing mission-status
application boundary; and registration of P7-16 with its P7-14 dependency.
This supersedes the earlier pending product-owner decision, not the remaining
independent review/formal acceptance gates. It does not authorize production
implementation, medical qualification, risk acceptance or deployment.

- Product-owner approval is recorded above. Education-owner sign-off (or
  evidence that the product owner holds that role) remains required for the
  assessment-limitation/Finish experience, explicit Pause and reduced availability;
  do not claim a satisfactory adaptive-learning rollout merely from safe halt.
- Architecture/API owner: approve the two-result/read-projection contract,
  coordinated strict-client compatibility plan, precedence integration and
  completion transaction ownership. If extra persistence is needed, reopen
  the decision rather than adding schema under this packet.
  Independent architecture and subsequent security requirements reviews approve
  the linked source-ordering rule at design level. The mission lock alone is
  insufficient; the proposed dependency locks remain unimplemented and untested.
  Formal architecture/API-owner acceptance of the whole ADR remains separate.
- Security owner/reviewer: approve authoritative fact derivation, source/user
  boundaries, no-write limitation behavior, races, error/success distinctions
  and regression scope. Independent implementation/security review follows
  actual user/CI validation, not this planning assessment.
- Decision/roadmap owner: formally accept separate ADR-0016, then authorize
  the scoped SOT patches and roadmap count alignment under Document 27.
  Administrative registration/P7-14 dependency are already owner-authorized.
  Design approval
  of ADR-0014/0015 does not supply this continuation implementation approval.

Stop implementation if unavailable vs unknown cannot be independently
established, a new grading/profile authority is needed, an accepted policy
conflicts, new schema/infrastructure or educational behavior is required,
compatibility cannot be agreed, or a security-sensitive invariant is unresolved.
Keep publication, deployment, routing activation and completion prohibited
unless separately authorized. No agent may self-approve its implementation.

Historical free-text grading exposure remains a separate read-only audit:
authorized operator evidence must establish runtime/deployment/routing history,
datastore provenance/test seeding/restores, and aggregate graded non-MCQ
attempt counts by relevant time/runtime without exposing responses or source
content. Do not access student/production records in this task, rewrite past
attempts/receipts, or infer exposure from Golden or focused test reports.
Any historical evidence remediation requires its own decision and scope.

Earlier governance assessment (superseded below): **GOVERNANCE BLOCKED** — this is a
product-approved proposal with administrative task registration and recorded independent requirements closure, pending required owner-role approvals and formal alignment. It is not formal ADR acceptance, implementation authorization or rollout acceptance. See the [independent review record](p7-16-governance-review-2026-10-16.md).

Earlier closure check, 2026-10-16 (superseded by section 9): independent source-ordering requirements closure
is recorded; no completed review was repeated. Remaining blockers are explicit
architecture/API-owner acceptance of the final transport/compatibility/transaction
contract and education-owner assignment/sign-off, followed by formal acceptance
and versioned SOT alignment. Owner confirmations were requested, not inferred.
P7-16 remains Not Started and its stale source-ordering blocker note now references
the resolution. No approved roadmap count or normative policy changes occur
before ADR acceptance. See the final closure section of the review record for
the source mutation coverage and documentation-check findings.


## 9. Final owner replies and governance acceptance, 2026-10-16

The architecture/API owner identified themselves as the responding user and
answered "me, and approved" to the final transport/compatibility/transaction
question. The same user answered "approved" to the separate education-owner
assignment/sign-off question. Record this as direct educational approval in
that role-specific context, not inferred from earlier product approval.
ADR-0016's approval section records the exact provenance. These replies resolve
the historical missing-owner gates; no completed technical review was repeated.

ADR-0016 is ACCEPTED. Documents 06/07/11/15/19/20/26/27 are version-aligned;
Phase 7 roadmap/tracker count is 16 and P7-14 explicitly depends on P7-16.
SEC-016-REQ-001 remains requirements-resolved only, with PostgreSQL verification
future work. P7-16 remains Not Started. Final verdict: READY FOR IMPLEMENTATION
AUTHORIZATION, not permission to execute, deploy, qualify medical grading or
complete tracker work. ADR-0014/0015 remain PROPOSED; P7-15/Phase 8 are unchanged.

Provenance correction before execution planning: the first education reply
"approved" did not explicitly assign the owner role; the prior acceptance
claim was premature. The user's subsequent explicit education-owner declaration
and full role-specific sign-off, quoted in ADR-0016, closes that gap. Acceptance
is reaffirmed after that declaration, not inferred or backdated from the earlier
reply. Architecture/API-owner approval and independent design-review provenance
remain separate. No normative policy or implementation authority is added.
