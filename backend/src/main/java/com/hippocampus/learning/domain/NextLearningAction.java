package com.hippocampus.learning.domain;

import java.util.Objects;
import java.util.UUID;

public record NextLearningAction(
        LearningActionType actionType,
        UUID learningObjectiveId,
        String conceptKey,
        LearningDifficulty difficulty,
        String rationaleCode,
        boolean aiTaskRequired,
        LearningActionConstraints constraints,
        UUID reuseLearningActivityId) {

    public NextLearningAction {
        Objects.requireNonNull(actionType, "actionType must not be null");
        Objects.requireNonNull(learningObjectiveId, "learningObjectiveId must not be null");
        if (conceptKey != null && conceptKey.isBlank()) {
            throw new IllegalArgumentException("conceptKey must not be blank");
        }
        Objects.requireNonNull(rationaleCode, "rationaleCode must not be null");
        if (rationaleCode.isBlank()) {
            throw new IllegalArgumentException("rationaleCode must not be blank");
        }
        Objects.requireNonNull(constraints, "constraints must not be null");
        if ((actionType == LearningActionType.REUSE_VALIDATED_CONTENT)
                != (reuseLearningActivityId != null)) {
            throw new IllegalArgumentException(
                    "reuseLearningActivityId must be present exactly for REUSE_VALIDATED_CONTENT");
        }
        boolean generatesRetrievalQuestion = actionType == LearningActionType.RETRIEVE && aiTaskRequired;
        if (generatesRetrievalQuestion != (constraints.retrievalActivityType() != null)) {
            throw new IllegalArgumentException(
                    "retrievalActivityType must be present exactly for AI-backed RETRIEVE actions");
        }
    }

    public NextLearningAction(
            LearningActionType actionType,
            UUID learningObjectiveId,
            String conceptKey,
            LearningDifficulty difficulty,
            String rationaleCode,
            boolean aiTaskRequired,
            LearningActionConstraints constraints) {
        this(actionType, learningObjectiveId, conceptKey, difficulty, rationaleCode,
                aiTaskRequired, legacyCompatibleConstraints(actionType, aiTaskRequired, constraints), null);
    }

    public NextLearningAction(
            LearningActionType actionType,
            UUID learningObjectiveId,
            String conceptKey,
            LearningDifficulty difficulty,
            String rationaleCode,
            boolean aiTaskRequired) {
        this(actionType, learningObjectiveId, conceptKey, difficulty, rationaleCode,
                aiTaskRequired, LearningActionConstraints.unconstrained());
    }

    public NextLearningAction withRationale(String newRationaleCode) {
        return new NextLearningAction(
                actionType,
                learningObjectiveId,
                conceptKey,
                difficulty,
                newRationaleCode,
                aiTaskRequired,
                constraints,
                reuseLearningActivityId);
    }

    private static LearningActionConstraints legacyCompatibleConstraints(
            LearningActionType actionType,
            boolean aiTaskRequired,
            LearningActionConstraints constraints) {
        Objects.requireNonNull(constraints, "constraints must not be null");
        if (actionType != LearningActionType.RETRIEVE
                || !aiTaskRequired
                || constraints.retrievalActivityType() != null) {
            return constraints;
        }
        return constraints.withRetrievalActivityType(
                constraints.visualRequired()
                        ? RetrievalActivityType.IDENTIFICATION
                        : RetrievalActivityType.SHORT_ANSWER);
    }
}
