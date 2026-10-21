package com.hippocampus.learning.domain.policy;

import com.hippocampus.learning.domain.*;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Pure policy; callers supply only application-validated facts, never feedback prose. */
public final class GradedResponseFollowUpPolicy {
    public Optional<FollowUpIntent> create(UUID id, UUID owner, UUID mission, UUID attempt,
            UUID activity, LearningObjective objective, AttemptOutcome grade,
            FollowUpIntent.Basis basis, FollowUpIntent.Scope scope, Instant now) {
        if (grade == AttemptOutcome.UNGRADED) return Optional.empty();
        String target = objective.conceptKey();
        if (target == null || target.isBlank()) target = objective.objectiveText();
        if (basis == null) throw new IllegalArgumentException("Validated follow-up basis is required");
        FollowUpIntent.Purpose purpose = switch (grade) {
            case CORRECT -> FollowUpIntent.Purpose.DEEPEN_OR_TRANSFER;
            case PARTIAL -> FollowUpIntent.Purpose.REPAIR_GAP;
            case INCORRECT -> FollowUpIntent.Purpose.SCAFFOLD_OR_RETRY;
            case UNGRADED -> throw new IllegalArgumentException("UNGRADED");
        };
        return Optional.of(new FollowUpIntent(id, owner, mission, objective.id(),
                attempt, activity, grade, purpose, target, basis, scope,
                FollowUpIntent.identity(objective.id(), target), FollowUpIntent.State.PENDING,
                null, null, now, now));
    }
    public NextLearningAction select(LearningState state, NextLearningAction normal, FollowUpIntent intent) {
        if (intent == null || intent.terminal() || intent.state() == FollowUpIntent.State.MATERIALIZED) return normal;
        if (!state.missionId().equals(intent.missionId())
                || !state.learningObjectiveId().equals(intent.objectiveId())) {
            throw new IllegalArgumentException("Follow-up outside learning state");
        }
        // Existing lifecycle, completion and corrective support retain precedence.
        switch (normal.actionType()) {
            case COMPLETE, STOP, PAUSE, SOURCE_ONLY, COMMUNICATE_LIMITATION, RETRY_DEPENDENCY -> { return normal; }
            case UNDERSTANDING_CHECK -> { return normal; }
            case HINT, PREREQUISITE_SUPPORT, UNDERSTAND -> {
                if (!state.evidence().isAtLeast(EvidenceDimension.UNDERSTANDING, EvidenceStrength.DEVELOPING)) {
                    return normal;
                }
                RecentLearningActivity latest=state.recentActivityHistory().isEmpty() ? null : state.recentActivityHistory().getLast();
                boolean supportCompleted=latest!=null && latest.attemptOutcome()==null
                        && Set.of("UNDERSTAND","HINT","PREREQUISITE_SUPPORT").contains(latest.activityType());
                if (!supportCompleted) return normal;
            }
            default -> { }
        }
        LearningActivityIntent repetition = intent.grade() == AttemptOutcome.CORRECT
                ? LearningActivityIntent.STANDARD : LearningActivityIntent.REASSESSMENT;
        LearningActionConstraints c = state.actionConstraints();
        var constraints = new LearningActionConstraints(c.sourceRequirement(), c.visualRequired(),
                c.supplementalKnowledgeAllowed(), intent.questionIntent(), null, repetition,
                c.visualRequired() ? RetrievalActivityType.IDENTIFICATION : RetrievalActivityType.SHORT_ANSWER);
        return new NextLearningAction(LearningActionType.RETRIEVE, intent.objectiveId(), intent.target(),
                intent.grade() == AttemptOutcome.CORRECT ? LearningDifficulty.INTERMEDIATE : LearningDifficulty.FOUNDATIONAL,
                "GRADED_RESPONSE_FOLLOW_UP", true, constraints);
    }
    /** V2: no new fallback activity may stand in for an exhausted authorized transition. */
    public void verifyProgress(LearningState state, FollowUpIntent intent, NextLearningAction action) {
        if (intent == null || intent.terminal() || intent.state() == FollowUpIntent.State.MATERIALIZED) return;
        if (Set.of(LearningActionType.COMPLETE, LearningActionType.STOP, LearningActionType.PAUSE,
                LearningActionType.SOURCE_ONLY, LearningActionType.COMMUNICATE_LIMITATION,
                LearningActionType.RETRY_DEPENDENCY).contains(action.actionType())
                || Set.of("TIME_LIMIT", "SOURCE_LIMITED", "SOURCE_UNAVAILABLE",
                        "VISUAL_UNAVAILABLE", "VISUAL_UNRELIABLE").contains(action.rationaleCode())) return;
        boolean unresolvedPrerequisite = false;
        if (!state.evidence().isAtLeast(EvidenceDimension.UNDERSTANDING, EvidenceStrength.DEVELOPING)) {
            for (RecentLearningActivity activity : PolicyHistory.recentForConcept(state)) {
                if (activity.learningActivityId().equals(intent.originatingActivityId())) break;
                if (Set.of("UNDERSTANDING_CHECK", "UNDERSTAND", "HINT", "PREREQUISITE_SUPPORT")
                        .contains(activity.activityType()) && activity.attemptOutcome() != null) {
                    unresolvedPrerequisite = activity.attemptOutcome() == AttemptOutcome.UNGRADED;
                    break;
                }
            }
        }
        if (unresolvedPrerequisite || action.actionType() == LearningActionType.REFLECT) {
            throw new com.hippocampus.shared.domain.error.DomainConflictException(
                    new com.hippocampus.shared.domain.error.ErrorCode("FOLLOW_UP_NO_PROGRESS"),
                    "No eligible follow-up or prerequisite transition is currently available.");
        }
    }
}
