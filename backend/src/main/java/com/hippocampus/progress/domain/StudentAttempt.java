package com.hippocampus.progress.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record StudentAttempt(
        UUID id,
        UUID userId,
        UUID learningActivityId,
        int attemptNumber,
        String responseText,
        String responsePayload,
        Instant submittedAt,
        String evaluationStatus,
        UUID evaluationArtifactId,
        String deterministicResult,
        Instant createdAt,
        UUID requestId,
        String limitationCode,
        String submissionReceipt) {

    public StudentAttempt {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(learningActivityId, "learningActivityId must not be null");
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber must be at least 1");
        }
        Objects.requireNonNull(submittedAt, "submittedAt must not be null");
        Objects.requireNonNull(evaluationStatus, "evaluationStatus must not be null");
        if (evaluationStatus.isBlank()) {
            throw new IllegalArgumentException("evaluationStatus must not be blank");
        }
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (limitationCode != null && limitationCode.isBlank()) {
            throw new IllegalArgumentException("limitationCode must not be blank");
        }
        if ("UNGRADED".equals(evaluationStatus)
                && (deterministicResult != null || limitationCode == null)) {
            throw new IllegalArgumentException("UNGRADED requires a limitation and no deterministic result");
        }
    }

    public StudentAttempt(
            UUID id, UUID userId, UUID learningActivityId, int attemptNumber,
            String responseText, String responsePayload, Instant submittedAt,
            String evaluationStatus, UUID evaluationArtifactId,
            String deterministicResult, Instant createdAt) {
        this(id, userId, learningActivityId, attemptNumber, responseText, responsePayload,
                submittedAt, evaluationStatus, evaluationArtifactId, deterministicResult,
                createdAt, null, null, null);
    }
}
