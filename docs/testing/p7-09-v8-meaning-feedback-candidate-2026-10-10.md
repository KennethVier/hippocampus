# P7-09 V8 meaning and feedback candidate — 2026-10-10

**IMPLEMENTED — USER VALIDATION REQUIRED.** This executes the approved V8 packet
on the existing local P7-09 branch. V8 is an unqualified test candidate. P7-09
remains **Ready for Review**; no commit, push, merge or qualification is granted.

## Change and boundaries

`RESPONSE_EVALUATION_V8` is derived from frozen V7. It instructs evaluation of
demonstrated medical meaning, including unambiguous everyday wording, while
requiring essential medical identities and retaining explicit wrong-name,
structure, function and causal errors. Context interprets stated meaning; it
cannot supply missing learner knowledge. Independent correct assertions,
atomic status semantics, ambiguity/omission distinctions and source boundaries
remain intact. Feedback uses student-friendly English, explains necessary terms
and the actual mechanism before any optional original-gap question, and adapts
depth only to available learner context.

Only version-aware registration, atomic decoding, existing provider schema/LOW
thinking selection, repair-rule selection and test-only qualification selection
include V8. The `AiOutputValidator` addition is necessary so V8 decodes the same
atomic result as V7 rather than the older independent-summary contract.
Schema contents, aggregation, validation rules, repair prompt/rules, provider
settings, budgets, routing approval and Learning Engine authority are unchanged.

V6 remains selected by production `AiResponseEvaluationAdapter` and is the
runner default. Explicitly setting the test-only
`HIPPOCAMPUS_LIVE_AI_GOLDEN_RESPONSE_PROMPT=RESPONSE_EVALUATION_V8` selects V8;
unknown/incompatible identities fail closed. The existing qualification identity
mechanism records that prompt and recomputes current code/config/resource hashes.
V6/V7 review evidence cannot approve V8. No new fingerprint is fabricated here.

Frozen template-content SHA-256 (UTF-8 Java text blocks):

- V6 unchanged: `84717380a4a3d5464069132fb2663e41c456c2a900f052a2f4cd9bd7e0bcf528`
- V7 unchanged: `38a3dd48382bd7ad11d1542fa118f6e5d156eae05a986eae2ad79d49fa20df6c`
- V8 candidate: `5f862c32ad7a3e95cec91fc804bbd3b8bf32e8f13ddd6d490fa0cfaad8a482bc`

Historical Golden v2/v4/v5 inputs, frozen V6/V7 reports and human-review evidence
are preserved. No dataset/resource version or deterministic semantic matcher
changed. Original historical **7 PASS / 2 FAIL** judgments and unresolved
independent adjudication remain unchanged. V7's recorded validation and review
evidence does not validate or qualify this new V8 identity.

## Changed files for V8

Paths below are relative to `backend/src/` unless prefixed with `docs/`:

- `main/java/com/hippocampus/ai/application/prompt/PromptId.java`
- `main/java/com/hippocampus/ai/application/prompt/PromptTemplateRegistry.java`
- `main/java/com/hippocampus/ai/application/validation/AiOutputValidator.java`
- `main/java/com/hippocampus/ai/infrastructure/provider/ProviderStructuredOutputSchema.java`
- `main/java/com/hippocampus/ai/infrastructure/provider/gemini/GeminiProviderAdapter.java`
- `test/java/com/hippocampus/ai/application/prompt/PromptTemplateRegistryTests.java`
- `test/java/com/hippocampus/ai/application/validation/AiOutputValidatorTests.java`
- `test/java/com/hippocampus/ai/infrastructure/provider/gemini/GeminiProviderAdapterTests.java`
- `test/java/com/hippocampus/ai/infrastructure/learning/AiResponseEvaluationAdapterTests.java`
- `test/java/com/hippocampus/ai/evaluation/GoldenAiLiveEvaluationRunner.java`
- `test/java/com/hippocampus/ai/evaluation/GoldenAiLiveEvaluationRunnerTests.java`
- `docs/IMPLEMENTATION-TRACKER.md`
- `docs/testing/p7-09-v8-meaning-feedback-candidate-2026-10-10.md`

The production adapter remains unchanged; its existing fake-orchestrator test is
extended to check that V8 registration leaves the production request at V6.

## Deterministic coverage and validation

Ten curated fake-output cases cover correct plain-English and equivalent
technical wording, missing required nerve identity, explicitly wrong nerve
identity with independent correct reasoning, correct identification with wrong
mechanism, empty/off-topic/uncertain/injection cases and genuinely ambiguous
meaning. Their expected atomic judgments are explicit fixture data, not a new
semantic evaluator. Tests assert aggregation, field isolation, feedback transport,
source preservation and optional questions without a new follow-up field or
learning action. Prompt tests assert meaning/feedback guidance and preservation
of frozen content, atomic rules, output contract and repair/provider schemas.
Further tests cover exact selection, V6 defaults, stale approval identity,
schema rejection and bounded repair success/failure.

Fake-provider tests establish deterministic contract behavior, **not actual
Gemini semantic accuracy or generated teaching quality**. No live provider,
LLM judge, external infrastructure, new dependency or application startup is used.

From `backend`, run the focused selection below. It contains 12 changed methods
and **27 expected test invocations**, including parameterized prompt/fixture cases:

```powershell
$focusedTests = 'PromptTemplateRegistryTests#registersOnlyUniqueApprovedPromptIdentities+responseEvaluationV8PreservesFrozenPromptsAtomicContractAndRepairCompatibility+responseEvaluationV8GuidesMeaningBasedGradingAndStudentFriendlyFeedback+versionedTemplatesMatchStableSha256Snapshots,AiOutputValidatorTests#responseEvaluationAtomicPromptsReturnDeterministicLegacyResult+responseEvaluationV7RejectsInvalidCoverageAndNewFollowUpFields,GeminiProviderAdapterTests#usesLowThinkingOnlyForCurrentAtomicResponseEvaluationPrompts,GoldenAiLiveEvaluationRunnerTests#v8SelectionAndQualificationIdentityAreExplicitAndCannotReuseEarlierApproval+v8CuratedFakeOutputsPreserveAtomicRecognitionAndSafeguards+v7SelectionIsExplicitAndRejectsUnknownOrIncompatibleIdentities+v7UsesBoundedRepairAndRevalidatesOriginalAtomicContract,AiResponseEvaluationAdapterTests#usesResponseEvaluationV6Prompt'
.\mvnw.cmd "-Dtest=$focusedTests" test
```

Agent attempts:

- `mvn.cmd -o "-Dtest=$focusedTests" test`, with the selection above excluding
  `AiResponseEvaluationAdapterTests#usesResponseEvaluationV6Prompt` (26 expected
  invocations), stopped before compilation/tests: Spring Boot parent POM `4.1.1`
  is absent from the local cache.
- `mvn.cmd -o '-Dtest=AiResponseEvaluationAdapterTests#usesResponseEvaluationV6Prompt' test`
  stopped before compilation/tests for the same missing parent POM.
- **0 tests executed; no V8 test PASS claimed.** No broad validation, dependency
  installation, paid/live generation or provider qualification was performed.
- `git diff --check` passed; this handoff is present as an untracked working-tree
  file and must be included in any later user-approved commit.

Required user validation, from repository root after the focused command:

```powershell
node scripts/validation/validate.mjs backend
```

## Remaining gates

User/CI validation, external independent code review, independent security review,
new complete identity-bound V8 provider evidence and independent human semantic
review/adjudication under ADR-0011, and applicable release acceptance remain
outstanding. Retained V6/V7 evidence is not relabeled or overwritten. No paid/live
run is authorized by this implementation packet, and no evaluation-approved
routing, P7-09 Done status or PR #228 merge approval is asserted.
