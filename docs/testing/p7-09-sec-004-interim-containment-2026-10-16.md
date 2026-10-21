# P7-09 SEC-004 interim containment — 2026-10-16

**IMPLEMENTED — USER VALIDATION REQUIRED.** Local offline validation only.
No rollout, deployment, routing activation or security-risk acceptance.
ADR-0014/0015 remain PROPOSED and their index entries remain consistent.
SEC-004 remains High pending execution evidence and independent review gates.

## Scope and files

P7-09 owns response evaluation and formative feedback without false scoring.
The owner's October 16 approval permits this temporary containment without
approving medical profiles or the broader ADR-0015 generation architecture.

| File | Current-task change |
| --- | --- |
| `docs/adr/ADR-0014-bounded-free-text-response-attribution.md` | Record the local-only containment approval, limits and still-open acceptance gates; confirm runtime-revision design approval. |
| `backend/src/main/java/com/hippocampus/ai/infrastructure/learning/AiResponseEvaluationAdapter.java` | Return existing validated UNCERTAIN after pass validation; remove the unused aggregation field. |
| `backend/src/main/java/com/hippocampus/learning/application/SubmitActivityResponseUseCase.java` | Independently contain every valid free-text evaluator result as neutral UNGRADED before persistence/evidence assembly. |
| `backend/src/test/java/com/hippocampus/learning/application/SubmitActivityResponseUseCaseTests.java` | Add 32 adversarial grade/response combinations, source-failure and incorrect-MCQ checks; update the prior PARTIAL submission expectation. |
| `backend/src/test/java/com/hippocampus/ai/infrastructure/learning/AiResponseEvaluationAdapterTests.java` | Model-supported constituent output must remain validated UNCERTAIN with empty dimensions. |
| `backend/src/test/java/com/hippocampus/ai/evaluation/CapturedCase3AttributionReplayTests.java` | Contain formerly graded port outputs while retaining a separate raw constituent-aggregation assertion. |
| `backend/src/test/java/com/hippocampus/ai/evaluation/GoldenResponseAdapterQualificationTests.java` | Assert all nine port outputs are neutral UNCERTAIN; retain prior raw aggregation expectations separately. This is containment regression, not Golden qualification. |
| `backend/src/test/java/com/hippocampus/learning/application/GetStudyMissionUseCaseTests.java` | Check refresh of a stored UNGRADED attempt/receipt and learner-visible continuation without grading claims or answer leakage. |
| This report | Record scope, validation limitations and review outcomes. |

No generation, profile catalog, schema, progression policy, Phase 8 logic,
provider routing/configuration, SEC-005 fingerprint logic or tracker status
changed. Existing unrelated working-tree changes are outside this report.

## Exact containment boundary

No current runtime contract carries an independently approved verification
basis. The AI port therefore returns validated UNCERTAIN even after successful
discovery/verification. Its credited, missing and misconception lists are
empty; feedback is the existing neutral limitation. All pre-existing evidence,
limit, validation and provider-failure handling remains in place. Bounded pass
execution still occurs when its existing preconditions hold; this change does
not disable provider execution or alter routing. All attempted validation here
is offline; no provider was called.

The application use case retains owned mission/activity checks, contract
loading, source resolution, evaluator invocation and result validation. After
those checks, every free-text result becomes UNGRADED with empty dimensions,
limited certainty, neutral saved-response feedback and no deterministic
result. This protects against injected/alternate evaluators returning
CORRECT, PARTIAL, INCORRECT, COMPLETE relationships or constituent claims.
There is no partial-credit exception, model-label override or runtime switch.

The earlier deterministic branch remains unchanged: a contract with nonblank
`correctOption` is evaluated by option comparison. Existing generation
validation requires non-MCQ options to be empty and `correctOption` null;
MCQ keys must identify an option. Those checks do not establish the generated
medical answer key, distractors or question as medically valid. That
qualification risk is outstanding and is not accepted by this containment.

## Persistence, failures and learner impact

The existing persistence use case saves the actual response/payload, owned
activity, request ID, timestamp, limitation and receipt; it completes the
activity under existing snapshot/attempt-number checks. The existing state
assembler excludes UNGRADED from evidence strength while retaining lifecycle
history. No mastery/evidence persistence code was added or changed.

Propagated source-resolution or evaluator exceptions retain their existing
no-attempt failure semantics; invalid evaluator results still fail validation.
Provider/pass failures already normalized by the adapter continue to yield
validated uncertainty. A failure to persist remains a failure, not a confirmed
submission. Exact request replay recovers the existing saved receipt without
another attempt or evaluation. Historical exact receipts, including prior
grades, are replayed unchanged; this patch does not rewrite historical evidence.

Automatic free-text grades and useful partial credit are temporarily
unavailable. Existing Learning Engine decisions are unchanged, so RETRY can
reuse a permanently ungradable artifact. This patch establishes no improved
continuation policy or student-facing rollout readiness.

## Focused validation

Attempted in `backend`:

```powershell
mvn -o -q "-Dtest=SubmitActivityResponseUseCaseTests,AiResponseEvaluationAdapterTests,CapturedCase3AttributionReplayTests,GoldenResponseAdapterQualificationTests,GetStudyMissionUseCaseTests,StudyMissionLearningStateAssemblerTests,ResponseAttributionValidatorTests" test
```

**BLOCKED before compilation:** the offline cache lacks
`org.springframework.boot:spring-boot-starter-parent:4.1.1`.
**Zero Java tests executed; no compilation or runtime PASS is claimed.**
The exact command above is the remaining focused user/CI validation command
once the existing approved dependencies are available. No download or live
qualification was attempted, and previously passed SEC-005 tests were not run.

Added test coverage is unexecuted: four possible model grades across eight
adversarial responses (32 combinations), including borrowed identity,
unasserted COMPLETE relationships, wrong roles, negation, contradiction,
constituents and unfamiliar paraphrase. Each combination checks neutral
persistence, no evidence strength, and exact replay through a fresh use case.
Additional tests cover refresh, source failure and deterministic incorrect
MCQ. Existing focused tests cover propagated failures, malformed results,
ownership, concurrency, staleness, correct MCQ and state assembly. These are
test intentions, not measured passing outcomes. Real PostgreSQL durability
and transaction behavior have not been revalidated in this task.

`git diff --check`: exit code 0; LF/CRLF notices only.

## Independent review

A fresh general-review context inspected the current changed boundaries and
tests. Verdict: **VALIDATION INCOMPLETE**, with no material static correctness
or scope defect identified. It confirmed application containment, preserved
error handling, MCQ classification and historical receipt behavior; successful
execution of the focused suite remains required.

A separate fresh security-review context applied the requested
`hippocampus-security-vulnerability-review` skill after general review.
Verdict: **MANUAL SECURITY REVIEW REQUIRED**. It identified no new material
static security defect in the narrow containment, but the automated-test
prerequisite is unmet. No SECURITY PASS or SEC-004 closure is granted.

**Retained SEC-004: HIGH, BLOCKING for completion/rollout.** The underlying
model-derived attribution/aggregation design still lacks an independently
approved runtime basis (OWASP A06/A08 integrity/design themes; CWE-345).
Raw aggregation and legacy model-result validation remain available, but the
review found no route from them to a new graded free-text submission through
the inspected guarded use case. Containment is not qualification of that
underlying design. MCQ medical-key validity, historical evidence, actual
persistence/concurrency behavior and repeated UNGRADED continuation remain
outside the demonstrated security evidence.

Required follow-up: execute the exact focused command successfully with the
approved dependencies available, supply the result for independent general
and security re-review, and retain all medical and rollout approval gates.

**Final verdict: MANUAL SECURITY REVIEW REQUIRED.** Implementation is local;
compilation, runtime validation, completed security review and student-facing
availability remain unverified.

## Isolated reconstruction follow-up — 2026-10-17

**READY FOR INDEPENDENT GENERAL REVIEW CONFIRMATION.** The October 16 report above is historical and is
preserved. Its zero-tests-executed statement describes that original attempt.
Nothing in this follow-up converts that attempt into a passing validation.

### Candidate and corrected inventory

Equivalent isolated checkout (local shared-object clone, separate index and
working tree; original checkout files were not edited):
`C:/Users/Kenneth/AppData/Local/Temp/hippocampus-p7-09-containment-20261017`.
Exact detached base: `1261587be7150bed981481f56fd494daaab9e9e4`.
The clone borrows Git objects from the original repository; retain its object
store while reviewing this checkout. The complete candidate patch is also
retained separately for reconstruction.

Only these base-relative changes form this candidate:

- `AiResponseEvaluationAdapter.java`: both containment hunks.
- `SubmitActivityResponseUseCase.java`: free-text containment hunk.
- `AiResponseEvaluationAdapterTests.java`: constituent containment assertions.
- `SubmitActivityResponseUseCaseTests.java`: reviewed containment changes plus
  the newly added historical graded-receipt regression below.
- `CapturedCase3AttributionReplayTests.java`: reviewed containment/raw assertions.
- `GoldenResponseAdapterQualificationTests.java`: reviewed containment changes,
  including conservative paraphrase discovery rejection and raw aggregation.
- `GoldenResponseAdapterLiveCollectorTests.java`: containment, raw aggregation,
  contract PASS, matcher FAIL and qualification PENDING assertions only. This
  collector-test change was missing from the original inventory above.
- `GetStudyMissionUseCaseTests.java`: receipt-refresh imports, test, fixture and
  record only, with the P7-16 continuation test omitted from its mixed hunk.
- `StudyMissionLearningStateAssemblerTests.java`: new corrupt-status regression.
- This handoff: historical report plus this follow-up.

The original inventory's ADR-0014 row describes the original combined working
tree, not this isolated candidate. ADR-0014/0015 proposals and unrelated doc
revisions are excluded. The original owner's interim approval was read from
ADR-0014 but that untracked proposal is not copied into this patch.

Excluded base-relative changes: all P7-16 production, migration, frontend and
test changes; collector fingerprint implementation and fingerprint-specific
collector-test imports/assertions; P7-16 continuation test; feasibility spikes,
historical evaluation artifacts, scratch files and local environment data.
The collector implementation remains exactly at the WIP base revision.

### Identity and comparison gate

Evidence directory outside both working trees:
`C:/Users/Kenneth/AppData/Local/Temp/hippocampus-p7-09-containment-20261017-evidence`.
It contains the complete final patch, per-file raw and UTF-8/LF hashes, a
pre-new-test reconstruction inventory, selected original-file preservation
hashes, inherited-commit/file inventories and exact operator commands.

The user subsequently supplied the latest independent review's projected
inventory. Its verdict was VALIDATION INCOMPLETE with no material functional
defect identified in the selected implementation. All nine reconstructed
baseline hashes match the supplied inventory, including the historical handoff.
Current files also match after accounting in memory for the explicitly added
historical-replay test and handoff follow-up; no tests were removed from disk
and no expected hashes were changed. Seven unchanged reviewed Java files match
directly. The assembler regression is an additional file outside that projection.
See `review-projection-comparison.json` for exact expected/actual values.

Normalization is UTF-8 text with LF line endings. The reviewer specified no
further BOM or final-newline handling rules; none are asserted as requirements.
The recorded final candidate hashes include the seven new cases and updated
handoff, so those files legitimately differ from the original projection.

### Subsequent combined-tree validation evidence

**Combined-tree results, user-reported October 17, 2026:** full repository
validator 6/6 PASS; backend clean verify 2,457 tests, 0 failures, 0 errors,
11 skipped. Class results: SubmitActivityResponseUseCaseTests 52 PASS;
AiResponseEvaluationAdapterTests 6 PASS; CapturedCase3AttributionReplayTests
6 PASS; GoldenResponseAdapterQualificationTests 1 PASS;
GoldenResponseAdapterLiveCollectorTests 11 PASS; GetStudyMissionUseCaseTests
12 PASS; StudyMissionLearningStateAssemblerTests 4 PASS;
ResponseAttributionValidatorTests 24 PASS;
StudyMissionPersistenceIntegrationTests 7 PASS; SourceReferenceIntegrationTests
10 PASS. These are results from the mixed P7-09/P7-16 working tree. A complete
combined patch/hash identity was not supplied. They do not establish isolated
validation and do not supersede the historical October 16 failed attempt.
The supplied evidence is retained separately in the evidence directory.

### Remaining coverage and new review surface

Targeted inspection found no dedicated historical graded free-text receipt
replay or corrupt persisted-status assembly regression in the existing
learning/progress tests. Two minimal regressions were therefore added only
in this isolated checkout and require independent review:

- `historicalGradedReceiptReplaysWithoutReevaluationOrRelabeling`: three cases
  (CORRECT/PARTIAL/INCORRECT), seeded historical free-text attempts/receipts,
  unavailable contract and throwing evaluator. Checks exact receipt and saved
  JSON, preserved grade/attempt, no evaluation and no persistence/state change.
- `corruptPersistedEvaluationStatusFailsClosed`: four malformed/unknown status
  cases through persisted-attempt assembly, requiring an exception rather than
  a fabricated INCORRECT result or evidence. Null/blank status rejection is
  already a StudentAttempt construction invariant.

Existing containment tests cover exact UNGRADED replay. The inherited
assembler already parses persisted outcomes strictly and skips UNGRADED
strength. No production behavior, routing, medical key or qualification
authority was added by these two regression tests.

### Execution and operator boundary

The exact focused Maven command in `operator-validation.ps1` was executed on
the isolated candidate with all three live-provider opt-ins explicitly false.
The initial sandbox attempt stopped in the wrapper download before compilation;
the expanded-access retry completed with **BUILD SUCCESS: 122 tests, 0 failures,
0 errors, 0 skipped**, on October 17, 2026 at 22:43:38 Asia/Manila.

| Isolated test class | Passed |
| --- | ---: |
| SubmitActivityResponseUseCaseTests | 55 |
| AiResponseEvaluationAdapterTests | 6 |
| CapturedCase3AttributionReplayTests | 6 |
| GoldenResponseAdapterQualificationTests | 1 |
| GoldenResponseAdapterLiveCollectorTests | 11 |
| GetStudyMissionUseCaseTests | 11 |
| StudyMissionLearningStateAssemblerTests | 8 |
| ResponseAttributionValidatorTests | 24 |

All seven new regression cases passed. Compared with the combined-tree count,
GetStudyMissionUseCaseTests has one fewer case because the P7-16 test is excluded;
SubmitActivityResponseUseCaseTests has three additional historical-grade cases,
and the assembler has four additional corrupt-status cases. Production/test
source bytes were unchanged by this test execution. Raw logs and eight Surefire
XML reports are retained outside the checkout; no live qualification was run.

Subsequent **isolated operator validation**, October 17, 2026:

- PostgreSQL focused suites: BUILD SUCCESS, 17 tests, 0 failures/errors/skips
  (StudyMissionPersistenceIntegrationTests 7; SourceReferenceIntegrationTests 10),
  finished 23:00:34 Asia/Manila.
- Backend clean verify: BUILD SUCCESS, 2,369 tests, 0 failures, 0 errors,
  11 skipped across 231 classes, finished 23:12:01 Asia/Manila. Verified against
  the retained Maven log and the 231 Surefire XML reports, including the seven
  added regression cases and the unchanged selected suite counts above.
- The repository validator's historical overall verdict is FAIL: its
  backend-clean-verify check passed, but git-diff-check exited 129 with
  "Not a git repository". The operator's subsequent standalone check also
  failed Git discovery. The exact cause of that environment difference is not
  established. This is not recorded as a passing validator invocation.
- The executor subsequently verified the same detached base and exact 10-file
  inventory, and ran `git diff --check` successfully (exit 0) in the isolated
  checkout. Thus both required validation components have passing evidence
  without repeating clean verify or changing source. The failed summary/log
  remain preserved alongside the separate successful check.

Before updating this document, all 10 candidate files matched their recorded
pre-operator-run raw hashes. Production and test source files remain unchanged;
only this evidence follow-up and external reports/manifests were updated.
The 11 full-suite skips comprise three live-provider smoke cases, four OCR
adapter cases and four filesystem/storage cases; no passing result is claimed
for skipped cases. Live-provider smoke opt-ins remained inactive in the reports.

Evidence: `operator-postgres-and-backend-transcript.txt`, preserved original
validator summary/logs and all Surefire XML under `operator-backend-validation/`,
and `post-operator-diff-check.txt` in the evidence directory. Hash manifests
bind these artifacts to the isolated tested source inventory. Operator/CI
container execution ownership was preserved; the executor did not rerun tests.

### Inherited WIP and publication boundary

The exact WIP base inherits 22 commits beyond cached `origin/main`
`c488a0918808d731c7c1a2c0522de0375b05c230`. No fetch was performed. Their
commit list and complete base-relative changed-file inventory are retained
separately. In particular cfa3cac supplies the attribution contracts, two-pass
adapter, authorized evidence resolver, UNGRADED persistence/schema/receipt,
refresh support, Golden v7 and related frontend; 1261587 supplies additional
attribution guards, offset normalization, Gemini/schema/prompt changes and
collector/replay corrections. These are inherited prerequisites, not newly
approved containment/publication changes.

No approved publication base was supplied or found in the available task
records. Cached origin/main is an observed ref, not an approved base. Do not
publish this WIP ancestry. Reapplying to a separately approved publication
base requires dependency assessment, appropriate validation and diff review
for any source-byte or behavior change.

Required isolated test and whitespace-check evidence is now available.
The seven new regression cases and final diff still require independent
general-review confirmation. Publication-base approval, SEC-004 High,
Golden evaluation Definition of Done and independent security review remain
explicitly outstanding. No commit, push, merge, deployment or tracker completion.

**Final follow-up verdict: READY FOR INDEPENDENT GENERAL REVIEW CONFIRMATION.**

## SEC-901 security correction — subsequent isolated revision

**READY FOR USER REVALIDATION.** This section supersedes the prior candidate's
review-readiness statement for the newly changed source. The independent
security finding SEC-901 is Medium and blocking pending validation/re-review.
No independent general or security approval is claimed by this executor.

### Finding, authoritative contract and correction

The independent findings supplied by the owner confirmed an inherited bypass:
exact replay checked receipt identity but not persisted outcome validity or
attempt/receipt outcome consistency. Refresh compared two unrestricted strings,
allowing matching unknown values. StudentAttempt accepts nonblank unknown
statuses and V21/V25 do not constrain evaluation_status to the domain enum.
JDBC maps evaluation_status and submission_receipt::text without outcome parsing.
The prior state-assembly test did not exercise these early-return boundaries.

`AttemptOutcome` is the authoritative recognized set: CORRECT, PARTIAL,
INCORRECT, UNGRADED, parsed exactly with valueOf, as in the learning-state
assembler. UNCERTAIN belongs to the evaluator port and is not a persisted
attempt outcome. The authoritative comparison is evaluationStatus versus
receipt.outcome; deterministicResult is the optional MCQ-specific result and
is not substituted for evaluationStatus. Existing StudentAttempt null/blank
and UNGRADED limitation/no-deterministic-result invariants remain unchanged.

SubmissionReceipt now rejects unrecognized outcomes at construction/deserialization.
Its package-local fromAttempt method independently parses the persisted attempt,
decodes/validates the receipt, verifies matching recognized outcomes and existing
attempt/mission/activity receipt identities, and returns the unchanged receipt.
Invalid/unconfirmed data raises the existing typed STALE_SUBMISSION with neutral
"The saved submission could not be confirmed." feedback. No trimming, case
normalization, corrupt-record repair, grade conversion or reevaluation occurs.
Both exact replay and mission refresh use this same cohesive receipt boundary.
Ownership queries, exact response/payload/request matching and source controls
are preserved. Replay fails before contract/evidence/evaluator access or writes;
refresh has no evaluator dependency and performs no attempt/receipt mutation.

### Explicit security-remediation scope extension

The prior general-review inventory did not approve the following new changes:

- SubmissionReceipt.java: enum-backed constructor invariant and stored-attempt
  validation method (new production file in the base-relative patch inventory).
- GetStudyMissionUseCase.java: delegate refresh receipt validation to that method
  (new production file in the base-relative inventory).
- SubmitActivityResponseUseCase.java: replace replay-only identity checks with
  shared stored-attempt/receipt validation, retaining exact-replay controls.
- SubmitActivityResponseUseCaseTests.java: 17 rejection cases: unknown attempt,
  unknown receipt, matching unknowns, malformed case/whitespace, and all 12
  mismatched recognized outcome pairs; each checks zero evaluations/writes,
  unchanged attempt/receipt and repeated rejection through a fresh use case.
- GetStudyMissionUseCaseTests.java: seven corrupt/mismatched refresh cases plus
  four valid historical-outcome cases, asserting unchanged state and zero writes;
  read-only attempt fixtures fail immediately if insertion is attempted.
- StudyMissionPersistenceIntegrationTests.java: eight operator-run JDBC/JSONB
  round-trip cases (four corrupt/inconsistent, four valid outcomes), exercising
  both submission replay and refresh, zero evaluation and durable nonmutation
  (new test file in the base-relative inventory).
- This handoff: current correction scope/evidence. The original October 16 report
  and subsequent pre-correction evidence remain historical.

Final base-relative inventory: four production files, eight test files and this
handoff (13 files). No P7-16, collector fingerprint, schema, routing, ADR proposal,
Phase 8 or unrelated WIP change was added. Isolated P7-16 code remains excluded;
shared receipt/API shapes and constructor signatures remain compatible, but no
combined-tree P7-16 validation is claimed for this corrected revision.

### Current revision validation and remaining gates

Corrected focused unit run: **BUILD SUCCESS, 102 tests, 0 failures, 0 errors,
0 skipped**, October 17, 2026 at 23:42:04 Asia/Manila:
SubmitActivityResponseUseCaseTests 72, GetStudyMissionUseCaseTests 22,
StudyMissionLearningStateAssemblerTests 8. All 28 new application cases passed.
Existing historical CORRECT/PARTIAL/INCORRECT replay, UNGRADED replay, deterministic
MCQ behavior and free-text containment cases passed. The new PostgreSQL test
compiled but its eight cases were not executed by the implementation executor.
Initial sandbox attempt stopped before compilation on wrapper download; the
expanded-access focused retry passed with live-provider opt-ins disabled.

Current git diff --check and patch reverse-check results, current source/patch
hashes, focused log/XML and exact operator commands are recorded in:
`C:/Users/Kenneth/AppData/Local/Temp/hippocampus-p7-09-containment-20261017-sec901-evidence`.
The preceding bundle remains immutable historical evidence for the prior source;
its 122/17/2369 results do not validate newly changed SEC-901 files.

Required next: operator PostgreSQL focused validation and backend clean verify
against these corrected source hashes; independent general review of the added
scope; then a fresh independent security vulnerability review. SEC-901 closure
is not self-approved. The approved publication base remains unresolved. SEC-004
remains HIGH — contained, not resolved; Golden evaluation Definition of Done,
grading reactivation and rollout remain unauthorized. No commit, push, publish,
merge, deployment or tracker Done change.

**SEC-901 implementation verdict: READY FOR USER REVALIDATION.**
