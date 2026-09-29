---
ADR: ADR-0006
Title: Explicit Reusable Learning Activity Identity
Status: ACCEPTED
Date: 2026-09-29
Decision Owners: Project Hippocampus Team
Categories: DOMAIN, BACKEND, EDUCATION
Affected Documents: 11, 18, 19, 27
---

# Context

The Learning Engine can determine that prior validated content is pedagogically
compatible by evaluating recent activity history, including concept, represented
activity/action type, difficulty, question intent, template signature, validation
state. The existing contracts do not identify the exact
persisted LearningActivity represented by the matching history entry, however,
and the persisted LearningActivity does not retain the represented
LearningActionType or all other metadata needed to reconstruct that history.

Existing Phase 6 policy does not select `REUSE_VALIDATED_CONTENT` when the failed
action has `visualRequired == true`. This decision does not change that rule or
introduce a visual-compatibility history field.

Selecting the newest artifact for the same objective during P7-04 materialization
would weaken Phase 6 compatibility rules. GeneratedArtifact records AI-content
provenance and eligibility, but it does not own the Learning Engine's pedagogical
compatibility decision.

# Decision

## Durable recent-activity identity

The conceptual `RecentLearningActivity` contract includes the durable identity
and existing policy fields:

``` text
learningActivityId UUID
conceptKey
activityType <- durable representedActionType
questionIntent
difficulty
sessionId
templateSignature
attemptOutcome
repetitionIntent
validatedContent
```

Every history entry that represents a durable LearningActivity identifies the
exact LearningActivity that produced it. Provider and artifact concerns do not
belong in `RecentLearningActivity`. Its `activityType` is reconstructed from the
durable LearningActivity `representedActionType`, not from the concrete rendered
activity type. No visual-compatibility history field is added.

## Durable LearningActivity compatibility metadata

`learning_activities` durably stores:

``` text
represented_action_type VARCHAR NOT NULL
question_intent VARCHAR NULL
template_signature VARCHAR NULL
```

`activity_type` is the concrete materialized/rendered LearningActivityType.
`represented_action_type` is the pedagogical LearningActionType represented by
the activity for Learning Engine history and policy reconstruction. The
distinction includes:

``` text
HINT                     -> activity_type = UNDERSTAND,
                            represented_action_type = HINT
PREREQUISITE_SUPPORT     -> activity_type = UNDERSTAND,
                            represented_action_type = PREREQUISITE_SUPPORT
visual-required UNDERSTAND
                         -> activity_type = VISUAL,
                            represented_action_type = UNDERSTAND
direct RETRIEVE          -> activity_type = RETRIEVE,
                            represented_action_type = RETRIEVE
RETRY of RETRIEVE        -> activity_type = RETRIEVE,
                            represented_action_type = RETRIEVE
REDUCE_DIFFICULTY of APPLY
                         -> activity_type = APPLY,
                            represented_action_type = APPLY,
                            difficulty = engine-selected reduced difficulty
```

For direct pedagogical actions (`UNDERSTAND`, `RETRIEVE`, `CONNECT`, `APPLY`,
`HINT`, `PREREQUISITE_SUPPORT`, `FEEDBACK`, and `REFLECT`), the represented
action is the underlying pedagogical action being materialized. When
`visualRequired` changes the concrete rendering to `VISUAL`, the represented
action remains that underlying pedagogical action.

For deterministic adaptation/orchestration actions (`RETRY`,
`REDUCE_DIFFICULTY`, and `REUSE_VALIDATED_CONTENT`), the new activity inherits
both its concrete activity type and represented action type from the exact
prior activity being retried, adapted, or reused. `REDUCE_DIFFICULTY` replaces
the inherited difficulty with the new difficulty selected by the Learning
Engine. The orchestration action itself is not stored as
`represented_action_type`. Non-materializable control actions remain outside
LearningActivity. A `RETRY` may reuse the prior activity's generated artifact
and source provenance only as permitted by the P7-04 eligibility,
authorization, grounding, and provenance rules.

Question intent and template signature describe the materialized learning
interaction and template. These fields support anti-repetition, history
reconstruction, and reuse decisions and must not exist only inside
`GeneratedArtifact.content_payload`. Difficulty, objective, generated-artifact
reference, sequence, status, source requirements, and timestamps retain their
existing authority. Concept identity continues to come from the
LearningObjective/LearningState relationship rather than being duplicated.
LearningActivity does not gain provider/model fields.

## Explicit NextLearningAction reuse target

The conceptual `NextLearningAction` contract includes:

``` text
reuseLearningActivityId UUID NULL
```

The following invariant applies:

``` text
actionType == REUSE_VALIDATED_CONTENT
    => reuseLearningActivityId IS NOT NULL

actionType != REUSE_VALIDATED_CONTENT
    => reuseLearningActivityId IS NULL
```

The value is the exact prior persisted LearningActivity selected by the Learning
Engine as pedagogically compatible. The Learning Engine targets a learning
activity, not a GeneratedArtifact, because it reasons about learning history and
pedagogical events. Artifact resolution remains an application/persistence
responsibility.

## Learning Engine ownership

The Learning Engine inspects `RecentLearningActivity` history, applies the
existing compatibility criteria, selects the most recent compatible validated
activity according to existing history ordering, emits
`REUSE_VALIDATED_CONTENT`, and copies that entry's `learningActivityId` into
`reuseLearningActivityId`.

No second scoring or ranking algorithm is introduced. P7-04 does not repeat or
replace the pedagogical compatibility search.

The explicit `reuseLearningActivityId` decision remains specific to
`REUSE_VALIDATED_CONTENT`. This decision adds no new `NextLearningAction` field
for `RETRY` or `REDUCE_DIFFICULTY`. P7-04 resolves the exact prior activity for
those actions according to the already-approved mission/activity progression
semantics. If existing mission state cannot identify that activity
deterministically during implementation, that is a separate blocker rather
than permission to add another compatibility search or guess a target.

## Application responsibility

When materializing `REUSE_VALIDATED_CONTENT`, the application:

1. resolves exactly `action.reuseLearningActivityId`;
2. verifies that the activity belongs to the same StudyMission and applicable
   LearningObjective/context;
3. verifies that it references a GeneratedArtifact and resolves that exact
   artifact;
4. verifies that the artifact owner is the mission owner, its validation status
   is `VALIDATED`, and `reusable = true`;
5. verifies grounding compatibility and all applicable Document 18 reuse
   constraints;
6. verifies that SourceReference ownership and provenance remain authorized
   within the mission's frozen MaterialVersion/DocumentNode scope; and
7. rejects deleted material.

Exact identity is necessary but not sufficient. The Learning Engine owns
pedagogical compatibility; application and persistence own authorization,
provenance, artifact validity, and durability. A UUID carried by LearningState or
NextLearningAction is never treated as an authorization token, and foreign IDs
fail closed.

## New activity on reuse

Reuse creates a new LearningActivity row. It does not reactivate or mutate the
previous activity. The new activity may reference the same validated
GeneratedArtifact and source provenance, while previous activity history remains
immutable.

The new activity inherits `represented_action_type` from the exact prior
LearningActivity selected by `reuseLearningActivityId`. It does not store
`REUSE_VALIDATED_CONTENT` as its represented action because that value is the
orchestration decision rather than the pedagogical activity being reused.

## GeneratedArtifact responsibility

GeneratedArtifact remains the durable record of AI-content provenance. It does
not own pedagogical compatibility. Reuse still satisfies Document 18 constraints
including, where applicable, task type, prompt version, provider/model evaluation
status, grounding mode, source versions, LearningObjective/concept,
`reusable = true`, and `validation_status = VALIDATED`. Grounded reusable
artifacts retain source provenance.

# Rationale

An explicit LearningActivity identity preserves the exact outcome of the
Learning Engine's compatibility decision across the application boundary. It
keeps learning-policy authority in the Learning Engine while giving the
application a precise durable target on which it can reapply authorization,
provenance, lifecycle, and artifact-validity controls.

Persisting represented action type, question intent, and template signature on
LearningActivity makes the pedagogical event reconstructable without equating
its concrete rendering with its LearningActionType or transferring educational
state to the AI artifact model.

# Alternatives Considered

## P7-04 chooses the newest LearningActivity with the same objective

Rejected because this is weaker than Phase 6 compatibility, duplicates
pedagogical decision logic, and may select the wrong activity or artifact.

## Put all compatibility metadata only on GeneratedArtifact

Rejected because artifact metadata describes AI/content provenance, while
question, template, and repetition compatibility are learning-domain state. This
would shift educational policy toward the AI artifact model.

## Re-run compatibility search inside P7-04

Rejected because it duplicates Learning Engine rules, can diverge from Phase 6,
and violates the boundary that the Learning Engine decides while the application
materializes.

## Store only GeneratedArtifact ID in NextLearningAction

Rejected because Learning Engine history reasons about LearningActivities. The
activity is the durable pedagogical event; its artifact is a subordinate
generated-content reference.

# Consequences

Phase 6 contracts and persistence must eventually carry the new activity
identity and metadata before P7-04 reuse materialization resumes. The
application gains deterministic artifact resolution but must still perform all
authorization, provenance, lifecycle, and eligibility checks.

Reusing content adds a new LearningActivity while preserving the original
activity and artifact history. No AI/provider call is needed merely to regenerate
already validated compatible content. The new activity inherits the prior
activity's represented pedagogical action type.

# Security & Privacy Impact

Explicit identity narrows selection but grants no authority. Every reuse is
re-authorized against the authenticated user, owning StudyMission, frozen mission
source scope, GeneratedArtifact ownership, SourceReference provenance, and
material lifecycle. Deleted source material cannot become reusable merely because
a prior artifact remains persisted.

# Educational/Product Impact

The Learning Engine remains the sole owner of pedagogical compatibility. The
decision preserves Phase 6 anti-repetition and compatibility semantics without
changing the product goal or granting AI/application orchestration educational
authority.

# Cost / Infrastructure Impact

No new service, provider, or infrastructure is introduced. Valid reuse avoids an
unnecessary AI generation call.

# Migration Impact

A later implementation must add required `represented_action_type` and nullable
`question_intent` and `template_signature` columns to `learning_activities` and
evolve the affected domain/application contracts. This ADR does not implement
that migration.

# Testing Impact

Future implementation tests must cover:

- HINT history surviving materialization/reload as represented `HINT` even when
  concrete `activity_type` is `UNDERSTAND`;
- PREREQUISITE_SUPPORT surviving the same mapping;
- a concrete `VISUAL` activity retaining its underlying represented action;
- a reused activity inheriting `represented_action_type` from the exact selected
  prior activity rather than storing `REUSE_VALIDATED_CONTENT`;
- `RETRY` inheriting `represented_action_type` from the prior activity, with a
  retried `RETRIEVE` remaining recognizable as `RETRIEVE` in reconstructed
  Learning Engine history;
- `REDUCE_DIFFICULTY` inheriting `represented_action_type`, with a reduced
  `APPLY` activity remaining recognizable as `APPLY` in reconstructed Learning
  Engine history;
- the reuse-target invariant and most-recent exact compatible selection; and
- exact activity resolution, new-activity creation, and fail-closed ownership,
  provenance, validation, reusable-state, grounding, lifecycle, and
  deleted-material checks.

# Documentation Changes Required

Documents 11, 18, 19, and 27 are aligned with this decision. The Implementation
Tracker records P7-04 as blocked until ADR-0006 is accepted/merged and its
contract changes can be implemented.

# Approval

**Status:** ACCEPTED

**Approved By:** Project Hippocampus Team
