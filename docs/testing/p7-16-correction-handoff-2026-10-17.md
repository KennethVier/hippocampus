# P7-16 correction handoff — 2026-10-17

IMPLEMENTED — USER VALIDATION REQUIRED

This is an implementation handoff, not independent review or approval. The current correction packet explicitly authorized implementation and focused application, PostgreSQL, frontend and browser checks. No commit, staging, push, PR, deployment, separate security review or tracker status transition was performed.

## Corrections

### A — Presentation authorization at commit

The previous presentation writer used a nonlocking ownership/scope predicate that omitted target/chunk/page eligibility. The writer now joins an effective READ COMMITTED write transaction and obtains the existing owned-mission write lock, refreshes the aggregate through the existing repository, and checks ACTIVE/current activity/timestamp/unfinished state before mutation.

It locks the owned generated artifact FOR UPDATE, locks existing artifact-source relations FOR SHARE in source UUID order, and compares their exact set to the current activity. It then reads the origin and positively classifies the complete presentation contract using the real repository. Existing origins must agree with artifact, objective, mission and owner. V26 prevents origin update/delete; the artifact parent lock also prevents concurrent origin/source-link insertion through their foreign keys. No inferred origin is created. An unbound historical artifact can still complete its approved presentation contract; this does not authorize reuse or assessment.

For source authorization it reuses JdbcActivitySourceReferenceAuthorization.lockAndValidateForCompletion, with no duplicate SQL predicates. Individual deduplicated dependency rows are locked FOR SHARE NOWAIT in the approved order:

material_versions → materials → source_references → document_nodes → chunks → visual_assets

The existing implementation rereads locked bindings and validates frozen material/version/node scope, owner/non-DELETED lifecycle, target existence, active chunks and page bounds. Locks survive until commit/rollback. Artifact content/provenance remain locked through the same commit. Continue translates source dependency contention to the existing sanitized stale conflict only after the injected presentation transaction rolls back. No provider call is inside this transaction.

Source-path trace: Continue's classification/source presentation queries are preliminary nonlocking checks; materialization and mission completion already use the authoritative SEC-016 locked validator. Presentation now does too. Response submission retains its existing pre-evaluation evidence resolver and mission/attempt snapshot persistence boundary; this correction does not redesign response submission.

### B — Indeterminate HTTP outcomes

Continue now treats HTTP 500–599 as indeterminate, alongside existing network and invalid-response failures. It blocks further writes, refetches authoritative state, leaves failed recovery blocked until explicit recovery succeeds, and never resends the mutation automatically. Confirmed 409 handling and deterministic 4xx rejection remain separate. Navigation tests include late HTTP 502/504 recovery responses. Browser fixtures explicitly simulate a committed new activity followed by 502/504.

### C — Persisted VISUAL application flows

The new parameterized PostgreSQL flow exercises real materialization persistence, origin bindings, visual source provenance, JsonActivityResponseContractRepository, AssessmentAvailabilityResolver and Continue. Only the bounded AI/evidence ports use synthetic fixtures; no live provider is called.

| Concrete type | Represented action/contract | Observed assertion |
| --- | --- | --- |
| VISUAL | UNDERSTAND / EXPLANATION_V2 | PRESENTATION; explicit completion, next policy-selected activity, no attempt/evidence fabricated |
| VISUAL | CONNECT / CONCEPT_CONNECTION_V2 | RESPONSE_BEARING; saved UNGRADED establishes UNAVAILABLE; repeated limitation is write-free |
| VISUAL | APPLY / CONTEXTUAL_APPLICATION_V1 | RESPONSE_BEARING; saved UNGRADED establishes UNAVAILABLE; no regeneration |
| VISUAL | RETRIEVE / IDENTIFICATION | RESPONSE_BEARING; saved UNGRADED establishes UNAVAILABLE |
| VISUAL | RETRIEVE / MCQ | DETERMINISTIC routing fixture; retry preserves artifact/origin and calls no AI |
| VISUAL | CONNECT / historical V1 | Approved PRESENTATION exception; no response contract invented |

Every flow tests non-object/incomplete JSONB and task mismatch as UNKNOWN, with no new materialization/provider call. A separate real PostgreSQL case proves an unbound historical visual V1 connection remains presentation-only, refuses legacy admission before writes and completes through opt-in without creating an origin, attempt, grade or provider call. Synthetic MCQ uncertainty is a routing fixture, not a claim that normal deterministic submission yields UNGRADED or that its medical key is qualified.

Additional PostgreSQL regressions cover populated V25→V26 upgrade with unchanged legacy rows/receipts/provenance and zero inferred origins; two materializations from one snapshot admitting only one activity/artifact/origin; committed source/contract invalidation; chunk/page/frozen-target rejection; schema rejection of nonexistent targets; contention on all six dependency table kinds; in-flight revocation first; artifact/provenance writer first; presentation holding locks before competing source/artifact/provenance invalidation; and post-save rollback/lock release. Fresh connections compare durable state, including actual historical receipts.

## Files changed in this correction round

- backend/src/main/java/com/hippocampus/learning/application/ContinueStudyMissionUseCase.java
- backend/src/main/java/com/hippocampus/learning/application/PersistPresentationCompletion.java
- backend/src/main/java/com/hippocampus/learning/infrastructure/config/StudyMissionApplicationConfiguration.java
- backend/src/test/java/com/hippocampus/FlywayMigrationApplicationTests.java
- backend/src/test/java/com/hippocampus/learning/application/ContinueStudyMissionUseCaseTests.java
- backend/src/test/java/com/hippocampus/learning/application/GetStudyMissionUseCaseTests.java
- backend/src/test/java/com/hippocampus/learning/application/P716ContinuationTests.java
- backend/src/test/java/com/hippocampus/learning/application/PresentationAdmissionTests.java
- backend/src/test/java/com/hippocampus/learning/infrastructure/persistence/StudyMissionContinuationOrderingIntegrationTests.java
- frontend/e2e/study-mission.spec.ts
- frontend/src/features/study-mission/hooks/useStudyMission.ts
- frontend/src/features/study-mission/pages/StudyMissionPage.test.tsx
- docs/testing/p7-16-correction-handoff-2026-10-17.md

The origin migration, authoritative source SQL and numbered/ADR authority were not changed in this round. No P7-15, Phase 8, provider routing, generation prompt or grading policy was added.

## Execution evidence

- Final focused backend/application: 86 tests, 0 failures/errors/skips. Log: .validation/logs/p7-16-correction-focused.log.
- Final affected PostgreSQL: 77 tests, 0 failures/errors/skips (Flyway 9; materialization 10; ordering 58). Log: .validation/logs/p7-16-correction-postgres-final.log.
- Frontend recovery file: 51 passed. TypeScript typecheck and ESLint: exit 0.
- Final Study Mission browser run: 26 passed, 0 failed; desktop and mobile Chromium. Log: .validation/logs/p7-16-correction-playwright-final.log.
- git diff --check: exit 0.

The first restricted Maven attempts did not execute Java tests. An initial escalation was rejected under the superseded read-only review role; supplying the current implementation/test authorization resolved the rejection. Focused fixture failures and the VISUAL frozen-identity fixture failure were corrected and are superseded by final results above. An earlier browser attempt encountered Chromium ERR_NO_BUFFER_SPACE during initial navigation; final browser evidence is recorded separately. Assertions were not weakened and no production schema was changed to accommodate fixtures.

Current source hashes and final run correspondence are retained locally in .validation/p7-16-correction-source-manifest.json. Generated logs and the manifest are operator evidence, excluded from publication.

## Publication isolation

No index or commit was created. Future P7-16 publication must use the separately isolated P7-09 containment as its prerequisite base, preserving its independent review gates. Do not silently include response-grading/collector changes in the P7-16 diff.

GetStudyMissionUseCaseTests now has distinct methods: refreshRestoresPersistedUngradedReceiptWithoutInventingAResult plus savedUngradedFixture are the P7-09 receipt coverage; optInContinuationPreservesTheReceiptAndLegacyReadWhileProjectingTheCurrentDecision is P7-16 continuation coverage. Their test behaviors are independently asserted, allowing explicit hunk-level separation after the prerequisite base is established.

Complete P7-16 code/test inventory relative to the current Git index (includes untracked source):

```text
backend/src/main/java/com/hippocampus/learning/api/ContinuationContractVersion.java
backend/src/main/java/com/hippocampus/learning/api/StudyMissionContinuationController.java
backend/src/main/java/com/hippocampus/learning/api/StudyMissionReadController.java
backend/src/main/java/com/hippocampus/learning/api/StudyMissionWithContinuationResponse.java
backend/src/main/java/com/hippocampus/learning/application/ActivityMaterializationException.java
backend/src/main/java/com/hippocampus/learning/application/AssessmentAvailabilityResolver.java
backend/src/main/java/com/hippocampus/learning/application/ChangeStudyMissionStatusUseCase.java
backend/src/main/java/com/hippocampus/learning/application/ContinueStudyMissionUseCase.java
backend/src/main/java/com/hippocampus/learning/application/GetStudyMissionUseCase.java
backend/src/main/java/com/hippocampus/learning/application/MaterializeLearningActivityUseCase.java
backend/src/main/java/com/hippocampus/learning/application/PersistMaterializedActivity.java
backend/src/main/java/com/hippocampus/learning/application/PersistPresentationCompletion.java
backend/src/main/java/com/hippocampus/learning/application/StudyMissionContinuationDecision.java
backend/src/main/java/com/hippocampus/learning/domain/AssessmentAvailability.java
backend/src/main/java/com/hippocampus/learning/domain/LearningEngine.java
backend/src/main/java/com/hippocampus/learning/domain/LearningRationaleCodes.java
backend/src/main/java/com/hippocampus/learning/domain/LearningState.java
backend/src/main/java/com/hippocampus/learning/infrastructure/config/StudyMissionApplicationConfiguration.java
backend/src/main/java/com/hippocampus/learning/infrastructure/persistence/JdbcActivitySourceReferenceAuthorization.java
backend/src/main/java/com/hippocampus/learning/infrastructure/persistence/JdbcGeneratedArtifactRepository.java
backend/src/main/java/com/hippocampus/learning/infrastructure/persistence/JpaStudyMissionRepository.java
backend/src/main/java/com/hippocampus/learning/infrastructure/persistence/JsonActivityResponseContractRepository.java
backend/src/main/java/com/hippocampus/learning/port/ActivityResponseContractRepository.java
backend/src/main/java/com/hippocampus/learning/port/ActivitySourceReferenceAuthorization.java
backend/src/main/java/com/hippocampus/learning/port/GeneratedArtifactRepository.java
backend/src/main/java/com/hippocampus/learning/port/SourceDependencyBusyException.java
backend/src/main/java/com/hippocampus/learning/port/StudyMissionRepository.java
backend/src/main/java/com/hippocampus/shared/infrastructure/web/ApiExceptionHandler.java
backend/src/main/resources/db/migration/V26__bind_generated_artifact_objective_origin.sql
backend/src/test/java/com/hippocampus/FlywayMigrationApplicationTests.java
backend/src/test/java/com/hippocampus/learning/api/ContinuationContractVersionTests.java
backend/src/test/java/com/hippocampus/learning/api/StudyMissionContinuationControllerWebTests.java
backend/src/test/java/com/hippocampus/learning/api/StudyMissionReadControllerWebTests.java
backend/src/test/java/com/hippocampus/learning/application/AssessmentAvailabilityCompatibilityTests.java
backend/src/test/java/com/hippocampus/learning/application/ContinueStudyMissionUseCaseTests.java
backend/src/test/java/com/hippocampus/learning/application/GetStudyMissionUseCaseTests.java
backend/src/test/java/com/hippocampus/learning/application/MaterializeLearningActivityUseCaseTests.java
backend/src/test/java/com/hippocampus/learning/application/P716ContinuationTests.java
backend/src/test/java/com/hippocampus/learning/application/PresentationAdmissionTests.java
backend/src/test/java/com/hippocampus/learning/infrastructure/config/StudyMissionApplicationConfigurationTests.java
backend/src/test/java/com/hippocampus/learning/infrastructure/persistence/ActivityMaterializationPersistenceIntegrationTests.java
backend/src/test/java/com/hippocampus/learning/infrastructure/persistence/JsonActivityResponseContractRepositoryTests.java
backend/src/test/java/com/hippocampus/learning/infrastructure/persistence/StudyMissionContinuationOrderingIntegrationTests.java
frontend/e2e/study-mission.spec.ts
frontend/src/features/study-mission/api/studyMissionApi.test.ts
frontend/src/features/study-mission/api/studyMissionApi.ts
frontend/src/features/study-mission/api/studyMissionContracts.ts
frontend/src/features/study-mission/components/MissionInteraction.tsx
frontend/src/features/study-mission/hooks/useStudyMission.ts
frontend/src/features/study-mission/pages/StudyMissionPage.test.tsx
frontend/src/features/study-mission/pages/StudyMissionPage.tsx
frontend/src/features/study-mission/queries/studyMissionQueries.ts
frontend/src/test/App.test.tsx
```

Approved P7-16 governance/documentation inventory (retain ADR-0016/0017 decision provenance; no status completion implied):

```text
docs/06-user-journey-and-learning-flow.md
docs/07-feature-specifications.md
docs/11-ai-learning-engine.md
docs/15-ai-evaluation-strategy.md
docs/18-domain-model-and-database-design.md
docs/19-backend-architecture.md
docs/20-frontend-architecture.md
docs/26-development-roadmap-and-implementation-phases.md
docs/27-decision-log-and-adr-index.md
docs/IMPLEMENTATION-TRACKER.md
docs/adr/ADR-0016-assessment-unavailable-mission-continuation.md
docs/adr/ADR-0017-generated-artifact-objective-origin-binding.md
docs/testing/p7-16-assessment-unavailable-continuation-proposal-2026-10-16.md
docs/testing/p7-16-governance-review-2026-10-16.md
docs/testing/p7-16-implementation-handoff-2026-10-16.md
docs/testing/p7-16-source-revocation-ordering-resolution-2026-10-16.md
docs/testing/p7-16-correction-handoff-2026-10-17.md
```

Keep separate: AiResponseEvaluationAdapter, SubmitActivityResponseUseCase, their containment tests, CapturedCase3AttributionReplayTests, GoldenResponseAdapterQualificationTests, GoldenResponseAdapterLiveCollector and its tests; ADR-0014/0015 proposals, feasibility-spike Java/Python/corpus/report files and P7-09 containment/evaluation evidence. These were preserved. Exclude scratch root/review patches, .review-bundles archives, .validation output, retained evaluation outputs, frontend/.env, browser reports/results and local settings.

## Exact operator revalidation

Keep live-provider opt-ins disabled. From backend:

```powershell
$env:HIPPOCAMPUS_LIVE_AI_SMOKE = 'false'
$env:HIPPOCAMPUS_LIVE_AI_GOLDEN = 'false'
$env:HIPPOCAMPUS_LIVE_AI_GOLDEN_ADAPTER = 'false'
.\mvnw.cmd -B -ntp "-Dtest=PresentationAdmissionTests,ContinueStudyMissionUseCaseTests,P716ContinuationTests,GetStudyMissionUseCaseTests,AssessmentAvailabilityCompatibilityTests,JsonActivityResponseContractRepositoryTests,MaterializeLearningActivityUseCaseTests,StudyMissionApplicationConfigurationTests" test
.\mvnw.cmd -B -ntp "-Dtest=StudyMissionContinuationOrderingIntegrationTests,ActivityMaterializationPersistenceIntegrationTests,FlywayMigrationApplicationTests" test
```

From frontend:

```powershell
npm.cmd test -- src/features/study-mission/api/studyMissionApi.test.ts src/features/study-mission/pages/StudyMissionPage.test.tsx
npm.cmd run typecheck
npm.cmd run lint
npm.cmd run test:e2e -- e2e/study-mission.spec.ts
```

Finally, from repository root:

```powershell
node scripts/validation/validate.mjs all
git diff --check
```

Broad repository validation is deliberately operator-run. Re-review the final corrected diff with that evidence, then perform the separate independent security gate. These local results do not grant medical qualification, independent implementation approval, SEC-004/SEC-016 closure, rollout permission or tracker Done.

READY FOR USER REVALIDATION
