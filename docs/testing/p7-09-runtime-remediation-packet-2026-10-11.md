# P7-09 bounded runtime grading remediation — implementation packet

Status: **IMPLEMENTATION-READY PACKET; implementation not started.**
Task: **P7-09 — Implement response-evaluation task end-to-end**. Keep tracker
status **Ready for Review** until required evidence and independent gates pass.

## Goal and authority

Prevent false credit and false failure for open-ended Study Mission responses.
Implement accepted ADR-0012 under Documents 11, 12, 15, 26, 27 and accepted
ADR-0008/0011. Preserve Learning Engine ownership, session/mission ownership,
frozen material versions, and the existing Provider Router. ADR-0013's
follow-up activity lifecycle belongs to **P7-15**, outside this packet.

## Implement

1. **Authorized evidence.** In the learning application, resolve the current
   owned mission/activity, validated response contract, activity provenance,
   and mission's frozen `MissionMaterial` versions before any retrieval,
   ranking, or model call. Extend `ResponseEvaluationPort.Request` with an
   immutable authorized evidence package and grounding mode; pass it through
   `AiResponseEvaluationAdapter` instead of the current synthetic empty
   `GENERAL_KNOWLEDGE` package. Reuse `ActivitySourceReferenceAuthorization`
   and the materials/RAG ports, but add the narrow owner/version-scoped read
   needed to obtain exact authorized chunk text. `SourceReference` metadata
   alone has no text. Check activity source IDs against mission material/version
   and current owner, then check retrieved chunk/version and source text again
   before provider use. Provider output never supplies authorization. Where
   authorized source evidence is insufficient, fail to a safe unresolved
   result; in `GENERAL_KNOWLEDGE` do not claim source-grounded credit without
   an approved verification basis.
2. **Assertion discovery and verification.** Add cohesive AI-domain records
   for assertion, candidate, exact student/source spans, relation, per-item
   disposition, and coverage. Extend the response-evaluation structured
   contract and validator. First evaluation may propose components; a bounded
   independent discovery pass still inventories relevant student assertions
   against every expected concept/relation when that list is empty. Validate
   UTF-16 half-open spans, code-point boundaries, exact text, ownership,
   source version, IDs, and complete indexes before semantic use. A bounded
   verifier through the existing `RESPONSE_EVALUATION` route decides each
   assertion/candidate from authorized evidence without seeing the first
   grade/feedback. Revalidate its output. Never infer student knowledge from
   the question, expected answer, or provider citation. Keep calls
   nonrecursive, one discovery and one verifier pass with existing bounded
   repair, explicit configured caps, and outside persistence transactions.
   Provider timeout, truncation, bad provenance, incomplete coverage, or
   disagreement resolves to uncertainty, not invented credit or failure.
3. **Verified aggregation.** Replace aggregation of free-text
   `supportedComponents` with checked assertion/relation results. Emit
   `CORRECT` only for complete verified essential coverage without material
   misconception, `PARTIAL` for at least one verified meaningful constituent
   plus a gap/error, and `INCORRECT` only after sufficient verification that no
   creditable constituent was stated. Keep a wrong causal/nerve attribution
   and actual missing relation separately; deduplicate equivalent missing
   concepts. Limit feedback to verified claims and explicit limitations.
4. **Non-scoring submission.** Add `UNGRADED` to the submission/attempt outcome
   domain as a non-scoring accepted state; keep AI `UNCERTAIN` distinct. A
   validated unresolved result becomes one real `StudentAttempt` and completed
   activity in `PersistActivityResponse`'s existing short transaction, with
   student text, stable limitation code, and no deterministic result. Use a
   Flyway migration only for fields/constraints actually needed (for example,
   durable limitation code and client request ID); do not invent an evidence
   event or Phase 8 schema. Add owner/activity/request identity for exact
   replay and a unique durable guard. Exact duplicate submission returns the
   confirmed persisted result; mismatched or stale input keeps the conflict
   response. External evaluation failure before a validated result creates no
   attempt. Retry after `UNGRADED` is a new Engine-selected activity, never a
   relabeling or duplicate of the accepted submission.
5. **Learning Engine and API/UI.** Update
   `StudyMissionLearningStateAssembler` to skip `UNGRADED` for evidence and
   graded performance and to reject/surface unknown/corrupt persisted statuses
   instead of its current `INCORRECT` fallback. Make policy handling explicit:
   a non-scoring attempt can lead to bounded support, a new retry activity,
   continuation, or pause under existing safety/source/time/stop/completion
   precedence, without mastery change. Check any `RecentLearningActivity`
   policy that treats null as presentation; do not encode `UNGRADED` as null.
   Extend `SubmitActivityResponseUseCase`, `ActivitySubmissionResponse`, and
   frontend contracts/rendering to show a confirmed non-scoring submission,
   neutral feedback, no false correctness lists, and accurate continuation.
   Refresh/resume must recover the state; preserve existing graded clients and
   stale two-tab conflict behavior. Do not create a manual-review subsystem.

## Focused and adversarial regression evidence

- Changed method-level tests for exact spans (including surrogate pairs),
  complete assertion/concept coverage, paraphrase, negation, false causal
  relationship, duplicate missing concepts, source insufficiency, forged or
  stale/other-user source IDs, injection, disagreement, timeout, cap, repair,
  and fail-closed aggregation.
- Case 5 fixture: first evaluation supplies zero credit; discovery finds
  independently stated wrist-extensor paralysis; source/semantic verification
  yields PARTIAL while retaining wrong median-nerve attribution and missing
  radial-nerve relation. Case 3 fixture: question mentions radial nerve, but
  pronoun-only student wording never earns nerve-identification credit.
- Persistence/API/Engine tests: validated `UNCERTAIN` produces one `UNGRADED`
  attempt and neutral response; no evidence/mastery/failure signal; unknown
  persisted status is an error; duplicate replay is stable; mismatched/stale
  submissions conflict; external failure writes nothing; resume/continue
  remains safe. Include owned/frozen-version source checks and cross-user
  denial. Preserve deterministic MCQ behavior.
- Golden v7 Case 3 rubric correction is a **separate prospective dataset
  change** requiring rubric/medical review; do not edit Golden v6 to make a
  test pass. Neither deterministic regressions nor a second LLM establish
  Golden qualification.

## Likely affected files and responsibilities

- `learning`: `ResponseEvaluationPort`, `SubmitActivityResponseUseCase`,
  `PersistActivityResponse`, `StudyMissionLearningStateAssembler`,
  `AttemptOutcome`, source-evidence resolver/port, `ActivitySubmissionResponse`,
  and their focused tests.
- `ai`: `AiResponseEvaluationAdapter`, response-evaluation input/judgment/
  result records, schema and output validator, `ResponseEvaluationAggregator`,
  bounded orchestration configuration, and their focused tests.
- `materials`/`rag`: narrow authorized exact-text resolution using existing
  source reference, chunk, and evidence-package infrastructure; no new store.
- `progress`/Flyway: `StudentAttempt` and its repository plus only the needed
  additive migration for ungraded limitation and replay identity.
- `frontend/src/features/study-mission`: submission schema/API, interaction
  feedback and continuation rendering, and focused component/API tests.

## Qualification, security, and execution limits

Record configured provider/route and prompt/dataset identities, per-pass
latency, total latency, token/cost usage, repair and uncertainty rates, and
limit outcomes from bounded test and live evidence. Set acceptance thresholds
from measured MVP constraints before routing approval; do not invent a number
in code or this packet. Complete ADR-0011 contract, curated regression, live
provider, and independent human semantic review on a new identity-bound run.
Medically qualified review resolves disputed medical meaning. Maintain Case
3's no-borrowed-credit and Case 5's proportional-credit standard. Apply the
independent Hippocampus security gate after general review, focusing on
source authorization before ranking/model use, cross-user leakage, prompt
injection, raw-text diagnostics, ungraded replay, and failure paths. No
evaluation-approved routing change follows from successful evidence collection
alone.

## Executor command policy and user validation

Agent executor: use `hippocampus-implement-task`; run only eligible changed
test methods under AGENTS.md. Do not run broad suites, live Gemini/Ollama,
Docker, application/browser startup, or validation scripts. Report exact
changed tests run and list user/CI validation commands. Suggested user/CI
commands after implementation, adjusted to the final changed test names:

```powershell
cd backend
.\mvnw.cmd -Dtest=ResponseEvaluationAggregatorTests,SubmitActivityResponseUseCaseTests,StudyMissionLearningStateAssemblerTests,AiResponseEvaluationAdapterTests test
cd ..
node scripts/validation/validate.mjs backend
node scripts/validation/validate.mjs frontend
git diff --check
```

Run source/persistence integration tests, frontend stale/resume tests, and the
complete identity-bound Golden case set through user/CI validation; retain
actual pass/fail evidence. Live provider qualification requires a separately
authorized quota-aware run and independent human review, not an executor
default command.

## Stop and publication boundaries

Stop for a genuine conflict with accepted ADRs or Source-of-Truth, inability
to resolve authorized exact source text for a required grounded task, a need
to award verified source-grounded credit in `GENERAL_KNOWLEDGE` without an
approved basis, or a change that would silently introduce Phase 8 evidence
storage or P7-15 follow-up behavior. Do not change production prompt
selection, create V10, edit Golden v6/historical reports/V9, mark P7-09 Done,
commit, push, merge, or self-approve. Implementation reports use
`IMPLEMENTED — USER VALIDATION REQUIRED` until user/CI evidence arrives.
