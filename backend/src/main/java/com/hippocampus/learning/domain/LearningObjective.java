package com.hippocampus.learning.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record LearningObjective(
        UUID id,
        String objectiveText,
        String conceptKey,
        String displayName,
        Integer priority,
        LearningObjectiveStatus status,
        Instant createdAt) {

    public LearningObjective {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(objectiveText, "objectiveText must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (objectiveText.isBlank()) {
            throw new IllegalArgumentException("objectiveText must not be blank");
        }
    }
}
