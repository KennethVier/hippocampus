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
        LearningActionConstraints constraints) {

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
    }

    public NextLearningAction(
            LearningActionType actionType,
            UUID learningObjectiveId,
            String conceptKey,
            LearningDifficulty difficulty,
            String rationaleCode,
            boolean aiTaskRequired) {
        this(
                actionType,
                learningObjectiveId,
                conceptKey,
                difficulty,
                rationaleCode,
                aiTaskRequired,
                LearningActionConstraints.unconstrained());
    }

    public NextLearningAction withRationale(String newRationaleCode) {
        return new NextLearningAction(
                actionType,
                learningObjectiveId,
                conceptKey,
                difficulty,
                newRationaleCode,
                aiTaskRequired,
                constraints);
    }
}
