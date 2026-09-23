package com.hippocampus.learning.domain;

import java.util.Objects;
import java.util.UUID;

public record RecentLearningActivity(
        String conceptKey,
        String activityType,
        String questionIntent,
        LearningDifficulty difficulty,
        UUID sessionId,
        String templateSignature,
        AttemptOutcome attemptOutcome,
        LearningActivityIntent repetitionIntent,
        boolean validatedContent) {

    public RecentLearningActivity {
        conceptKey = requireNonBlank(conceptKey, "conceptKey");
        activityType = requireNonBlank(activityType, "activityType");
        if (questionIntent != null && questionIntent.isBlank()) {
            throw new IllegalArgumentException("questionIntent must not be blank");
        }
        Objects.requireNonNull(difficulty, "difficulty must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        if (templateSignature != null && templateSignature.isBlank()) {
            throw new IllegalArgumentException("templateSignature must not be blank");
        }
        Objects.requireNonNull(repetitionIntent, "repetitionIntent must not be null");
    }

    public RecentLearningActivity(
            String conceptKey,
            String activityType,
            String questionIntent,
            LearningDifficulty difficulty,
            UUID sessionId) {
        this(
                conceptKey,
                activityType,
                questionIntent,
                difficulty,
                sessionId,
                null,
                null,
                LearningActivityIntent.STANDARD,
                false);
    }

    public boolean isUnsuccessfulAttempt() {
        return attemptOutcome == AttemptOutcome.PARTIAL || attemptOutcome == AttemptOutcome.INCORRECT;
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
