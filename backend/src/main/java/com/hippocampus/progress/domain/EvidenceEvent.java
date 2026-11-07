package com.hippocampus.progress.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EvidenceEvent(
        UUID id,
        UUID userId,
        UUID topicId,
        UUID subtopicId,
        String conceptKey,
        UUID studentAttemptId,
        UUID learningActivityId,
        EvidenceEventType eventType,
        EvidenceOutcome outcome,
        String difficulty,
        String confidence,
        Instant occurredAt,
        Instant createdAt) {

    public EvidenceEvent {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(topicId, "topicId must not be null");
        Objects.requireNonNull(studentAttemptId, "studentAttemptId must not be null");
        Objects.requireNonNull(learningActivityId, "learningActivityId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (conceptKey != null && conceptKey.isBlank()) {
            throw new IllegalArgumentException("conceptKey must not be blank");
        }
        if (difficulty != null && difficulty.isBlank()) {
            throw new IllegalArgumentException("difficulty must not be blank");
        }
        if (confidence != null && confidence.isBlank()) {
            throw new IllegalArgumentException("confidence must not be blank");
        }
    }
}
