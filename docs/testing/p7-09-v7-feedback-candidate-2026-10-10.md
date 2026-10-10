# P7-09 V7 feedback candidate — 2026-10-10

**IMPLEMENTED — USER VALIDATION PASSED; V7 QUALIFICATION PENDING.** Product owner
authorized this specific tuning before final P7-09 acceptance. This executor
handoff records user-supplied local validation evidence. The user reports that
external implementation review passed without identified blocking code defects;
this document does not grant independent security review, release acceptance,
semantic approval or Golden qualification.
P7-09 remains **Ready for Review**.

V7 retains the V6 task contract except its version header and feedback wording.
It encourages natural recognition of independent correct knowledge, correction
of the actual wrong explanation or missing information, an accessible accurate
causal connection, and at most one optional source-bounded question about the
original gap. It discourages invented misconceptions, compulsory headings,
superficial praise and claims of learning from reading a correction.

## Changed files

Paths below are relative to `backend/src/` unless prefixed with `docs/`:

- `main/java/com/hippocampus/ai/application/prompt/PromptId.java`
- `main/java/com/hippocampus/ai/application/prompt/PromptTemplateRegistry.java`
- `main/java/com/hippocampus/ai/application/validation/AiOutputValidator.java`
- `main/java/com/hippocampus/ai/infrastructure/provider/ProviderStructuredOutputSchema.java`
- `main/java/com/hippocampus/ai/infrastructure/provider/gemini/GeminiProviderAdapter.java`
- `test/java/com/hippocampus/ai/application/prompt/PromptTemplateRegistryTests.java`
- `test/java/com/hippocampus/ai/application/validation/AiOutputValidatorTests.java`
- `test/java/com/hippocampus/ai/infrastructure/provider/gemini/GeminiProviderAdapterTests.java`
- `test/java/com/hippocampus/ai/evaluation/GoldenAiLiveEvaluationRunner.java`
- `test/java/com/hippocampus/ai/evaluation/GoldenAiLiveEvaluationRunnerTests.java`
- `test/java/com/hippocampus/ai/evaluation/GoldenAiQualification.java`
- `docs/IMPLEMENTATION-TRACKER.md`
- `docs/testing/p7-09-v7-feedback-candidate-2026-10-10.md`

## Selection and identity

Production `AiResponseEvaluationAdapter` still selects V6. The test-only runner
defaults to V6; `HIPPOCAMPUS_LIVE_AI_GOLDEN_RESPONSE_PROMPT` accepts the exact
identity `RESPONSE_EVALUATION_V6` or `RESPONSE_EVALUATION_V7`, failing closed for
other values. Direct deterministic tests select V7 through the explicit request
overload. No runtime configuration or evaluation-approved task set is changed.

The runner records the selected prompt in its existing qualification identity.
Retained report review recomputes identity using that report's selected prompt.
Current V6 runs remain available; frozen V6 evidence remains tied to its original
revision and fingerprint, not silently rebound to changed repository hashes.

Template-content SHA-256 (UTF-8 Java text-block content):

- V6 preserved: `84717380a4a3d5464069132fb2663e41c456c2a900f052a2f4cd9bd7e0bcf528`
- V7 candidate: `38a3dd48382bd7ad11d1542fa118f6e5d156eae05a986eae2ad79d49fa20df6c`

No new dataset/resource version: loader-resolved v5 retains its v2 base, v4
rubric overrides and v5 input overlay verbatim. No provider/model selection,
generation settings, budgets, repair prompt or repair rules changed. V7 is
explicitly included in the existing atomic decoder/schema, Gemini LOW-thinking
selection and version-aware repair-rule selection. V6 remains compatible.

The new run fingerprint must be collected from the intended checked-out code
and effective configuration; none is fabricated here. V6 approval cannot qualify
V7. Historical frozen outputs, hashes and original **7 PASS / 2 FAIL** decisions
are unchanged. The new prompt does not resolve historical human disagreements.

## Focused validation handoff

Run from `backend`. This exact selection contains 12 methods / 15 expected test
invocations (one method has four prompt-version parameters). All provider calls
are fake or mocked; no live LLM, infrastructure or application startup is used.

```powershell
$focusedTests = 'PromptTemplateRegistryTests#responseEvaluationV7ChangesOnlyFeedbackAndVersionHeader+responseEvaluationV7KeepsRepairAndProviderSchemasIdenticalToV6+responseEvaluationV7GuidesAdaptiveSourceBoundFeedbackWithoutNewAuthority+versionedTemplatesMatchStableSha256Snapshots,AiOutputValidatorTests#responseEvaluationAtomicPromptsReturnDeterministicLegacyResult+responseEvaluationV7RejectsInvalidCoverageAndNewFollowUpFields,GeminiProviderAdapterTests#usesLowThinkingOnlyForCurrentAtomicResponseEvaluationPrompts,GoldenAiLiveEvaluationRunnerTests#v7SelectionIsExplicitAndRejectsUnknownOrIncompatibleIdentities+v7QualificationIdentityCannotReuseV6Review+v5IndexedMechanismReachesV7PromptWithUnchangedEvidenceAndInjectionBoundary+v7FakeProviderPreservesWrongMechanismVersusMissingKnowledgeAndOptionalFeedbackQuestion+v7UsesBoundedRepairAndRevalidatesOriginalAtomicContract'
.\mvnw.cmd "-Dtest=$focusedTests" test
```

Final local validation results supplied by the user:

- Focused V7 validation using the selection above: **15 tests, 0 failures,
  0 errors, BUILD SUCCESS**.
- Registry regression test after correcting the stale explicit expected identity
  list: **1 test, 0 failures, 0 errors, BUILD SUCCESS**. From `backend`:

  ```powershell
  .\mvnw.cmd '-Dtest=PromptTemplateRegistryTests#registersOnlyUniqueApprovedPromptIdentities' test
  ```

- Final repository-root `node scripts/validation/validate.mjs backend`:
  **backend-clean-verify PASS, git-diff-check PASS, overall VERDICT PASS**.

Earlier restricted-environment agent attempts used the focused selection above:

- `.\mvnw.cmd "-Dtest=$focusedTests" test`: blocked before Maven/test execution;
  wrapper distribution download received `Permission denied: connect`.
- `mvn.cmd -o "-Dtest=$focusedTests" test`: blocked before compilation/tests;
  Spring Boot parent POM `4.1.1` is absent from the local cache.
- Those restricted-environment attempts executed **0 tests**. No dependency
  installation or further provider calls were made. The earlier restricted
  Maven failures and initial local integration-test failures remain historical
  attempts, not the final validation outcome. The final user-supplied results
  above supersede the earlier validation blocker. `git diff --check` passed.

Added coverage checks feedback instructions, V6 hash/unchanged contract, V7
selection and identity invalidation, identical schema/repair rules, atomic
decoding/aggregation, rejection of duplicate indexes/new follow-up fields,
bounded successful/failed repair, source-context preservation and the existing
student-input injection boundary. Case 4/7 fake fixtures exercise PARTIAL with
actual wrong-mechanism evidence versus missing knowledge without misconceptions,
and optional questions remaining inside feedback. Fake fixtures do not prove
generated teaching quality or human semantic PASS.

Completed user-owned repository-root validation commands:

```powershell
node scripts/validation/validate.mjs backend
git diff --check
```

Preserve existing V6 regression evidence. The final user-owned backend validation
passed, and external implementation review identified no blocking code defects.
These results do not establish V7 provider qualification or resolve independent
human adjudication.

## Later user-controlled qualification

Only after focused validation and independent code review, follow the existing
[layered live evidence guide](p7-09-layered-live-evidence.md) with provisioned
provider/model credentials and intended effective configuration. From `backend`,
for a new complete nine-case V7 evidence set:

```powershell
$env:HIPPOCAMPUS_LIVE_AI_GOLDEN = 'true'
$env:HIPPOCAMPUS_LIVE_AI_GOLDEN_PROVIDER = 'gemini'
$env:HIPPOCAMPUS_LIVE_AI_GOLDEN_TASK = 'response-evaluation'
$env:HIPPOCAMPUS_LIVE_AI_GOLDEN_RESPONSE_PROMPT = 'RESPONSE_EVALUATION_V7'
Remove-Item Env:HIPPOCAMPUS_LIVE_AI_GOLDEN_CASE -ErrorAction SilentlyContinue
Remove-Item Env:HIPPOCAMPUS_LIVE_AI_GOLDEN_REVIEW_FILE -ErrorAction SilentlyContinue
Remove-Item Env:HIPPOCAMPUS_LIVE_AI_GOLDEN_REVIEW_REPORT -ErrorAction SilentlyContinue
.\mvnw.cmd '-Dtest=GoldenAiLiveEvaluationRunner#runsP7GoldenEvaluationWhenExplicitlyEnabled' test
```

Explicitly set the response prompt to `RESPONSE_EVALUATION_V6` for V6 runs.
Retain the new complete code/config/resource identity, provider diagnostics,
validated outputs, failures and human semantic review under ADR-0011. Do not
rerun generations to chase preferred wording. Collection success cannot approve
routing. Independent human semantic review/adjudication, applicable release
acceptance and independent security requirements remain outstanding. External
implementation review passed without identified blocking code defects; no
broader P7-09 completion or Golden approval is inferred.

No public schema, action enum, API, DB, frontend, aggregation, authorization,
source-reference validation, Provider Router or Learning Engine changed. No
authorization/provenance/output-parsing/prompt-injection regression was identified
by diff inspection. Final local validation and external implementation review
are user-reported evidence, not an executor self-approval. Independent security
review, release acceptance, V7 qualification and unresolved independent human
adjudication remain outstanding. No commit, push, merge or Done is recorded.
