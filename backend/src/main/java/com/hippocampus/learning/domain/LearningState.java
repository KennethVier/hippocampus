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
        List<RecentLearningActivity> recentActivityHistory) {

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
    }
}
