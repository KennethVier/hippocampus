---
ADR: ADR-0011
Title: Layered P7-09 Response-Evaluation Qualification
Status: ACCEPTED
Date: 2026-10-08
Decision Owners: Project Hippocampus Team
Categories: TESTING, AI, ARCHITECTURE
Affected Documents: 15, 25, 27, IMPLEMENTATION-TRACKER
---

# Context

P7-09 requires nuanced structured response evaluation without false scoring,
correct/partial/alternative/wrong-reasoning tests, and a Golden evaluation pass.
Documents 11 and 15 govern educational behavior; Documents 15 and 25 separate
contract checks, semantic quality evaluation, live provider testing, and human
review. ADR-0008 requires explicit task-specific evaluation approval before
routing a configured provider/model.

Production contract validation proves structural and business-rule correctness,
including expected-index coverage and judgment shape. It does not independently
prove the medical meaning, source support, or learner grounding of free text.
Deterministic curated regressions remain valuable for known semantic cases.

Repeated P7-09 live evidence contains medically and contractually valid outputs
rejected by the closed `WristDropRelations` grammar for harmless paraphrases.
Successive wording adaptations have not established reliable general semantic
coverage. Further surface-form expansion is not a reliable live qualification
strategy, and structural success cannot replace semantic evidence.

# Decision

> P7-09 qualification requires deterministic production-contract validation,
> deterministic curated semantic regressions, and separately recorded semantic
> review of actual provider outputs against the versioned Golden rubric.
> Contract success alone does not constitute Golden semantic approval. The
> closed deterministic grammar is not the sole live semantic authority.

## Layer 1 — Deterministic production-contract validation

Verify, as applicable, structured-schema validity; complete and unique
expected-concept indexes; status-specific judgment shape; aggregation
invariants; source-reference validity; allowed evaluation, certainty, and action
values; field ownership; bounded one-shot repair; fail-closed behavior; and
revalidation of repaired output against the original contract.

Coverage of indexes is not proof that their judgments are semantically correct.
Reference validity is not proof that every claim is source-supported. Field
ownership prevents cross-field substitution; semantic adequacy within each field
also requires Layer 4. Layer 1 does not independently grant semantic approval.

## Layer 2 — Deterministic curated semantic regressions

Use versioned Golden fixtures for correct, partial, alternative-wording,
incorrect, wrong-reasoning, uncertain, off-topic, empty, and injection/adversarial
cases, including field isolation, causal direction, and unsupported/fabricated
misconceptions. Ordinary CI uses deterministic fixtures/fake providers.

`WristDropRelations` and related matchers may remain bounded regression tooling.
An unsupported unseen live grammar form does not by itself establish semantic
failure or success. False positives in deterministic fixtures remain defects;
negative safeguards must stay fail-closed. Do not weaken fixtures merely to make
live wording pass.

## Layer 3 — Live provider qualification

Keep live runs small, explicitly executable, and quota-aware. Retain evidence of
provider compatibility, configured model/route identity, structured-output
reliability, repair usage and result, finish reasons, token/usage and latency
metadata where available, and actual generated output. A targeted debugging run
does not replace the required complete case set.

Record the structural/contract outcome separately from semantic review status.
A successful smoke call or schema validation cannot silently imply semantic
`PASS`. Missing provider telemetry is recorded as unavailable, not invented.

## Evidence-collection execution versus Golden qualification

> The automated live Maven run may succeed when all machine-verifiable
> execution, contract, configuration, and evidence-reporting requirements pass,
> even while required human semantic review remains `PENDING`. Maven success in
> this state means only that live evidence collection completed successfully.
> It does not constitute Golden qualification approval, must retain semantic
> review as `PENDING`, must retain overall qualification as `PENDING`, and must
> not authorize evaluation-approved routing under ADR-0008.

Use distinct architectural concepts: `evidenceCollectionStatus`,
`contractStatus`, `semanticMatcherStatus`, `semanticReviewStatus`, and
`qualificationStatus`. Do not overload one `passed` concept to mean both
execution success and qualification approval. Executable field names are for
the subsequent implementation packet to decide.

Evidence collection may succeed when provider invocation, configuration, and
the requested case/filter are valid; the production response-evaluation
contract validates; bounded repair succeeds if required; and required
diagnostics/report evidence is produced. A deterministic semantic matcher miss
on otherwise contract-valid output remains recorded diagnostic evidence and
does not by itself fail Maven evidence collection.

The Maven evidence-collection run must still fail for objective machine-verifiable
failures: provider invocation failure, invalid configuration, unknown case/filter,
malformed or unrecoverable output, failed production contract validation, invalid
repair, or incomplete required evidence/report generation.

Each required case's semantic review is `PASS`, `FAIL`, or `PENDING`. Without
attributable human review it is `PENDING`. Contract success, live-call success,
matcher success, and repair success cannot infer semantic review `PASS`.

Overall Golden qualification is `PASS` only when all required evidence is
complete and every required case meets this ADR's acceptance requirements.
An unresolved required-case semantic `FAIL` makes qualification `FAIL`; absent
such a failure, an unresolved required-case `PENDING` makes qualification
`PENDING`. Neither permits qualification `PASS`. Objective qualification
failures also prevent approval, independently of review status.

| Evidence collection | Contract | Required semantic review | Overall qualification |
| --- | --- | --- | --- |
| PASS | PASS | PENDING | PENDING |
| PASS | PASS | PASS | PASS only if all other required cases/evidence also pass |
| PASS | PASS | FAIL | FAIL |

These are distinct valid states: even a recorded semantic `FAIL` need not mean
the evidence-collection execution failed. A successful targeted run is evidence
for the selected case, not approval of the complete required set.

Successful Maven execution is not evaluation approval. Neither provider
configuration, smoke success, contract `PASS`, nor successful evidence collection
may independently populate or authorize `evaluation-approved-tasks`. ADR-0008
routing authorization requires the completed layered qualification evidence
defined here and the applicable existing release gates.

## Layer 4 — Human semantic review of actual live output

For current MVP/P7-09 qualification, use explicit human review, not another LLM
judge or provider dependency. A designated independent human AI/QA reviewer,
named in the record and separate from the implementation executor, reviews the
actual output with the learner response, expected concepts/answer, authorized
source evidence, grounding mode, and versioned case rubric. Obtain medically
qualified review for disputed or high-impact medical claims where required by
Documents 15 §51 and 25 §106; medical-student feedback alone is not medical
correctness authority. AI assistance cannot substitute for human sign-off.

Review classification correctness, medically/source-supported reasoning,
acceptance of valid alternative wording, the actual learner gap,
learner-grounded misconceptions, absence of invented misconceptions, feedback
correctness, and field-specific semantics. Correct requirements use only
`correctConcepts`, missing requirements only `missingConcepts`, misconception
requirements only `misconceptions`, and feedback requirements only `feedback`.
Feedback corrections cannot compensate for missing required structured fields.

Each retained case output has `PASS`, `FAIL`, or `PENDING`, with reviewer identity,
review date, rubric/version, reasons, and artifact reference. Unreviewed or
unresolved cases remain `PENDING`; demonstrated violations are `FAIL`.
Preserve materially disagreeing reviews. A designated independent human
adjudicator resolves disagreements against learner/source evidence and the
rubric, using medically qualified judgment when needed. Record the resolution;
never hide disagreement in an average or override it with a grammar verdict.

## Current case set and acceptance

Review the complete current Golden v5 response-evaluation set:

| Case ID | Coverage |
| --- | --- |
| P7-09-CORRECT-001 | Correct |
| P7-09-PARAPHRASE-001 | Valid alternative wording |
| P7-09-PARTIAL-001 | Partial |
| P7-09-WRONG-REASONING-001 | Correct conclusion, wrong reasoning |
| P7-09-INCORRECT-001 | Incorrect |
| P7-09-OFFTOPIC-001 | Off-topic |
| P7-09-UNCERTAIN-001 | Uncertain |
| P7-09-EMPTY-001 | Empty |
| P7-09-INJECTION-001 | Injection/adversarial |

The exact rubric is the loader-resolved v5 case set: v2 base resources, v4 rubric
overrides, and the v5 response-evaluation input-contract overlay. Retain the
repository revision or resource hashes identifying those immutable inputs.
This ADR changes neither the dataset nor its allowed classifications/rubrics.

A qualification record requires passing applicable Layers 1–2, complete Layer 3
evidence for this set, and human semantic `PASS` for every included case output.
Any unresolved required-case `FAIL` or `PENDING` prevents recording a Golden
qualification pass. Preserve unsuccessful runs and reruns; do not cherry-pick a
passing generation or routinely rerun until green. Existing Document 15 release
gates, including critical correctness/grounding/integrity gates, still apply.

Repeated-sample counts, statistical agreement/reliability thresholds, and any
new numerical quality or performance targets are **UNRESOLVED** unless already
established by an applicable benchmark decision. No single complete run proves
statistical stability. This ADR establishes per-case reviewed evidence, not a
new statistical guarantee. If observed inconsistency or a claimed reliability
target makes repeated sampling material to approval, qualification remains
pending until the owners resolve the sampling/acceptance criteria through the
Document 27 process and record benchmark evidence. Do not invent numbers.

## Evidence retention and approval identity

Prefer the existing `target/ai-golden-evaluation` reports and normal retained
review artifacts. Copy/reference them in durable qualification evidence before
build cleanup or report overwrite; a disposable target path alone is insufficient.
No new database, service, or persistence mechanism is authorized.

Retain run/case IDs, dataset/rubric version and resource identity, provider and
model identity/version (or the provider's available identifier and limitations),
prompt/version and repository revision, relevant effective configuration,
contract-validation result, repair usage/result and repair prompt identity,
sanitized diagnostics, finish reasons, available usage/latency, validated
structured output, semantic status, reviewer/adjudication records, and failure
reasons. A failed run without validated output records that absence explicitly.
Tie the approval record to the reviewed case artifacts and their identities.

Configuration evidence includes grounding/source-context identity, generation
settings (including thinking/temperature where applicable), schema/contract,
context/output budgets, and route/fallback selection. Do not retain credentials,
authorization headers, secrets, unrestricted raw provider/request payloads, or
unnecessary private medical/student/source content. Use access-controlled
artifacts and the minimum authorized evidence needed for review.

## Re-review and ADR-0008 reconciliation

Invalidate prior live semantic approval for material changes to model identity
or version, response-evaluation prompt/version, structured-output contract,
aggregation or validation semantics affecting interpretation, repair semantics
or repair prompt, and the relevant Golden dataset/rubric/version. Documents 15
§§24, 63–64 and 25 §§101–102 also require re-evaluation for material provider/route
or fallback changes, generation/thinking settings, context/output budgets,
grounding/source/retrieval context, or other configuration affecting output.
Record the changed identity and relevant new qualification evidence; a provider
alias without an exposed immutable version has a documented reproducibility
limitation, not a fabricated version guarantee.

For `RESPONSE_EVALUATION`, ADR-0008's evaluation-approved task property must be
supported by this layered qualification record and applicable existing release
gates. Configuration, schema success, or smoke success alone cannot approve a
route. Each candidate/fallback needing approval requires evidence for its own
configuration. ADR-0008 remains accepted; this ADR clarifies its evidence
requirements and does not replace its eligibility algorithm or Provider Router.

# Rationale

This preserves semantic quality requirements while separating a parser's
coverage limitation from a provider defect. It follows the existing layered
evaluation, human-review, reproducibility, and quota-aware testing authority.

# Alternatives Considered

- **Closed grammar as sole live gate:** rejected because repeated harmless
  paraphrases yield false negatives and growing surface-form coupling.
- **Structural-only approval:** rejected because it cannot establish medical,
  source, feedback, or misconception correctness required by P7-09.
- **LLM judge:** deferred. Document 15 §55 permits calibrated assistance, not
  sole medical correctness authority; a judge introduces reproducibility,
  correlated-error, privacy, cost, and calibration concerns.

# Consequences

Known semantic fixtures remain deterministic. Live structural results and human
semantic judgments become explicit separate evidence. Human review adds work
and can disagree; adjudication and retained artifacts are necessary. The closed
grammar remains limited, and numeric sampling criteria remain unresolved.

# Security & Privacy Impact

Existing authorization, source scope, credential handling, and release gates
remain unchanged. Retained review evidence must be minimized and protected as
described above. This governance task performs no security review.

# Educational/Product Impact

Preserves nuanced feedback, field-specific gap evidence, and learner-grounded
misconceptions. The Learning Engine continues to own educational state and
interpret advisory AI output.

# Cost / Infrastructure Impact

No new provider or judge calls/dependency. Existing live runs remain quota-aware;
human review has an operational cost. No new infrastructure is authorized.

# Migration Impact

Acceptance of this policy does not certify prior live outputs, approve a model,
complete P7-09, or change executable behavior. Implementing separate report
outcomes and the review workflow requires a subsequent approved packet.

# Testing Impact

Keep current deterministic fixtures and safeguards. Future tooling must preserve
contract failure, semantic `FAIL`/`PENDING`, and matcher-coverage limitations as
distinct outcomes without silently granting qualification.

# Documentation Changes Required

Align Documents 15, 25, and 27, both ADR indexes, and P7-09 notes. Preserve tracker
status, Definition of Done, existing review/security gates, and ADR-0008.

# Clarification History

- **2026-10-08 — Project Hippocampus Team:** Explicitly authorized Maven
  evidence-collection success while human semantic review and overall Golden
  qualification remain `PENDING`; distinguished execution from approval and
  retained objective automated failure and ADR-0008 routing gates. This accepted
  clarification is documentation only and does not implement runner behavior.

# Approval

**Status:** ACCEPTED

**Approved By:** Project Hippocampus Team, through the user's explicit
2026-10-08 instruction to record this durable qualification policy.

Approval is for the governance decision only; implementation and provider
qualification evidence remain outstanding.
