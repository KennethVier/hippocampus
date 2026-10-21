# P7-16 implementation handoff — 2026-10-16

**IMPLEMENTED — USER VALIDATION REQUIRED**

## Authority and scope

Verified accepted ADR-0016 and its corrected approval provenance before implementation:
the owner explicitly identified themself as the education decision owner and approved
the no-repeat policy, limitation messaging, learner-controlled Pause/Finish and reduced
automatic assessment availability. The architecture/API owner approval and independent
requirements dispositions remain design evidence, not implementation review evidence.
The owner's subsequent conversation authorized this local implementation.

No tracker status, ADR status, Source-of-Truth policy, grading guard, schema, dependency,
provider routing, P7-08 generation, MCQ selection/key, P7-15 or Phase 8 implementation was
changed by this task. Existing unrelated working-tree changes are outside this report.

## Changes made

Paths in the first three groups are relative to `backend/src/main/java/com/hippocampus/`.

- Engine facts: `learning/domain/AssessmentAvailability.java` (new), `LearningState.java`,
  `LearningEngine.java`, `LearningRationaleCodes.java`. Explicit saved-UNGRADED context
  and application-owned target classification constrain only known-unavailable response
  targets after existing selection/precedence. Existing MCQ actions are preserved, never
  substituted. Missing/unknown contracts are not approved grading bases.
- Application: new `learning/application/AssessmentAvailabilityResolver.java` and
  `StudyMissionContinuationDecision.java`; changes to `ContinueStudyMissionUseCase.java`,
  `GetStudyMissionUseCase.java`, `ChangeStudyMissionStatusUseCase.java`,
  `MaterializeLearningActivityUseCase.java`, `ActivityMaterializationException.java`.
  GET and Continue share provider-free decision reconstruction. Direct materialization
  also rejects known-unavailable retry/reuse/regeneration before evidence/provider work.
  Dispatch distinguishes real activity creation, write-free limitations and authorized
  lifecycle completion. Submission/receipt persistence is unchanged.
- Persistence/composition: `learning/port/ActivitySourceReferenceAuthorization.java`, new
  `SourceDependencyBusyException.java`, `StudyMissionRepository.java`;
  `learning/infrastructure/persistence/JdbcActivitySourceReferenceAuthorization.java`,
  `JpaStudyMissionRepository.java`; `learning/infrastructure/config/StudyMissionApplicationConfiguration.java`.
  Completion uses the injected existing lifecycle transaction, fresh owned mission write
  lock, full attempt snapshot comparison, individual source dependency locks, post-lock
  authorization and Engine COMPLETE revalidation. A completion-specific fresh read
  refreshes mission/child entities under the mission lock to prevent an earlier request
  persistence context from hiding a concurrent Pause/Stop. Existing lock-read callers
  keep their behavior. Unsupported persistence/source implementations fail closed.
- Transport: `learning/api/StudyMissionContinuationController.java`,
  `StudyMissionReadController.java`, new `ContinuationContractVersion.java` and
  `StudyMissionWithContinuationResponse.java`; `shared/infrastructure/web/ApiExceptionHandler.java`.
  Opt-in v1 has strict 200 outcome variants and a current GET projection. Legacy GET
  shape stays separate; legacy 204 requires actual creation. Unsupported legacy
  continuation is denied with upgrade 409 before presentation completion or other writes.
  Invalid/duplicate/empty/mixed version values are rejected before application dispatch.
- Frontend under `frontend/src/features/study-mission/`: `api/studyMissionContracts.ts`,
  `api/studyMissionApi.ts`, `queries/studyMissionQueries.ts`, `hooks/useStudyMission.ts`,
  `components/MissionInteraction.tsx`, `pages/StudyMissionPage.tsx`. Coordinated opt-in,
  strict parsing, versioned cache key, current decision controls, limitation/Pause/Finish,
  and refetch after ambiguous/conflicting continuation without automatic mutation retry.
- Backend tests: new `P716ContinuationTests`, `ContinuationContractVersionTests`,
  `StudyMissionContinuationOrderingIntegrationTests`; updated
  `ContinueStudyMissionUseCaseTests`, `GetStudyMissionUseCaseTests`,
  `MaterializeLearningActivityUseCaseTests`, `ActivityMaterializationPersistenceIntegrationTests`,
  `StudyMissionReadControllerWebTests`, `StudyMissionContinuationControllerWebTests`,
  `StudyMissionApplicationConfigurationTests`.
- Frontend tests: `api/studyMissionApi.test.ts`, `pages/StudyMissionPage.test.tsx`,
  `frontend/e2e/study-mission.spec.ts`. Existing transport fixtures now use opt-in;
  the new browser scenario preserves an UNGRADED receipt through refresh/Pause/resume
  without a new assessment. Browser tests were not executed.
- This handoff document only; governance/tracker records were not changed.

## Transaction and compatibility safeguards

Completion is application-only; public Pause/Resume/Stop commands are unchanged.
It requires effective READ COMMITTED, ACTIVE owned mission, current completed activity,
matching mission/activity timestamps and matching saved attempt identities/content.
Source rows are deduplicated and sorted by canonical UUID in fixed table order:
`material_versions -> materials -> source_references -> document_nodes -> chunks -> visual_assets`.
Each uses individual `FOR SHARE NOWAIT`; mappings and full frozen authorization are
freshly checked after locking, with no lock-set expansion. Required empty sources fail.
Ordinary active-version replacement is not revocation. No provider/network calls occur
inside completion. Locks survive through lifecycle commit; only dependency contention or
changed binding becomes stale conflict after rollback. Other failures retain error semantics.

No attempt, receipt, artifact, activity or evidence is appended by NO_ACTIVITY. Repeated
limitation requests are write-free; repeated terminal completion conflicts without a
second timestamp write. GET never commits COMPLETE or rewrites the saved receipt.

Deployments remain unauthorized. A future authorized rollout must coordinate guarded
backend before updated frontend, retain the opt-in contract during cached-client overlap,
and never fall back from malformed/legacy opt-in responses to inferred success. No
rollback may remove the containment/continuation guard while affected clients remain.

## Actual agent execution evidence

Only focused newly created/modified cheap tests were attempted. No PostgreSQL,
Testcontainers, application/browser startup, broad validation, lint/typecheck/build,
SEC-005 test or git hygiene command was run.

Backend workdir:

```powershell
.\mvnw.cmd -o -q "-Dtest=P716ContinuationTests#savedUnassessableResponseReturnsRepeatableLimitationWithoutWritesOrMaterialization" test
mvn -o -q "-Dtest=P716ContinuationTests#savedUnassessableResponseReturnsRepeatableLimitationWithoutWritesOrMaterialization" test
```

Both exited 1 before compilation/test execution. The wrapper tried to download its
uncached Maven distribution and was denied network access. Installed Maven reached
offline resolution but lacked `org.springframework.boot:spring-boot-starter-parent:pom:4.1.1`.
**No backend compilation or backend test pass is claimed.**

Frontend workdir, commands with observed exit 0:

```powershell
npm.cmd test -- src/features/study-mission/api/studyMissionApi.test.ts -t "rejects legacy 204"
npm.cmd test -- src/features/study-mission/api/studyMissionApi.test.ts -t "accepts no activity"
npm.cmd test -- src/features/study-mission/pages/StudyMissionPage.test.tsx -t "keeps saved ungraded work"
npm.cmd test -- src/features/study-mission/api/studyMissionApi.test.ts -t "rejects contradictory materialization metadata"
npm.cmd test -- src/features/study-mission/pages/StudyMissionPage.test.tsx -t "finishes only through explicit Continue|refetches an ambiguous Continue result"
```

Observed results respectively: 1 passed/21 skipped; 1/21; 1/25; 1/22; 2/26.
The first command was repeated after removing an unused test binding: 1 passed/22 skipped.
These are **six distinct passing tests**, not entire-file/suite passes. Skipped tests
are not passes. The original `npm test` spelling was blocked by PowerShell's npm.ps1
execution policy before Vitest; using npm.cmd resolved that invocation problem.

The earlier 104/104 operator evidence concerns P7-09 containment before these changes;
it is not validation of P7-16 or an independently repeated run.

## Operator/CI validation commands (not executed by the agent)

Keep all live qualification/provider opt-ins disabled in the validation shell:

```powershell
$env:HIPPOCAMPUS_LIVE_AI_SMOKE = 'false'
$env:HIPPOCAMPUS_LIVE_AI_GOLDEN = 'false'
$env:HIPPOCAMPUS_LIVE_AI_GOLDEN_ADAPTER = 'false'
```

From `backend` (normal operator Maven/dependency setup required):

```powershell
.\mvnw.cmd -B -ntp "-Dtest=P716ContinuationTests,ContinuationContractVersionTests,LearningEngineTests,LearningEngineScenarioTests,StudyMissionLearningStateAssemblerTests,ContinueStudyMissionUseCaseTests,ChangeStudyMissionStatusUseCaseTests,GetStudyMissionUseCaseTests,MaterializeLearningActivityUseCaseTests,StudyMissionReadControllerWebTests,StudyMissionContinuationControllerWebTests,StudyMissionApplicationConfigurationTests,SubmitActivityResponseUseCaseTests,AiResponseEvaluationAdapterTests,CapturedCase3AttributionReplayTests,GoldenResponseAdapterQualificationTests,ResponseAttributionValidatorTests" test

# Real PostgreSQL/Testcontainers and existing application transactions; operator/CI only
.\mvnw.cmd -B -ntp "-Dtest=StudyMissionContinuationOrderingIntegrationTests,StudyMissionPersistenceIntegrationTests,ActivityMaterializationPersistenceIntegrationTests,SourceReferenceIntegrationTests" test

# Broader backend gate, excluding the already validated SEC-005 collector/fingerprint class
.\mvnw.cmd -B -ntp "-Dtest=*,!GoldenResponseAdapterLiveCollectorTests" clean verify
```

From `frontend`:

```powershell
npm.cmd test -- src/features/study-mission/api/studyMissionApi.test.ts src/features/study-mission/pages/StudyMissionPage.test.tsx
npm.cmd run test:e2e -- e2e/study-mission.spec.ts
```

From repository root:

```powershell
node scripts/validation/validate.mjs frontend
git diff --check
```

Capture exit codes and Surefire/Vitest/Playwright counts, including failures/errors/skips.
Stop on a failure; do not call a quiet Maven command a numerical pass without reports.
The frontend script runs the existing lint/typecheck/test/build gate and diff check.
The broader backend command intentionally excludes the SEC-005 class rather than
rerunning its fingerprint validation without a direct dependency.

## Acceptance evidence to collect

| Requirement | Added/updated evidence and required validation |
| --- | --- |
| Saved UNGRADED + known-unavailable retry/reuse/regeneration | P716ContinuationTests actual Engine/application tests; direct materializer rejects before provider/persistence calls. |
| Ordinary uncertainty, MCQ, precedence | P716ContinuationTests routing facts/target tests plus existing Engine/scenario/assembler/submission tests; no MCQ substitution. |
| Legacy/v1 dispatch and strict mixed-version behavior | Continue/read controller tests, version parser tests, API tests; legacy before-write denial and no dummy activity UUID. |
| Read-only current decision and immutable receipt | GetStudyMissionUseCaseTests repeated opt-in/legacy reads and unchanged receipt/mission; existing persistence/submission replay tests. |
| Atomic completion, ownership, source authorization | New PostgreSQL ordering tests exercise real transaction proxy/JPA/JDBC, owner/required-source checks, frozen replacement, rollback and incompatible isolation. |
| Source revocation ordering | Real PostgreSQL tests: committed deletion first, in-flight writer/NOWAIT and released partial locks, completion first with pg_blocking_pids evidence, conflicts on every one of the six referenced dependency table kinds, chunk/page invalidation. |
| Concurrent lifecycle and stale persistence context | New PostgreSQL tests: concurrent completion once, terminal duplicate denial, stale Pause/Stop snapshot, retained request context cannot hide committed Pause, rollback after save failure. Existing submission/materialization lock tests remain required. |
| Pause, refresh/resume, Finish, ambiguous/conflicting writes | Page tests and new mocked browser limitation scenario; no automatic mutation retry or completion. Browser execution and broader frontend gates pending. |
| Containment and no fabricated evidence | Existing applicable seven-class containment regression is included in the focused command; new tests assert no attempt/write/materialization for limitations and unchanged history after completion. |

Synthetic fixture medical terms/answer keys are not medical qualification. Test source
code and mocked API/browser scenarios do not prove PostgreSQL ordering or production
grading availability. Existing repeated UNGRADED history is preserved; the new policy
stops known-unassessable repeats without grading, substituting MCQs, or creating mastery.
MCQ medical answer-key qualification remains an outstanding independent risk.

## Remaining gates

Backend compilation, all operator/CI validation above, actual PostgreSQL ordering and
concurrency evidence, frontend typecheck/build/E2E, then **external general implementation
review followed by independent security vulnerability review** are pending. Apply the
repository's ASVS 5.0.0/security review requirements at that separate gate. No review
pass, medical qualification, P7-14 release gate, tracker completion or rollout approval
is claimed. Any blocking finding requires correction before those gates can close.
