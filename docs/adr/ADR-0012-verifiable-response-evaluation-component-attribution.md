---
ADR: ADR-0012
Title: Verifiable Component Attribution for Response Evaluation
Status: ACCEPTED
Date: 2026-10-11
Decision Owners: Project Hippocampus Team
Categories: AI, DOMAIN, SECURITY, TESTING
Affected Documents: 11, 12, 15, 27, IMPLEMENTATION-TRACKER, ADR-0011
Supersedes: None
Superseded By: None
---

# Context

P7-09 aggregates provider-supplied free-text `supportedComponents` without a
checkable link to the student's answer or authorized evidence. Golden v6 Case 5
requires PARTIAL credit for independently stated wrist-extensor paralysis
despite a wrong median-nerve attribution; V9 returned INCORRECT with no credited
component. Case 3 forbids crediting radial-nerve identification borrowed from
the question. Checking only proposed credit cannot recover a constituent when
the first candidate set is empty.

The current `SubmitActivityResponseUseCase` rejects `UNCERTAIN` before creating
a `StudentAttempt`, returning feedback, or offering continuation. The current
`ResponseEvaluationPort.Request` carries no authorized source package. These
are runtime implementation dependencies, not existing capabilities. The
current adapter fabricates an empty `GENERAL_KNOWLEDGE` evidence package; it
cannot establish source-grounded verification.

# Decision

Before deciding credit, a bounded discovery pass must independently inventory
relevant assertions in the exact student response even when the first model
proposes zero `supportedComponents`. It maps each assertion to all potentially
affected expected concepts and relationships, recording negation, qualifiers,
causal/anatomical attribution, contradiction, and uncertainty. The question and
expected answer are comparison context, never evidence of a student assertion.
Discovery can propose an omitted constituent but cannot award credit.

Extend each atomic judgment with evidence-bearing assertions and candidate
components while retaining separate missing concepts and misconceptions:

```text
Assertion {id, studentStart, studentEnd, studentText, proposition,
           polarity, qualifiers, relatedExpectedConceptIndexes[]}
Candidate {assertionIds[], expectedConceptIndex, constituentMeaning,
           relationToExpectedConcept, sourceEvidence[]}
SourceEvidence {sourceReferenceId, sourceVersion, sourceStart, sourceEnd, sourceText}
Verification {assertionDisposition[], candidateDisposition[],
              expectedConceptCoverage[], limitations[]}
```

Offsets are zero-based Java `String` UTF-16 code-unit indexes into the
unaltered submitted response or authorized source chunk: start inclusive, end
exclusive, at valid code-point boundaries. `substring(start, end)` must exactly
equal the supplied text. A student span must be nonempty and contain the
claimed assertion; multi-span paraphrases and relationships cite multiple
assertion IDs. Normalization can aid comparison but cannot replace exact links.

For source-grounded evaluation, the application resolves evidence from the
current authorized activity provenance and the mission's frozen material
versions. Student/mission/activity ownership and version scope are checked
**before** retrieval, ranking, or model use. The resulting bounded evidence
package is frozen for the evaluation request. Provider-supplied source IDs or
citations are claims to validate, never authorization evidence. Reuse existing
authorization and provenance mechanisms where they cover the request; extend
them only for missing source text/version resolution.

Before verification, application validation checks exact spans, all
expected-concept indexes, all relevant student assertions, candidate
associations, and source IDs, versions, authorization scope, and exact content
against an immutable evidence package selected for the current student,
mission, activity, and evaluation mode before AI ranking. Reject model-only,
stale, forged, and other-user sources. Source-grounded credit requires support
for the constituent *and asserted relationship*, not merely a valid citation.
For `GENERAL_KNOWLEDGE`, do not claim verified source-grounded credit without
an approved verification basis. Where a meaning cannot be safely verified,
resolve `UNCERTAIN`; never treat the current empty evidence package or a
provider citation as medical proof.

An application-owned, provider-neutral verifier receives the response,
validated assertions, expected concepts/relationships, discovered candidates,
and authorized source excerpts, but not the first model's grade or feedback.
Through the existing Provider Router's approved response-evaluation route, it
returns `SUPPORTED`, `UNSUPPORTED`, or `AMBIGUOUS` for every assertion and
candidate, short evidence-linked reasons, and per-concept coverage. `SUPPORTED`
requires student-demonstrated meaning, correct polarity/attribution, and
sufficient source support. `UNSUPPORTED` rejects that candidate; it does not
prove no other constituent was stated. The application checks coverage and
consistency again. Semantic adequacy requires ADR-0011 qualification and
medically qualified human review where appropriate; a second LLM opinion is
not independent medical correctness evidence.

Use at most one discovery pass and one verification pass per response, with
existing bounded repair only for malformed structured output. Neither pass
may invoke itself or recursively trigger evaluation. Use the existing
configuration and approved Provider Router route. Measure and set explicit
task limits for response/source size, assertion/candidate counts, output,
end-to-end latency, provider attempts, and cost before rollout. Exceeded
limits, truncation, timeout, routing or source
failure, malformed output, incomplete coverage, or disagreement resolve to
uncertainty. Calls remain outside persistence transactions. Never fall back
silently to the first model's grade or unsupported credit.

The aggregator derives `correctConcepts`, missing concepts, misconceptions,
feedback claims, and grade only from validated, verified assertions and
relationships. `CORRECT` requires all essential concepts/relationships and no
material misconception. `PARTIAL` requires at least one verified meaningful
constituent plus a gap or misconception. `INCORRECT` requires sufficiently
verified absence of *any* creditable constituent, including discovery
candidates. Unresolved absence or coverage is `UNCERTAIN`. A false attribution
does not erase a separately demonstrated constituent, but a correct isolated
word with false asserted meaning earns no credit. Deduplicate equivalent
missing concepts while retaining the false attribution and actual missing
relationship. Feedback reports only verified claims and qualified limitations.

`UNGRADED` is a **non-scoring submitted-attempt state**, distinct from
`INCORRECT`, `PARTIAL`, and `CORRECT`. It represents an accepted student
response whose evaluation is unresolved; it is not an AI grade or a fourth
correctness level. A validated `UNCERTAIN` evaluation maps to `UNGRADED` at
submission. Infrastructure failures before an evaluation/submission decision
remain retryable failures and create no attempt. An `UNGRADED` attempt retains
the actual response, activity/owner identity, timestamp, and limitation; it
has no deterministic result, correctness evidence, failure evidence, or mastery
change. No fabricated attempt may stand in for an unsaved response.

Persist the real `UNGRADED` attempt and completed activity atomically under the
existing owned mission snapshot and attempt-number checks. Return a confirmed
submission ID, `UNGRADED` outcome, neutral limitation feedback, empty or only
verified feedback dimensions, mission state, and an explicit continuation
availability/action. The Learning Engine chooses a safe retry as a new
activity, bounded explanation/support, continuation, or pause under normal
safety/source/time/stop/completion precedence. Do not relabel the same attempt
as `INCORRECT` or resubmit it to simulate a retry. A stale or duplicate
request must not append another attempt or advance the mission twice; return
the existing confirmed result when an idempotency key and matching owner,
activity, and request identity establish an exact replay, otherwise return the
existing stale/conflict response. Client refresh must recover the persisted
ungraded state and permissible next action without inventing a grade.

Learning-state assembly must skip `UNGRADED` for evidence strength and graded
performance policy while retaining it as submission/lifecycle history. It
must explicitly reject or surface unknown/corrupt persisted statuses; the
current fallback from parse failure to `INCORRECT` must be removed. `MANUAL_REVIEW`
is advisory, not an approved review queue. Until this contract is implemented,
retain the existing fail-closed retry behavior and do not claim uncertainty
was submitted or graded.

# Rationale

Evidence spans make attribution inspectable. Independent discovery addresses
the zero-candidate failure. Semantic verification is needed because substrings
cannot establish paraphrase equivalence or correct medical relationships.

# Alternatives considered

- Prompt-only changes: V9 already requested the distinction and failed Case 5.
- Automatic `INCORRECT` to `PARTIAL` conversion: invents credit in other cases.
- Literal substring validation alone: misses paraphrases and false relations.
- Verifying only first-model credit: cannot recover Case 5 from zero candidates.
- Second-model majority vote: does not establish independent medical truth.

# Consequences

This changes the evaluation, submission, and continuation contracts and may
increase latency and provider cost. It requires the bounded P7-09 runtime
grading remediation packet. It does not authorize
production prompt, Golden v6, V9, or historical evidence edits. P7-09 remains
Ready for Review; verifier agreement or deterministic regression alone does
not establish Golden qualification.

# Security and privacy impact

Use only the submitting student's answer and already-authorized evidence.
Validate source authorization and provenance before trusting references. Do
not log raw student spans or private source text in diagnostics. Malformed
offsets, forged references, injection, disagreement, and verifier failure
fail closed to uncertainty without false learning evidence.

# Educational and product impact

The formative `CORRECT`/`PARTIAL`/`INCORRECT` policy is unchanged. Unresolved
attribution is surfaced without false scoring or a new manual-review subsystem.

# Testing and documentation required

Cover exact-span tampering, Unicode boundaries, wrong-user/source IDs,
stale/mismatched chunks, source insufficiency, borrowed stem/expected-answer
facts, paraphrases, negation, false causal links, disagreements, limits,
timeouts, and fail-closed submission. A deterministic regression must show
Golden v6 Case 5 recovering the independently verified paralysis constituent
when the first evaluation proposes **zero** credit, while retaining the wrong
median-nerve attribution and missing correct relation. Case 3 must continue
rejecting question-borrowed radial-nerve credit, including pronoun-only
answers. Follow with identity-bound live output and independent human semantic
review under ADR-0011; regression success alone is insufficient. After
acceptance, align Documents 12 and 15, the P7-09 packet, and ADR-0011
qualification inputs prospectively. Preserve historical identities.

# Open decisions and feasibility dependencies

- The implementation packet must specify the owned source-package resolver and
  source-text/version plumbing. The current `ResponseEvaluationPort.Request`
  does not carry this data.
- The implementation must add the accepted ungraded contract across persistence,
  API, Learning Engine, and UI. The current submit path cannot represent it.
- Measure limits and confirm MVP latency/cost and complete-coverage reliability.
  If reliable coverage cannot be qualified, remain uncertain.

# Approval

Accepted with the 2026-10-11 controlling adjustments. Runtime changes require
the separately approved P7-09 implementation packet and normal validation,
independent review, security review, and qualification gates.


## ADR-0018 scoped eligibility clarification ? 2026-10-19

Accepted ADR-0018 adds runtime authority and historical evidence eligibility without altering this record's original approval provenance. Safely graded means independently admitted under the current trusted contract, not a persisted grade label or model agreement. Preserve original attempts/receipts; current unqualified free-text history cannot drive competence, failure, prerequisite or graded follow-up. A historical ineligible evaluation is distinct from a real saved UNGRADED attempt. Preserve existing safety/source/time/stop/terminal precedence, immutable audit and independent qualification requirements. No new medical grading authority or schema is approved.
