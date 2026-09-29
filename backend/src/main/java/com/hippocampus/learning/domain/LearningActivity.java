package com.hippocampus.learning.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record LearningActivity(
        UUID id,
        UUID learningObjectiveId,
        LearningActivityType activityType,
        LearningActionType representedActionType,
        String questionIntent,
        String templateSignature,
        String status,
        LearningDifficulty difficulty,
        int sequenceNumber,
        UUID generatedArtifactId,
        boolean sourceRequired,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt,
        Set<UUID> sourceReferenceIds) {

    public LearningActivity {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(activityType, "activityType must not be null");
        Objects.requireNonNull(representedActionType, "representedActionType must not be null");
        if (questionIntent != null && questionIntent.isBlank()) {
            throw new IllegalArgumentException("questionIntent must not be blank");
        }
        if (templateSignature != null && templateSignature.isBlank()) {
            throw new IllegalArgumentException("templateSignature must not be blank");
        }
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (status.isBlank()) {
            throw new IllegalArgumentException("status must not be blank");
        }
        if (sequenceNumber < 1) {
            throw new IllegalArgumentException("sequenceNumber must be positive");
        }
        sourceReferenceIds = Set.copyOf(Objects.requireNonNull(
                sourceReferenceIds, "sourceReferenceIds must not be null"));
    }

    public RecentLearningActivity toRecentLearningActivity(
            String conceptKey,
            UUID sessionId,
            AttemptOutcome attemptOutcome,
            LearningActivityIntent repetitionIntent,
            boolean validatedContent) {
        return new RecentLearningActivity(
                id, conceptKey, representedActionType.name(), questionIntent, difficulty,
                sessionId, templateSignature, attemptOutcome, repetitionIntent, validatedContent);
    }
}
