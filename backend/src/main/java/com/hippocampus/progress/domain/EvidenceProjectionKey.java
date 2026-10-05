package com.hippocampus.progress.domain;

import java.util.Objects;
import java.util.UUID;

public record EvidenceProjectionKey(
        UUID userId,
        UUID topicId,
        UUID subtopicId,
        String conceptKey,
        EvidenceDimension dimension) {

    public EvidenceProjectionKey {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(topicId, "topicId must not be null");
        Objects.requireNonNull(dimension, "dimension must not be null");
        if (conceptKey != null && conceptKey.isBlank()) {
            throw new IllegalArgumentException("conceptKey must not be blank");
        }
    }
}
