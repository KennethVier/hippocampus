# P7-09 Golden dataset v7 implementation — 2026-10-14

**Status:** test-only dataset implemented; live Golden qualification **PENDING**.
Project owner approved the prospective Case 3 rubric on 2026-10-14. The
targeted medical statement marks Cases 3 and 5 APPROVE, but reviewer and
attestation provenance verification remains **PENDING**. Supplied references:
`MED-REV-NEURO-2026-088` and `AUTH-SIG-SHA256-7B93E10C-20261014`.
Neither reference is treated as proof of credentials, signature verification,
or independence.

## Dataset identity

Golden v7 resolves the same nine v2 response inputs and synthetic sources,
then applies the v4 alternatives, v5 Case 4 input contract, v6 Case 5 rubric,
and the additive v7 Case 3 replacement in that order. SHA-256 of each exact
resource file:

| Input | SHA-256 |
| --- | --- |
| `v2/response-evaluation-cases.json` | `74BC2CFCF07D0F0D2EC1360872786C455AF75BE2632E65DD692812D30E57E2DB` |
| `v4/response-evaluation-rubric-overrides.json` | `567D91CCF0C2102E7C98C81DD2EDFA3C9961D0307949C53E5616609CC9FDABB0` |
| `v5/response-evaluation-input-contract.json` | `DFA6AAAE72F48619BEBD757A6990C18E2B6FB32955216CB92AFF70EFB045071E` |
| `v6/response-evaluation-rubric-overrides.json` | `AD9C339DCC43B36D71C6DEF41778810DA88AF66651F0DABE5597F59D1B553317` |
| `v7/response-evaluation-rubric-overrides.json` | `27F113EB5EFECF3F9C2F53F7374D20E43A9D3F4445BF31BDB93DC2A4BC0D55E7` |

Loader SHA-256: `C258B5F19E91167978DD5B180DA0289B2C15EC68E55D9B20AAEB39839D452689`.
Offline adapter test SHA-256:
`C3A265910074D38BB67290F8401AF1D423AD73A601B51814D55DB32DAC2A531E`.
These are dataset and test identity inputs, not a provider/run approval
fingerprint. The historical live runner and its qualification identity remain
single-pass and do not qualify the current two-pass adapter.

## Scope and focused results

Case 3 remains `PARTIAL`: only student-stated wrist-extensor denervation is
credited; radial-nerve identification is missing; radial supply and the
loss-of-extension-to-wrist-drop mechanism are required as missing concepts
and feedback; no misconception is invented. Case 5 is inherited byte-for-byte
from the v6 resolved case: verified wrist-extensor paralysis earns `PARTIAL`,
the median-nerve claim is corrected as a misconception, and the radial-nerve
relation stays missing. The other seven resolved response cases and both
other task families match v6. Default loading remains v5.

Focused offline tests (2026-10-14): v7 loader/rubric PASS; v7 Case 3 and Case 5
semantic matcher PASS; production-adapter harness PASS across all nine v7
cases. The adapter uses authorized synthetic sources and fixture discovery and
verification outputs. This is deterministic behavior evidence, not live
provider reliability or human semantic review.

Full backend deterministic validation on 2026-10-14: `node
scripts/validation/validate.mjs backend` **PASS** (`backend-clean-verify`
PASS; `git-diff-check` PASS; summary `.validation/summary.json`). Its clean
verify reports 12 `GoldenAiDatasetValidationTests`, 60
`GoldenAiSemanticEvaluatorTests`, and 1
`GoldenResponseAdapterQualificationTests`, all with zero failures, errors, or
skips. A separate requested `git diff --check` also exited 0. These results
remain implementation evidence, not Golden semantic/live approval.

Post-validation correction (2026-10-14): the test-only Case 3 feedback
matcher credited a valid wrist-position consequence question to every
feedback group, so it could satisfy v7's radial-nerve supply group without
naming the nerve. Position-question credit is now limited to groups without a
radial-nerve alternative; historical Case 3 groups contain none, so v2–v6
behavior is unchanged. Focused rerun: `GoldenAiSemanticEvaluatorTests` 60/60
PASS. The full backend validation above predates this correction and must be
rerun by the user.

## Two-pass live collector preparation (2026-10-14)

Created test-only live collector `GoldenResponseAdapterLiveCollector` and its test suite `GoldenResponseAdapterLiveCollectorTests` for the current production two-pass `AiResponseEvaluationAdapter`:

The live collector requires an explicit provider, model, positive model-specific input/output token prices, and limits before dispatch. For example, from `backend` with `GEMINI_API_KEY` provisioned:

```powershell
$env:HIPPOCAMPUS_LIVE_AI_GOLDEN_ADAPTER = 'true'
$env:HIPPOCAMPUS_LIVE_AI_GOLDEN_PROVIDER = 'GEMINI'
$env:HIPPOCAMPUS_GEMINI_SMOKE_MODEL = '<explicit model>'
$env:HIPPOCAMPUS_LIVE_AI_COST_PER_MILLION_INPUT = '<verified USD price for that model>'
$env:HIPPOCAMPUS_LIVE_AI_COST_PER_MILLION_OUTPUT = '<verified USD price for that model>'
$env:HIPPOCAMPUS_LIVE_AI_GOLDEN_MAX_REQUESTS = '25'
$env:HIPPOCAMPUS_LIVE_AI_GOLDEN_MAX_TOKENS = '100000'
$env:HIPPOCAMPUS_LIVE_AI_GOLDEN_MAX_COST_USD = '1.00'
.\mvnw.cmd '-Dtest=GoldenResponseAdapterLiveCollector#collectsLiveGoldenV7EvidenceWhenExplicitlyAuthorized' test
```

The configured prices are operator supplied estimates, not verified by the collector. Provider token reporting and post-request billing can exceed an estimated allowance; this is not a mathematically absolute dollar ceiling. No live execution is authorized by this example.
- Exercises discovery pass (`RESPONSE_EVALUATION_DISCOVERY_V1`), verification pass (`RESPONSE_EVALUATION_VERIFICATION_V1`), and `VerifiedResponseEvaluationAggregator`.
- Supports all 9 Golden v7 response-evaluation cases.
- Captures discovery and verification records, validated final evaluations, semantic matcher evaluation, diagnostic metadata, source references, model identity, token usage, latency, and cost estimates.
- Incorporates explicit configurable limits: requests (default 25), tokens (default 100,000), timeout (default 120s), cost USD (default $1.00), and delay pacing (default 0ms).
- Enforces deliberate authorization gate `HIPPOCAMPUS_LIVE_AI_GOLDEN_ADAPTER=true`; never runs automatically.
- Preserves historical single-pass runner (`GoldenAiLiveEvaluationRunner`) and datasets v1-v6.
- Preserves medical-attestation provenance status explicitly marked `PENDING` (`MED-REV-NEURO-2026-088` and `AUTH-SIG-SHA256-7B93E10C-20261014`).
- Focused offline tests (5/5 PASS) verify safety authorization gate, cryptographic qualification identity (`P7-09-two-pass-adapter-v1`), limit enforcement, and synthetic two-pass discovery, verification, and aggregation execution.

## Remaining gates

Verify the authentic medical approval record against both exact criteria and
the supplied references. Keep the reviewer identity/qualification and
attestation evidence access controlled.
Freeze code, prompt, dataset, source, provider/model, routing and budget identity
before any explicitly authorized, quota-bounded complete nine-case live run.
Obtain attributable independent human semantic decisions on those actual outputs
and any required medical adjudication, followed by applicable general/security/release
gates. No Gemini call, routing approval, or P7-09 completion is implied here.

