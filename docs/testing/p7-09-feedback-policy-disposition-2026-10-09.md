# P7-09 feedback-policy disposition and focused quality audit — 2026-10-09

Documentation only. P7-09 remains **Ready for Review**. This records the user's
approved product-owner direction and executor artifact inspection; it is not
independent adjudication, Golden PASS, general review, security review or Done.

## Evidence and authority

Read scope: P7-09 tracker entry; Document 11 §§13A, 18, 25; Document 15 §§24–26,
31–34, 51–53, 63–67; accepted ADR-0011; loader-resolved Golden v5 (v2 base,
v4 alternatives, v5 input overlay); and the
[anonymized reconciliation](p7-09-human-evaluation-reconciliation-2026-10-09.md).
The contract assessment additionally inspects only the existing V6 prompt,
response schema/result/action definitions, aggregator and submission use case.

Frozen evidence is the reconciliation's run
`d28c8b78-7caa-4cac-a15b-9bd1fa4c4c5a`, GEMINI / `gemini-3.1-pro-preview`,
prompt `RESPONSE_EVALUATION_V6`, qualification fingerprint
`cd95c795b64a7306e5d571511a876e813febc0331fcde38a1e2f57afe92bd345`.
The inspected artifact is the ignored local
`backend/ai-golden-evaluation/retained/d28c8b78-7caa-4cac-a15b-9bd1fa4c4c5a/gemini.frozen.json`,
`cases[].validatedStructuredOutput`. No new hashes, execution results or current
deployment identity are asserted by this audit.

## Product-policy disposition, separate from reviewer decisions

The product owner approves retaining **PARTIAL** for both cases:

- Case 4: credit the independently correct radial-nerve/wrist-drop identification;
  explicitly flag and correct the wrong flexor-activation mechanism. Correct
  identification must not imply correct causal reasoning.
- Case 7: credit genuine limited knowledge; identify the missing nerve name and
  causal relationship without inventing a misconception from uncertainty.

Feedback should recognize demonstrated knowledge, clarify the error or gap,
explain the correct causal mechanism accessibly, and offer one focused
understanding-check question when appropriate. This is a context-sensitive
approach, not a mandatory four-part template or a question after every answer.
Avoid superficial praise and unsupported claims that the student now understands.
Preserve anatomical precision: prefer “the wrist extensor muscles lose their
normal nerve supply” to “the wrist loses nerve supply,” where supported by the
authorized source. Do not insert literal denervation into synthetic v5 evidence
as a new required fact; v5 requires the source-supported functional-loss link.

Any follow-up must address the original gap within the same learning objective
and authorized source scope. It must not add unrelated concepts, automatically
create an activity, count as demonstrated understanding, or bypass Learning
Engine sequencing.

This resolves the product owner's grading preference, **not** ADR-0011's
independent disagreement gate. The original reconciliation is unchanged,
including all nine decisions and both FAIL judgments/rationales verbatim as
recorded there. The privately held original review remains the original source;
this audit neither reconstructs nor replaces its wording. Case 4's preference
for INCORRECT because of the wrong mechanism and Case 7's preference for
INCORRECT because of missing knowledge remain independent reviewer decisions.
The original total remains **7 PASS / 2 FAIL**. No re-rating is inferred.

## Frozen feedback findings

### Case 4 — `P7-09-WRONG-REASONING-001`

Output identity from the reconciliation:
`a39db845e3b1ce69e5f8bc12bec6eff633072e9a836af18ff260d92ef549e41c`.

Exact frozen feedback:

> You correctly identified the likely injury as a radial nerve injury and the resulting condition as wrist drop. However, your mechanism is incorrect. A radial nerve injury does not cause wrist drop by activating wrist flexors to overpower normal extensors. Instead, the radial nerve supplies the wrist extensors, so an injury eliminates wrist extension, resulting in wrist drop.

**Rubric compliance:** PARTIAL is the v5 allowed classification. Correct
identification is preserved; `missingConcepts` separately records supply,
functional loss and wrist-drop relationships; `misconceptions` records the actual
flexor-activation claim. Feedback explicitly rejects that claim and supplies the
source-supported causal correction. No classification or field-isolation defect
is established. This executor finding is not human semantic PASS.

**Educational quality:** the first three steps of the product direction are
present, with specific recognition rather than empty praise. The correction is
clear but repeats the false mechanism and compresses the muscle-function link
into “an injury eliminates wrist extension.” Explaining wrist extensors as the
muscles that lift the wrist would make the cause easier to follow. It names the
correct anatomical target; it does not use the vague “wrist loses nerve supply.”
There is no understanding-check question. One could be useful for this precise
mechanism error, but absence alone is not a v5 failure or breach of the
conditional “when appropriate” direction. There is no claim of achieved mastery.

An optional source-bounded check: “How does losing the ability to extend the
wrist explain wrist drop?” It targets the original functional-loss gap, not a
different nerve, lesion site or clinical topic.

### Case 7 — `P7-09-UNCERTAIN-001`

Output identity from the reconciliation:
`a71190d6ea96fd7ac19cfa8f9511648da168e5d6e03600357dcab7850a5213e0`.

Exact frozen feedback:

> You correctly identified that the injury involves the nerve supplying the muscles that lift the wrist (wrist extensors). To complete the explanation, recall that this is specifically the radial nerve, and injury to it causes a loss of wrist extension, which results in wrist drop.

**Rubric compliance:** v5 allows PARTIAL or UNCERTAIN; product direction retains
PARTIAL for this demonstrated constituent. `correctConcepts` credits the stated
nerve-to-muscles relationship; `missingConcepts` identifies the radial nerve and
loss-of-extension/wrist-drop link; `misconceptions` is empty. No invented
misconception or grading defect is established. This is not human semantic PASS.

**Educational quality:** concise, relevant, anatomically precise and already
explains “wrist extensors” in familiar language. It identifies the two actual
gaps without treating uncertainty as a false belief. “Recall that” supplies the
answer rather than checking comprehension; the causal link could more directly
connect the affected muscles' function to inability to lift the wrist. These
are modest teaching improvements, not established medical or rubric failures.
No focused question is present; one may be appropriate but is not mandatory.
It makes no unsupported claim that the learner now understands.

An optional source-bounded check: “Which nerve supplies the muscles that lift
the wrist?” This directly targets the student's explicitly missing nerve name.

## Focused-question contract assessment

The current V6 contract in
`backend/src/main/java/com/hippocampus/ai/application/prompt/PromptTemplateRegistry.java`
requires learner-grounded support, the smallest actual gap, and only demonstrated
misconceptions. STRICT_SOURCE facts must stay within supplied evidence.
`ProviderStructuredOutputSchema` represents `feedback` as a string;
`ResponseEvaluationResult` requires nonblank text, and
`ResponseEvaluationAggregator` carries that feedback into the result. The
contract neither requires nor prohibits a bounded question in that text.

**Supported within existing feedback:** one optional natural-language question
about the original gap, consistent with Document 11 §18 and Document 15 §34.
This is text, not a separate answerable activity or evidence event. The contract
has no dedicated follow-up-question field or automatic follow-up creation rule.
Structural acceptance cannot verify question relevance or teaching quality;
those require semantic review.

**Existing approved advisory actions:** `RETRY` and `TARGETED_EXPLANATION` can
recommend further work; both frozen outputs use `TARGETED_EXPLANATION`. Neither
action encodes a question payload or orders an understanding check. V6 explicitly
reserves the final educational decision to the Learning Engine.
`SubmitActivityResponseUseCase.execute` calls `learningEngine.decide` with
assembled learner state/outcome; feedback text is not a command that creates
an activity. Document 11 §§13A and 25 likewise reserve any actual Understanding
Check and next activity to existing deterministic engine policies. An interactive
follow-up feature would need separate scoped approval; none is introduced here.

## ADR-0011 gate status and exact remaining actions

| Gate | Evidence-supported state | Remaining action |
| --- | --- | --- |
| Layers 1–2 | Frozen nine-case contract PASS and historical 184-test curated evidence are reported in the reconciliation; current approval identity is not established here. | Independent reviewer must reconcile retained commands, counts, hashes and applicability to the intended release identity; obtain user/CI evidence for any uncovered or changed scope. |
| Layer 3 | Complete nine-output frozen v5 run is retained; collection PASS is distinct from qualification. | Verify effective configuration/resource/code identity and required diagnostics, including explicit unavailable telemetry and model-alias limitations. Collect a new complete run only if identity is stale or material inputs change; preserve unsuccessful evidence. |
| Independent designation/attestation | Reviewer-reported independence exists; qualification attestation, signature and date/time fields are incomplete. No genuine completed designation/attestation is established. | Coordinator must privately retain identity mapping, designate an independent human AI/QA reviewer separate from the executor, and obtain attributable review date, rubric, reasons, artifact identities and genuine attestation. Do not manufacture credentials or signatures. |
| Independent adjudication | Not satisfied; two preserved required-case FAILs remain unresolved despite product-policy disposition. | Designate an independent human adjudicator to compare exact outputs, learner/source evidence and v5 rubric with the approved policy; record reasoned disposition of both disagreements while retaining original FAILs. Determine whether any disputed/high-impact medical claim requires medically qualified review; obtain it if applicable. |
| Layer 4 / Golden qualification | Not satisfied. Earlier PASS records cannot erase this review's FAILs. Frozen PENDING artifacts are unchanged; unresolved semantic FAILs imply qualification FAIL when applied. | Obtain attributable final human semantic decisions for all nine exact current outputs, resolve every FAIL/PENDING through recorded adjudication, and produce a separate identity-bound qualification artifact using the existing supported workflow with current curated and applicable release acceptance evidence. |
| Release-owner acceptance | Not established by the product-owner grading decision or historical closeout. | Release owner must explicitly accept applicable Document 15 §§65–66 correctness, grounding, isolation, citation, schema/task targets, latency/resource limits and documented limitations against retained evidence. Resolve material sampling/benchmark/acceptance questions through Document 27; do not invent thresholds or infer stability from one run. Qualify any candidate/fallback configuration separately before an explicit ADR-0008 approval transition. |
| General/security/completion | Unrecorded in current tracker; this audit grants neither pass. | Fresh external general implementation/evidence review must inspect actual applicable diff and user/CI evidence; then independent security review. Only after required qualification, acceptance and completion evidence may human-authorized publication/merge and tracker closeout proceed. |

## Production correction decision and separate optional packet

**No production grading, schema or activity-behavior correction is demonstrated
as necessary by these two outputs.** Their classifications and substantive
corrections fit the approved direction. Modest explanation improvements and an
optional focused question are warranted candidates for feedback tuning, not a
reason to rerun until a preferred wording appears or change Golden outcomes.

If the owner separately approves tuning, the minimal packet is: adjust only the
response-evaluation feedback instruction in a newly versioned prompt to encourage
natural recognition, explicit gap correction, a concise source-supported
muscle-function explanation, and at most one appropriate original-gap question;
preserve atomic judgments, aggregation, source scope, schema, action enum and
Learning Engine behavior. Add focused feedback regression coverage for Cases 4
and 7 and safeguards against invented misconceptions, unrelated questions,
rigid praise/templates and claims of achieved understanding. Do not add a
denervation requirement to v5 or weaken its field-specific safeguards.

An output-affecting prompt change requires relevant contract/curated regressions,
a new identity-bound complete nine-case live qualification set, fresh independent
human review/adjudication and applicable release-owner acceptance under ADR-0011
and Document 15 §§63–64, plus general/security review of the separate change.
The same duty applies to any affected repair prompt/configuration. No such
production change, provider call, test execution or approval is made here.

For this documentation change, user review should compare this audit with the
frozen Case 4/7 text and existing reconciliation, confirm the latter's original
decisions remain untouched, and confirm P7-09 is still Ready for Review. No broad
application test is needed solely for this documentation disposition.
