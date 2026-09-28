package com.hippocampus.learning.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record StudyMission(
        UUID id,
        UUID userId,
        UUID topicId,
        UUID subtopicId,
        StudyMissionStatus status,
        LearningStage learningState,
        StudyMissionGroundingMode groundingMode,
        Integer availableTimeMinutes,
        Instant startedAt,
        Instant completedAt,
        Instant stoppedAt,
        UUID currentActivityId,
        List<MissionMaterial> materials,
        List<LearningObjective> objectives,
        List<LearningActivity> activities,
        Instant createdAt,
        Instant updatedAt) {

    public StudyMission {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(topicId, "topicId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(groundingMode, "groundingMode must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (availableTimeMinutes != null && availableTimeMinutes < 0) {
            throw new IllegalArgumentException("availableTimeMinutes must not be negative");
        }

        materials = List.copyOf(Objects.requireNonNull(materials, "materials must not be null"));
        objectives = List.copyOf(Objects.requireNonNull(objectives, "objectives must not be null"));
        activities = List.copyOf(Objects.requireNonNull(activities, "activities must not be null"));

        Set<Integer> sequences = new HashSet<>();
        Set<UUID> activityIds = new HashSet<>();
        for (LearningActivity activity : activities) {
            if (!sequences.add(activity.sequenceNumber())) {
                throw new IllegalArgumentException("activity sequence numbers must be unique");
            }
            activityIds.add(activity.id());
        }
        if (currentActivityId != null && !activityIds.contains(currentActivityId)) {
            throw new IllegalArgumentException("currentActivityId must identify an activity in the mission");
        }

        if (!objectives.isEmpty()) {
            Set<UUID> objectiveIds = new HashSet<>();
            for (LearningObjective objective : objectives) {
                objectiveIds.add(objective.id());
            }
            for (LearningActivity activity : activities) {
                if (activity.learningObjectiveId() != null
                        && !objectiveIds.contains(activity.learningObjectiveId())) {
                    throw new IllegalArgumentException(
                            "learningObjectiveId must identify an objective in the mission");
                }
            }
        }
    }
}
