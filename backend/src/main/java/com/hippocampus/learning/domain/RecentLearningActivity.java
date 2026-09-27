package com.hippocampus.learning.domain;

import java.util.Objects;
import java.util.UUID;

public record RecentLearningActivity(
        String conceptKey,
        String activityType,
        String questionIntent,
        LearningDifficulty difficulty,
        UUID sessionId) {

    public RecentLearningActivity {
        conceptKey = requireNonBlank(conceptKey, "conceptKey");
        activityType = requireNonBlank(activityType, "activityType");
        if (questionIntent != null && questionIntent.isBlank()) {
            throw new IllegalArgumentException("questionIntent must not be blank");
        }
        Objects.requireNonNull(difficulty, "difficulty must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
