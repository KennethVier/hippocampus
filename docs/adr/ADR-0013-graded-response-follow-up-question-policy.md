---
ADR: ADR-0013
Title: Learning-Engine Follow-up Questions After Graded Responses
Status: ACCEPTED
Date: 2026-10-11
Decision Owners: Project Hippocampus Team
Categories: EDUCATION, DOMAIN, AI
Affected Documents: 11, 12, 15, 26, 27, IMPLEMENTATION-TRACKER
Supersedes: None
Superseded By: None
---

# Context

P7-09 feedback may contain a bounded question, but `SubmitActivityResponseUseCase`
returns feedback and a Learning Engine next action;
`ContinueStudyMissionUseCase` later decides and materializes an activity. A
sentence ending in a question mark is not necessarily an answerable mission
activity. Current policies may select explanation, hint, retry, application,
reflection, completion, pause, or stop. They do not promise a new question
after every graded response.

# Decision

> For every safely graded, nonterminal CORRECT, PARTIAL, or INCORRECT response,
> the Learning Engine must establish an appropriate follow-up question intent,
> unless a higher-priority safety, stop, source, or terminal policy prevents it.
> Intent may be deferred when prerequisite support or time constraints require
> it. Follow-up questions are materialized through the normal authorized
> Learning Activity lifecycle.

The intent deepens or transfers after `CORRECT`, repairs a verified gap after
`PARTIAL`, and scaffolds or retries after `INCORRECT`. `UNGRADED` is outside
this graded-response requirement. The Engine may choose an explanation, hint,
prerequisite, pause, or safe alternative before materializing the question;
it may cancel under a higher-priority terminal or safety decision. AI does not
select the sequence.

A learner-visible question in feedback is optional prose. It must not solicit
a response the system cannot accept and does not satisfy a follow-up intent.
An answerable follow-up exists only after normal materialization creates a
current activity with an authorized source context, response contract,
activity ID, and submission path. Feedback text is never counted as that
activity or as learning evidence.

The Engine owns at most one pending intent per originating attempt and
learning objective, with the attempt/objective IDs, bounded purpose, target
concept/relation, grade and gap basis, source-scope/version requirements, and
recent-question identity. Retain it durably with mission continuation state
when deferred; provider output cannot persist it directly. On resume or
continuation, revalidate student/mission ownership, objective, safety, source
readiness, remaining time, recent-question history, and whether the originating
attempt still warrants the intent. Materialize once through the existing
activity lifecycle. Atomically consume or link the intent to that activity;
idempotent continuation and stale-client retries must reuse the activity, not
create duplicates. An answerable submitted follow-up satisfies the intent.
Cancel it on learner stop, terminal mission completion, superseding
objective/attempt, source-scope loss, or a safety decision, recording the
reason and never resurrecting it on resume. Pause or time limits may defer it
only while the mission remains resumable. Anti-repetition applies across
resumed sessions and generated paraphrases.

Safety and clinical boundaries, learner stop, source authorization/readiness,
time limits, corrective need, and mission completion retain Document 11's
precedence. No follow-up count can force an activity after a terminal or unsafe
decision, unavailable source, or insufficient time. AI may generate bounded
wording for an Engine-selected activity but cannot decide progression,
evidence, or intent lifecycle.

# Rationale and alternatives

A durable intent preserves the educational aim across explanation and resume.
Treating feedback prose as an activity would present a question without a
reliable submission or evidence path. Forcing immediate activity conflicts
with Document 11's precedence and mission lifecycle.

# Consequences

No existing tracker item owns the complete durable-intent lifecycle and
answerable follow-up materialization. **P7-15** is the separate Phase 7
implementation task, outside P7-09 runtime grading remediation and before the
P7-14 M3 gate, with exact scope:

- Learning Engine policy for grade-specific creation, precedence, deferral,
  satisfaction, cancellation, and anti-repetition;
- durable mission/attempt-linked intent state and atomic, idempotent
  continuation/materialization through existing activity/response contracts;
- learner-facing distinction between optional feedback prose and a current
  answerable activity, including resume behavior;
- scenario and integration regressions for all three grades, scaffolding,
  pause/resume, stale or duplicate continuation, source loss, safety, time
  limit, stop, objective change, and completion.

P7-15 depends on a validated P7-09 graded-outcome contract and reuses
P6 Learning Engine policies and P7-04/P7-05/P7-06/P7-08/P7-13 mission,
question, and UX paths as applicable. P7-14 depends on P7-15. The exact durable
schema, API details, and idempotency key are implementation-packet choices
constrained by this lifecycle. Documents 11, 12, 15, 26, and the tracker align
through Document 27 governance. P7-15 does not authorize Phase 8 work.

# Security and privacy impact

The follow-up uses only the current student's authorized mission evidence.
Reauthorize source scope before materialization; prevent cross-user access,
duplicate activity creation, and expected-answer leakage. Generated content
remains untrusted and subject to existing validation.

# Implementation decisions

- The P7-15 implementation packet must choose durable schema/API shape and an
  idempotency key without giving AI ownership of state.

# Approval

Accepted with the 2026-10-11 controlling adjustments. P7-15 implementation
requires its own approved packet, validation, independent review, and security
gate; this ADR does not implement the policy.


## ADR-0018 scoped eligibility clarification ? 2026-10-19

Accepted ADR-0018 adds runtime authority and historical evidence eligibility without altering this record's original approval provenance. Safely graded means independently admitted under the current trusted contract, not a persisted grade label or model agreement. Preserve original attempts/receipts; current unqualified free-text history cannot drive competence, failure, prerequisite or graded follow-up. A historical ineligible evaluation is distinct from a real saved UNGRADED attempt. Preserve existing safety/source/time/stop/terminal precedence, immutable audit and independent qualification requirements. No new medical grading authority or schema is approved.
