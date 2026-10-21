---
ADR: ADR-0019
Proposal Version: 1
Title: Initial Mission Completion Admission Without Completed Learner Activity
Status: ACCEPTED
Date: 2026-10-20
Decision Owners: Project Hippocampus owner
Categories: EDUCATION, DOMAIN, BACKEND, FRONTEND, SECURITY, TESTING
Affected Documents: 06, 07, 11, 18, 27
Supersedes: None
Superseded By: None
---

# Status and authority

The owner selected this conservative behavior **for review and formal decision recording**. That selection is not final acceptance or implementation approval. ADR-0019 is the next unused identifier in the inspected index/directory; this proposal exists only in a new evidence packet, not the authoritative ADR directory. Recheck identifier availability before approved registration. No existing ADR or SOT file is changed here. Final owner approval must identify this proposal and the P7-14 v2.1 packet.

# Context

StartStudyMissionUseCase creates an ACTIVE mission with empty activity/evidence history and returns the existing Engine's firstAction. TimeAwareMissionPolicy can select COMPLETE/TIME_LIMIT when neither the selected activity nor REFLECT fits. With current default UNDERSTAND8/REFLECT2 durations, available1 minute already reaches this case; a failed/interrupted initial preparation may also age into it. Raising the entry minimum would not resolve recovery after elapsed time.

Accepted ADR-0016 permits an independently selected COMPLETE only through explicit Continue/Finish and current authorization; it does not authorize automatic lifecycle completion. The actual ChangeStudyMissionStatusUseCase.completeFromContinuation requires a genuine completed current activity, matching completion snapshot/history, final source authorization and fresh Engine redecision. A generated Engine action alone supplies none of these facts. Documents06/07 preserve unfinished work and learner controls; Document11 distinguishes time-aware selection from authorized persistence and forbids claiming the topic complete merely because time expired; Document18 requires traceable evidence and immutable frozen source identity. Their permissive “may complete” wording does not mandate completing an empty mission.

# Proposed decision

1. For an ACTIVE mission where no genuine completed LearningActivity exists, an Engine COMPLETE is **not admissible as a completion write** in initial preparation. Never persist mission COMPLETED/completedAt, set an objective complete, fabricate an activity/attempt/grade/evidence, invoke completion persistence with a synthetic activity, or attest successful M3 acceptance. Preserve actual lifecycle and existing source/owner restrictions.
2. For the genuinely empty initial mission, return HTTP200 non-scoring `NO_ACTIVITY` with the **actual** Engine `actionType`/`rationaleCode` and separate application `reasonCode=INITIAL_COMPLETION_NOT_ADMISSIBLE`, `canRetry=false`, `retryAfterSeconds=null`. Retain COMPLETE/TIME_LIMIT as an uncommitted selection; do not relabel it COMMUNICATE_LIMITATION or invent a domain action/rationale. The extra code is application admission metadata, not a mission status or learning outcome.
3. Offer existing explicit learner Pause and Stop controls. Do not offer an initial Finish action, automatically Pause/Stop/reset/restart, extend the time budget, reset startedAt, reopen terminal missions, substitute another educational activity or force any stage/MCQ. Existing nonempty continuation/completion contracts and other domain grounding modes remain unchanged. If a pending real activity exists, the initial bootstrap route cannot append/reconstruct another one: existing current-activity/continuation admission owns that state.
4. Prevent futile initial generation before a claim/provider call. An owned explicit POST reconstructs/revalidates legitimate current facts under the existing locks, or returns the previously recorded unchanged initial block; no claim slot or learning row is created for this denial. GET is read-only and never expires/renews claims or redecides by generating. Identical blocking facts on repeated POST return the same non-scoring disposition without receipt churn or provider calls. An application-only context fingerprint binds the block to relevant current mission/objective/frozen-source facts and **effective** time state, not client labels or the mere passage of cooldown. Equivalent remaining-time-zero polls do not become a new context merely because the raw clock ticked.
5. Re-evaluation requires an explicit authorized student action and a legitimate relevant state change, with current owner/source/lifecycle checks. Resume alone cannot reset original elapsed time, restore qualification, or guarantee generation; active-version advancement and post-freeze topic-link changes do not invent source revocation or new authority. This packet adds no edit-time/extend-budget API. A genuinely new mission requires explicit new-start intent and a new key, preserving the old mission; never start one automatically to evade this block or the claim cap.
6. If the final locked Engine redecision becomes COMPLETE after generation, reject/discard the draft before any artifact/activity/contract write, close only the matching operational preparation attempt and retain the non-scoring block atomically under mission→receipt locks. Operational claim history is not a StudentAttempt or learning evidence. Generation already attempted consumes its existing claim; repeated denied preparation consumes no additional claim. A stale worker may neither finalize an activity nor overwrite a newer receipt/block.
7. Claim exhaustion is separate from this educational admission denial. Three consumed claims prohibit all further generation for that receipt. Preserve EXHAUSTED operational state/history and the actual latest selection; where the last current Engine decision is COMPLETE, the response may still identify INITIAL_COMPLETION_NOT_ADMISSIBLE while truthfully exposing zero retries. Do not reset counts, mint a replacement receipt, fabricate failure evidence or terminalize the mission. Lifecycle controls remain separately authorized.

Example (common mission/key/replayed fields omitted here only):

```json
{
  "outcome":"NO_ACTIVITY",
  "missionStatus":"ACTIVE",
  "actionType":"COMPLETE",
  "rationaleCode":"TIME_LIMIT",
  "reasonCode":"INITIAL_COMPLETION_NOT_ADMISSIBLE",
  "canRetry":false,
  "retryAfterSeconds":null
}
```

# Compatibility and consequences

No LearningEngine/TimeAwareMissionPolicy selection algorithm, action enum, durations, evidence calculation, completion predicate, source policy or accepted grading contract changes. The application declines an inadmissible write; it does not override the Engine to select a different activity. The result is an incomplete mission with a truthful stopping point, not a completed topic or mastered objective. This satisfies preservation/control constraints and narrows an unspecified initial admission case without invalidating legitimate nonempty completion.

Rejected alternatives: fabricated completed presentation; automatic empty COMPLETED mission; treating Prepare/Retry as Finish; forced generation despite COMPLETE; time reset; new domain action/status; silent fresh mission; claiming M3 acceptance. A future explicit initial Finish/zero-work completion capability would require its own accepted decision and API/admission contract; this proposal does not authorize it.

Smallest SOT alignment: Document06 §19, Document07 F-04/F-05 boundary, Document11 §33, Document18 §55 and Document27 index/decision note. Accepted ADR-0016/0018 text stays unchanged; no supersession or new grading exception. Documents19/20/26 need no empty-completion policy rewrite; future implementation documentation can reference this accepted boundary and its approved API. P7-14 remains existing scope; its Golden/all-P7/M3 dependencies are not waived.

# Verification and acceptance

Tests must show zero activity/attempt/evidence/completedAt writes and zero provider/claim dispatch for initially inadmissible COMPLETE; unchanged repeated POST/GET are write-free; Pause/Stop remain learner controlled; original time/frozen sources persist across resume; draft generated before state/time changed is rejected at final admission; newer fence/lease blocks stale workers; exhaustion cannot restart generation. Real PostgreSQL tests prove transactional ordering/replay. Independent general review and separate security review follow required operator/CI validation, not ADR acceptance alone.

Status remains PROPOSED until explicit final owner approval. No implementation, V28, Golden qualification, original SEC-004 closure, M3 acceptance, live execution or publication is authorized by this document itself.

## Final owner acceptance — 2026-10-20

The project owner explicitly accepted proposal version 1 and the complete P7-14 v2.1 packet in the attached conversation request, retained at `.validation/p714-implementation-evidence-v1-20261020/OWNER-APPROVAL.txt` (SHA-256 `258e8c2652c497464fb2fc8d1e94d9ad02368a397742d20845f292f10eea5ff7`). The approval-subject manifest SHA-256 is `177631875bbcf824d4bd06f9b79672e1ecd579b377a42efa3f30f1fb10c75dea`. Earlier proposal-stage statements below remain historical preparation context; they do not negate this final acceptance. This owner acceptance supplies no medical qualification, global SEC-004 closure, M3 acceptance or independent implementation/security approval.
