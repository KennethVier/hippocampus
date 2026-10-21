package com.hippocampus.learning.domain;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Application-established routing facts, not medical grading authority. */
public record AssessmentAvailability(boolean savedUngraded, boolean historicalIneligible, UUID currentActivityId,
        Map<UUID, Kind> activities) {
    public enum Kind { PRESENTATION, DETERMINISTIC, UNAVAILABLE, UNKNOWN }

    public AssessmentAvailability {
        activities = Map.copyOf(Objects.requireNonNull(activities));
        if (savedUngraded && historicalIneligible) throw new IllegalArgumentException("Distinct historical dispositions required");
        if (savedUngraded || historicalIneligible) Objects.requireNonNull(currentActivityId);
    }

    public AssessmentAvailability(boolean savedUngraded, UUID currentActivityId, Map<UUID, Kind> activities) {
        this(savedUngraded, false, currentActivityId, activities);
    }
    public boolean nonScoringAssessment() { return savedUngraded || historicalIneligible; }

    public static AssessmentAvailability unspecified() {
        return new AssessmentAvailability(false, null, Map.of());
    }

    public Kind target(NextLearningAction action) {
        return switch (action.actionType()) {
            case RETRY, REDUCE_DIFFICULTY -> activities.getOrDefault(currentActivityId, Kind.UNKNOWN);
            case REUSE_VALIDATED_CONTENT -> activities.getOrDefault(action.reuseLearningActivityId(), Kind.UNKNOWN);
            case RETRIEVE, UNDERSTANDING_CHECK -> action.constraints().retrievalActivityType() == RetrievalActivityType.MCQ
                    ? Kind.DETERMINISTIC : Kind.UNAVAILABLE;
            case CONNECT, APPLY -> Kind.UNAVAILABLE;
            default -> Kind.PRESENTATION;
        };
    }

    public NextLearningAction constrain(NextLearningAction action) {
        if (!nonScoringAssessment() || target(action) != Kind.UNAVAILABLE) return action;
        return new NextLearningAction(LearningActionType.COMMUNICATE_LIMITATION,
                action.learningObjectiveId(), action.conceptKey(), null,
                LearningRationaleCodes.ASSESSMENT_UNAVAILABLE, false,
                LearningActionConstraints.unconstrained());
    }
}
