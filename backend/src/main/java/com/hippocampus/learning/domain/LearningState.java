package com.hippocampus.learning.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record LearningState(
        UUID missionId,
        UUID learningObjectiveId,
        String conceptKey,
        MissionLifecycleState missionState,
        LearningStage currentStage,
        LearningEvidenceSnapshot evidence,
        SourceCapability sourceCapability,
        LearningTimeContext timeContext,
        List<RecentLearningActivity> recentActivityHistory,
        boolean connectionRelevant,
        LearningActionConstraints actionConstraints) {

    public LearningState {
        Objects.requireNonNull(missionId, "missionId must not be null");
        Objects.requireNonNull(learningObjectiveId, "learningObjectiveId must not be null");
        Objects.requireNonNull(conceptKey, "conceptKey must not be null");
        if (conceptKey.isBlank()) {
            throw new IllegalArgumentException("conceptKey must not be blank");
        }
        Objects.requireNonNull(missionState, "missionState must not be null");
        Objects.requireNonNull(currentStage, "currentStage must not be null");
        Objects.requireNonNull(evidence, "evidence must not be null");
        Objects.requireNonNull(sourceCapability, "sourceCapability must not be null");
        Objects.requireNonNull(timeContext, "timeContext must not be null");
        Objects.requireNonNull(recentActivityHistory, "recentActivityHistory must not be null");
        recentActivityHistory = List.copyOf(recentActivityHistory);
        Objects.requireNonNull(actionConstraints, "actionConstraints must not be null");
    }

    public LearningState(
            UUID missionId,
            UUID learningObjectiveId,
            String conceptKey,
            MissionLifecycleState missionState,
            LearningStage currentStage,
            LearningEvidenceSnapshot evidence,
            SourceCapability sourceCapability,
            LearningTimeContext timeContext,
            List<RecentLearningActivity> recentActivityHistory) {
        this(
                missionId,
                learningObjectiveId,
                conceptKey,
                missionState,
                currentStage,
                evidence,
                sourceCapability,
                timeContext,
                recentActivityHistory,
                false,
                LearningActionConstraints.unconstrained());
    }
}
