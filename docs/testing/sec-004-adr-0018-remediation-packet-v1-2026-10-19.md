# SEC-004 / ADR-0018 bounded remediation packet v1 — 2026-10-19

**DRAFT — OWNER APPROVAL REQUIRED. NOT IMPLEMENTATION AUTHORIZATION.**

Decision: [ACCEPTED ADR-0018](../adr/ADR-0018-response-evaluation-authority-and-historical-evidence-eligibility.md). Task: P7-09 SEC-004 correction with necessary P7-15/P7-16 consumer integration; no later-phase implementation or tracker completion. Stage C is authorized by explicit conversational owner approval; execution results are recorded separately. SEC-004 remains HIGH.

## Goal and exact source

Close unqualified medical-scoring authority and historical evidence-consumption paths while preserving deterministic MCQ, audit truth, ownership, sources, replay and safe continuation. This packet does not implement medical semantic qualification.

Read-only baseline: `C:/Users/Kenneth/Projects/Hippocampus/.validation/phase7-gate1-remediated-candidate-20261019`.

- Manifest SHA-256: `0de8c917647a76d5d9d300589373a0c651632968bc577dedac06ea911bb99941`.
- Combined patch SHA-256: `199662341e75e04efa8a9d9016d5dfa8c24cd79c8f40183387e1bbec347726af`.
- Proposed implementation destination after approval: `.validation/sec004-adr0018-remediation-candidate-v1-20261019`; evidence sibling `.validation/sec004-adr0018-remediation-evidence-v1-20261019`. Neither is created by this proposal. If occupied, stop rather than overwrite; record a new explicit version.

Initialize the new isolated derivative from verified source files, preserving the source manifest and existing integrated docs/migrations. Do not use the unrelated current working tree as the production base, copy build caches/evidence as source, modify the baseline candidate, or reuse an already-migrated disposable database with an incompatible migration history. Add accepted decision/SOT patches only after owner approval. Record a complete resulting source manifest, changed-file list and baseline-relative patch hash.

Authority: Documents 11/15/18/27; accepted ADR-0010/0011/0012/0013/0016/0017 and current P7-09/P7-14/P7-15/P7-16 tracker entries. Preserve [recovered original report](../../.validation/sec004-recovery-assessment-20261019-v1/original-review-recovered.md), [fresh assessment](../../.validation/sec004-recovery-assessment-20261019-v1/fresh-security-assessment.md) and [P7-09 qualification packet](../../.validation/sec004-recovery-assessment-20261019-v1/p7-09-qualification-packet-v1.md) byte-for-byte. Authority/source hashes and working/candidate differences are recorded in `.validation/sec004-decision-packet-20261019-v1/source-and-authority-identity.json`.

## Minimum implementation changes after approval

1. **One eligibility capability.** Add a cohesive application-owned `AttemptEvidenceEligibility` (name may be adjusted without changing responsibility) returning typed eligible/non-scoring/corrupt decisions from owned mission/activity/attempt plus resolved trusted contract facts. No Spring/provider SDK in domain policy. Do not mutate StudentAttempt, silently catch repository errors, or expose a request field to opt into trust. Reuse resolved facts per request and recheck at final persistence; do not invent a qualification registry.
2. **Positive MCQ admission.** Resolve actual bound artifact, validated MCQ shape, owner/mission/objective and current source scope through existing ports. Confirm allowed option and recomputed outcome, evaluationStatus and deterministicResult consistency. Do not replace missing identity or accept a free-text artifact carrying a forged deterministic marker. Keep ordinary approved MCQ behavior, including incorrect outcomes, separate from unavailable history. New submitted option validity and final direct-persistence admission must agree. Conflicting historical contracts are ineligible, not regraded in storage.
3. **Effective policy history.** Pass eligibility with history to `StudyMissionLearningStateAssembler` and every direct lifecycle predicate. Ineligible grades produce no evidence strength and no graded-performance/failure signal; retain activity chronology and an explicit non-scoring effective disposition. Null cannot represent an ineligible response because null already describes presentation completion. Unknown status or corrupt identity remains an error. Handle pending submission outcomes through the same server-established authority so a raw pending enum cannot bypass policy.
4. **Availability and routing.** Extend `AssessmentAvailability` to distinguish a genuine saved UNGRADED response from an ineligible historical evaluation, and use a common non-scoring-guard predicate without lying about savedUngraded. Update resolver/shared continuation/Engine consumers coherently. Preserve ADR-0016 precedence and known-unavailable versus unknown distinction; no repeated known-unassessable target, synthetic attempt, automatic mission reset, or evidence-driven advancement from excluded history.
5. **Follow-up lifecycle.** Check eligibility for origin, basis, prerequisite/restoration, supersession, selection, creation, final materialization/link/control and submission admission. Existing nonterminal unqualified-origin intents are hidden from GET policy projections and canceled on authorized writes as CANCELED/SAFETY, with diagnostic EVIDENCE_INELIGIBLE. Do not mutate immutable intent fields, old activities or receipts. Do not let this cancellation be returned as learner failure or silently switch to a stale graded action. A current linked activity must pass independent admission before another submission; pause/display/replay remain supported. Non-scoring real activity chronology may still supersede an old intent under existing rules; it never restores understanding or creates a graded obligation.
6. **Production helper authority.** Make `VerifiedResponseEvaluationAggregator.aggregate` return neutral UNCERTAIN with no scoring claims while no independently approved runtime basis exists. Retain structural validator checks and test fixtures. No boolean `qualified`, source phrase, model label or configuration flag can enable a production grade. Do not delete adversarial tests or change clinical expected meaning to make tests green. Existing synthetic aggregation-output tests must clearly become containment assertions or retained observational qualification fixtures outside the trusted production result path; no new live collector is authorized.
7. **Final writer.** `PersistActivityResponse` must reject a proposed graded free-text attempt or unverified deterministic marker before append/mission transition/intent write. Validate pending outcome, receipt and requested transition against server-authorized facts under the owned mission lock and existing snapshot/source/contract revalidation; do not trust `nextStage`, `completeMission` or a Basis string as authority. Failure rolls back all writes. Preserve no-network-in-transaction, canonical source lock ordering, replay identity and duplicate concurrency behavior. Reuse existing continuation and deterministic policies; avoid duplicating a second engine.
8. **Wire once.** Update existing application configuration/constructors and affected tests. Domain policies receive typed trusted facts; infrastructure performs persistence/contract resolution. No new external dependency, generic provenance framework, schema migration, backfill, qualification enablement or Phase 8 projector is in scope.

### Expected production files (paths relative to candidate)

Under `backend/src/main/java/com/hippocampus/`:

- New application eligibility capability and narrowly scoped typed fact/decision if needed.
- `ai/application/validation/VerifiedResponseEvaluationAggregator.java`; `ResponseAttributionValidator.java` only if a concrete structural invariant needs correction, not a lexical medical entailment heuristic.
- `learning/application/StudyMissionLearningStateAssembler.java`, `AssessmentAvailabilityResolver.java`, `StudyMissionContinuationDecision.java`, `FollowUpIntentLifecycle.java`, `SubmitActivityResponseUseCase.java`, `PersistActivityResponse.java`.
- `learning/domain/AssessmentAvailability.java`, and only affected `LearningState`/`LearningEngine` fact consumers or follow-up policy signatures.
- Existing `learning/infrastructure/config/StudyMissionApplicationConfiguration.java`; existing contract-port/JSON adapter capabilities only where necessary for positive owned MCQ proof. Do not widen weak defaults to allow.
- `GetStudyMissionUseCase`, `ContinueStudyMissionUseCase`, `ChangeStudyMissionStatusUseCase`, `MaterializeLearningActivityUseCase`, `PersistMaterializedActivity` only where final eligibility/fact propagation cannot be enforced through their shared collaborators. Preserve their lock/source ordering. `SubmissionReceipt`, `StudentAttempt` and their JDBC serialization are not to be relabeled or extended for this proposal.

The detailed pathway inventory is `.validation/sec004-decision-packet-20261019-v1/grade-consumer-inventory.md`. Stop for a concrete necessary file outside this bounded surface rather than sweeping unrelated code. Documentation changes after approval are the scoped changes listed in ADR-0018; no task status is marked Done.

## Required regression acceptance

All are required expected behaviors, not existing PASS claims. Add `learning/application/AttemptEvidenceEligibilityTests.java` for the new cohesive eligibility capability and named methods to the closest existing test classes; use narrow method execution where practical. Add a focused persistence test class only if the existing real PostgreSQL fixtures cannot exercise direct response persistence. Preserve historical Golden files and original review evidence.

### Semantic authority and containment

- Original plain answer “Wrist extensors are denervated” cannot gain radial identity; multi-assertion expansion, negation, contradiction, omitted qualifiers, unrelated exact excerpts and malicious SUPPORTED/FAITHFUL agreement remain non-scoring at helper, adapter, alternate evaluator submission and persistence boundaries.
- Fresh fixture “Radial nerve.” plus CONSTITUENT meaning “Radial nerve injury causes wrist drop”, MISSING coverage and SUPPORTED verification cannot release PARTIAL/full-relation credit. COMPLETE variant likewise cannot release CORRECT. Assert empty credited concepts/feedback claims, not only final label.
- Preserve Case 3 and Case 5 exact inputs, valid paraphrases and real constituent diagnostic meaning. All current free-text runtime results remain neutral; do not count abstention as useful clinical grading capability. Missing/failed schema/provider/source conditions preserve reject/no-write versus accepted UNGRADED distinctions.

### Historical evidence and deterministic compatibility

- Historical CORRECT/PARTIAL/INCORRECT free-text with null, unknown, misleading VALIDATED artifact metadata, conflicting method marker or plausible model identity contributes no positive or negative grade evidence, scaffolding counts, prerequisite success, difficulty changes or graded follow-up.
- Include both UNDERSTANDING and RECALL, plus CONNECTION/APPLICATION mappings, mixed eligible/ineligible histories and multiple attempts. Ineligible latest attempt cannot reveal a favorable older attempt as the latest performance; chronology/anti-repetition remain true.
- Positive deterministic MCQ: permitted option, trusted owned validated contract, consistent CORRECT/INCORRECT deterministic result, no AI calls, unchanged outcome/evidence/follow-up behavior. Negative: forged marker, non-MCQ correctOption spoof, mismatching artifact/activity, missing marker, disallowed selected option, recomputation mismatch, PARTIAL marker, stale/foreign/source-revoked contract. No rewriting historical data.
- Invalid enum/receipt/binding fails as data error. Unknown qualification is non-scoring, not an invented incorrect answer.

### Follow-up, replay, refresh and resume

- Origin validation and both direct CORRECT prerequisite checks reject unqualified history; forged basis JSON alone cannot admit an intent. Exercise PENDING, DEFERRED and MATERIALIZED intents and terminal audit retention.
- GET does not change rows, cancel intents or call providers; raw receipt bytes/outcome/feedback stay stable. POST authoritatively cancels ineligible intent under existing safety semantics without graded replacement, false satisfaction or repeated unavailable materialization. An existing linked activity cannot inherit grade authority from a canceled origin.
- Same request/body replay returns exact historical receipt with no evaluation or new attempt/intent/evidence. Mismatching request/body is rejected. GET refresh and Pause/resume/restart use current safe eligibility rather than stored receipt continuation metadata; preserve learner Pause and current accepted no-progress recovery.
- Legitimate deterministic intent creation, defer/restoration, answer satisfaction and materialization continue to work. Unqualified history cannot restore prerequisite understanding or trigger deeper/transfer intent.

### PostgreSQL, authorization and concurrency

- Real PostgreSQL rows for historical free-text/unknown provenance and positive MCQ; compare attempts, receipt JSON, activities, missions and intents before/after. No destructive/backfilled writes. Demonstrate GET is read-only and provider-free.
- Direct `PersistActivityResponse` injection of free-text CORRECT/PARTIAL/INCORRECT, forged MCQ and mismatched next-stage/completion is rejected atomically. Successful legitimate MCQ and neutral free-text commit once. Duplicate request admission, concurrent submit/Continue, double Continue, Pause/Stop/completion races retain snapshot/locking semantics.
- Eligibility/contract/source change between read and final admission aborts/recomputes safely before writes/provider dispatch; preserve existing NOWAIT source locks and canonical ordering. Prove new eligibility checks do not open a read/write gap or deadlock ordering regression.
- Owner B versus owner A mission/activity/attempt/artifact/intent, stale source versions and CSRF negatives remain denied; retain frozen-source and objective-origin policy. Current valid same-objective and presentation paths remain compatible.

No durable mastery writer was found in this Phase 7 candidate. Assert no new mastery/evidence-event writes and test all current effective competence mappings. Do not add Phase 8 persistence to demonstrate absence. If an authorized data audit later discovers durable contaminated projections, stop and scope their repair separately.

## Validation authorization after owner confirmation

Stage A/B runs **no tests, build, app, database or provider**. At Stage C, the user's instruction authorizes focused backend/PostgreSQL validation; execute changed/new methods first, then the following bounded affected-class set in the **new derivative**, never in the frozen baseline. A failed/stale fixture must be investigated; do not weaken assertions. Exact commands and results, including skips, must be retained.

From derivative `backend`, with all live opt-ins false:

```powershell
$env:HIPPOCAMPUS_LIVE_AI_SMOKE = 'false'
$env:HIPPOCAMPUS_LIVE_AI_GOLDEN = 'false'
$env:HIPPOCAMPUS_LIVE_AI_GOLDEN_ADAPTER = 'false'
.\mvnw.cmd -B -ntp '-Dtest=AttemptEvidenceEligibilityTests,ResponseAttributionValidatorTests,AiResponseEvaluationAdapterTests,SubmitActivityResponseUseCaseTests,StudyMissionLearningStateAssemblerTests,FollowUpV2RegressionTests,P716ContinuationTests,GetStudyMissionUseCaseTests,GradedResponseFollowUpPolicyTests,JsonActivityResponseContractRepositoryTests,StudyMissionApplicationConfigurationTests,ActivityResponseControllerWebTests' test
.\mvnw.cmd -B -ntp '-Dtest=ActivityMaterializationPersistenceIntegrationTests,StudyMissionContinuationOrderingIntegrationTests,FollowUpIntentPersistenceIntegrationTests' test
```

During implementation verify these existing classes' configuration, required infrastructure and newly added method targets. The exact approved PostgreSQL setup/connection command is not established by Stage A/B; retain operator-managed PostgreSQL/Testcontainers prerequisites and do not automatically start/download infrastructure under this packet. If local PostgreSQL is unavailable, hand these commands to operator/CI; infrastructure failure or skipped tests is not a pass. Any different setup command requires explicit authorization. Do not run unrelated suites simply because they are available.

Operator/CI broad validation from derivative `backend`:

```powershell
.\mvnw.cmd -B -ntp clean verify
```

From derivative `frontend` (impact-based revalidation of the full mission contract):

```powershell
npm.cmd run lint
npm.cmd run typecheck
npm.cmd test
npm.cmd run build
npm.cmd run test:e2e -- e2e/study-mission.spec.ts
```

Broader startup/browser/database orchestration remains operator/CI-controlled. Preserve before/after source identity, full exit codes, test totals/failures/errors/skips and raw logs. Baseline frontend PASS (55/289/28) and prior focused backend PASS remain authentic historical evidence; they do not validate newly changed eligibility behavior. No live-provider test is authorized, and historical two-pass collectors are not to be transplanted.

## Independent review and closure handoff

After required execution evidence and exact derivative identity exist, commission a fresh independent general implementation reviewer with actual diff, approved ADR/SOT, original/fresh findings and concise results. After no blocking general finding remains, commission a separate independent `hippocampus-security-vulnerability-review` context. The implementer cannot self-approve. Reviewers must challenge helper authority, raw-grade/intent bypasses, all read/write entry points, positive MCQ, unknown identity, immutable replay and concurrency.

The security reviewer decides closure only against independently supported criteria. Record explicitly whether the verdict is scoped containment/eligibility PASS and which original graded-capability requirements remain blocked. Keep SEC-004 High until that independent gate supports an explicit disposition; do not silently downgrade or mark it closed from tests, ADR acceptance or safe UNGRADED behavior.

Medical qualification remains separate: preserve P7-09 packet v1, the baseline nine, Case 3/5 expert adjudication, held-out attacks, provider feasibility/availability and original human disagreements. Numerical proposals in that packet are not approved by ADR-0018. A later graded capability needs its own approved basis and identity-bound real-output medical qualification. P7-14 still needs all P7 dependencies, actual end-to-end journey/source validation/Golden checks and independent final gate evidence.

## Stop conditions and publication

Stop before implementation without explicit owner approval of ADR-0018 and this packet. After approval, stop for a required schema change, genuine provenance exception, new medical grading authority, contradictory accepted policy, necessary wider module/Phase 8 changes, missing authorized infrastructure or a publication request outside scope. Do not interpret silence as approval.

No commit, push, PR, merge, deployment, grading activation or tracker Done transition is authorized at any stage of this packet. Authorized code work, after approval, remains confined to the new isolated derivative. A deployed-data audit requires a separately identified authorized source and read-only scope; no audit is scheduled or claimed here.
