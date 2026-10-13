# P7-09 offline adapter readiness — 2026-10-13

Status: **OFFLINE READINESS REVIEW APPROVED; GOLDEN QUALIFICATION PENDING**.

The historical `GoldenAiLiveEvaluationRunner` still invokes the legacy single-pass
`ResponseEvaluationInput`/`ResponseEvaluationResult` path. The new test-only
`GoldenResponseAdapterQualificationTests` instead loads all nine Golden v6 case
inputs, supplies authorized synthetic source chunks, and invokes the production
`AiResponseEvaluationAdapter` with fixture discovery and verification results.
It asserts the two current prompt IDs, the response and source passed to each
request, the verified aggregate, and key positive and negative attribution.
No provider is constructed or called. This exercises adapter behavior, not
provider generation, prompt reliability, human semantic review, or live routing.

## Offline identity

- Dataset inputs: v2 response cases SHA-256 `74BC2CFCF07D0F0D2EC1360872786C455AF75BE2632E65DD692812D30E57E2DB`; v4 rubric overlay `567D91CCF0C2102E7C98C81DD2EDFA3C9961D0307949C53E5616609CC9FDABB0`; v5 input overlay `DFA6AAAE72F48619BEBD757A6990C18E2B6FB32955216CB92AFF70EFB045071E`; v6 rubric overlay `AD9C339DCC43B36D71C6DEF41778810DA88AF66651F0DABE5597F59D1B553317`.
- Production adapter SHA-256 `C367A9947EBD83AEA70F7EEBA8695A6BF1EE54D227ED172F9F6054154F756EC9` at preparation time. The test uses `RESPONSE_EVALUATION_DISCOVERY_V1` and `RESPONSE_EVALUATION_VERIFICATION_V1` with synthetic `STRICT_SOURCE` evidence, fixed source version `00000000-0000-0000-0000-000000000709`, and no provider/model identity.
- Test file: `backend/src/test/java/com/hippocampus/ai/evaluation/GoldenResponseAdapterQualificationTests.java`, current SHA-256 `75EEEC85DC6BC6EC2134B5F63BD38C012B791F2988D645F8944348832B6AC572` (refreshed 2026-10-14). Rehash it and the production sources after any edit before treating this identity as frozen.

## Case expectations and execution state

| Case | Fixture expectation | Executed result |
| --- | --- | --- |
| 1 CORRECT | CORRECT, no missing concept or misconception | PASS |
| 2 PARAPHRASE | CORRECT with paraphrased independent assertions | PASS |
| 3 PARTIAL | PARTIAL for wrist-extensor denervation only; nerve identity and effect missing | PASS |
| 4 WRONG-REASONING | PARTIAL for radial-nerve identification and wrist drop; flexor explanation corrected | PASS |
| 5 INCORRECT | PARTIAL for wrist-extensor paralysis; median-nerve attribution corrected | PASS |
| 6 OFFTOPIC | UNCERTAIN, no credit or invented medical misconception | PASS |
| 7 UNCERTAIN | PARTIAL for wrist-lifting muscle relation, no inferred radial nerve | PASS |
| 8 EMPTY | INCORRECT, no credit or invented misconception | PASS |
| 9 INJECTION | UNCERTAIN, no credit or invented misconception | PASS |

The Case 3 expectation applies ADR-0012 attribution to the existing Golden
input. It does **not** assert that the inherited v6 semantic rubric passes.
The proposed Golden dataset v7 amendment remains separate. The project owner
approved the prospective Case 3 criteria on 2026-10-14; medically qualified
review remains pending. Golden v6, legacy runner, and historical
reports were not changed.

## Validation evidence

- Focused offline: `cd backend; mvn -o -q -Dtest=GoldenResponseAdapterQualificationTests#allNineGoldenCasesUseVerifiedAttributionWithoutBorrowedCredit test` — **PASS**, 1 test method asserting all nine cases, 0 failures/errors/skips. The user confirmed this result on 2026-10-14.
- Full deterministic: `node scripts/validation/validate.mjs backend` — **PASS**, user confirmed on 2026-10-14. Its `git-diff-check` also **PASS**. This record retains the user's concise pass evidence; it does not claim a new agent-run broad validation or attach unavailable detailed logs.
- Earlier restricted-sandbox attempt: the Maven wrapper could not download its distribution (`Permission denied: connect`) and stopped before compilation. That environment failure was superseded by the successful user-run validation; it was not a failing case or suite result.

Case 4 initially returned `UNCERTAIN` because the v5 input overlay defines
three expected concepts while its synthetic discovery and verification fixture
covered only indexes 0–1. The production validator correctly rejected it.
The fixture now covers indexes 0–2, links the wrist-drop constituent to index 2,
and verifies exact assertion spans, fidelity, source support, and both
misconceptions before invoking the adapter. No production behavior changed.
The user approved the offline qualification readiness review on 2026-10-14.
This approval covers the synthetic offline harness and its validation, not
Golden rubric v7, live provider behavior, independent human semantic review,
evaluation-approved routing, or P7-09 completion.

## Proposed Golden dataset v7 Case 3 decision — owner approved, medical pending

The existing `P7-09-PARTIAL-001` learner response is exactly “It denervates
the wrist extensors.” The question supplies “radial nerve injury”; the learner
does not independently name the radial nerve or state the downstream movement
loss. The authorized synthetic source is reference
`00000000-0000-0000-0000-000000000303`: “The radial nerve supplies wrist
extensors. Radial nerve injury can eliminate wrist extension and produce wrist
drop.” This source supports the medical correction, but its facts are not
student assertions.

| Criterion | Current resolved Golden v6 | Proposed Golden dataset v7 Case 3 overlay |
| --- | --- | --- |
| Correct-concept credit | The v2 denervation group also contains “wrist extensors lose radial nerve input”; the v4 alternatives add “radial nerve innervates the wrist extensors injury causes denervation”, “radial nerve innervates the wrist extensors”, and “radial nerve supplies the wrist extensors”. These can treat question/source wording as learner credit. | Credit only independently stated wrist-extensor denervation: “denervates the wrist extensors”, “denervation of wrist extensors”, or “loss of input to wrist extensors”. No credited correct concept may identify the radial nerve from “It”. |
| Missing concepts | The resolved rubric explicitly requires the movement consequence; it does not explicitly require the omitted radial-nerve supply/identity relation as a separate missing group. | Require both the unexpressed radial-nerve-to-wrist-extensor supply/identity relation and the unexpressed loss-of-wrist-extension-to-wrist-drop consequence in structured `missingConcepts` and corrective `feedback`. |
| Outcome and misconceptions | `PARTIAL` only; no misconception required. | `PARTIAL` only for verified denervation credit; no invented misconception. The source-backed nerve and movement links are taught as missing, not credited as student knowledge. |

The project owner approved these prospective criteria on 2026-10-14. An
appropriately medically qualified reviewer must still approve or amend the
exact Case 3 interpretation and unchanged Case 5 PARTIAL/misconception
standard. See the [prospective approval record](p7-09-golden-v7-case-3-rubric-proposal-2026-10-11.md)
and [targeted medical sign-off packet](p7-09-golden-v7-case-3-case-5-medical-signoff-2026-10-14.md).
Until that medical approval is established, do not create or apply a v7
overlay. Golden v6 and historical reports remain unchanged.

## Subsequent live run preparation

First obtain the targeted medical approval and implement the Case 3 Golden dataset v7
additive overlay with focused deterministic regression and a new dataset
identity. Complete any outstanding independent general/security gate for the
current adapter. Freeze and record repository revision and hashes for adapter, validator,
aggregator, discovery/verification prompts, schema/repair path, dataset,
authorized source chunks, routing/configuration, provider/model, generation
settings, and budgets. Prepare a **separate** test-only current-adapter live
collector for exactly one nine-case run: the legacy runner is insufficient.
Its report must retain each pass's actual output, source references, contract
and repair result, sanitized failure diagnostics, available finish/usage/latency
metadata, and run/case identity. Obtain explicit authorization for a single
quota-bounded live run, including provider/model, request/token ceiling, delay,
and cost ceiling, before any Gemini call or evaluation-approved routing. Keep
semantic review and qualification `PENDING` until attributable independent
human review of all nine current outputs and required medical adjudication.
