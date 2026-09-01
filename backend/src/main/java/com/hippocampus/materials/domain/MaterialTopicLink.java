package com.hippocampus.materials.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MaterialTopicLink(
        UUID id,
        UUID topicId,
        UUID materialId,
        UUID materialVersionId,
        UUID documentNodeId,
        MaterialTopicLinkOrigin origin,
        MaterialTopicLinkStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public MaterialTopicLink {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(topicId, "topicId must not be null");
        Objects.requireNonNull(materialId, "materialId must not be null");
        Objects.requireNonNull(origin, "origin must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (documentNodeId != null && materialVersionId == null) {
            throw new IllegalArgumentException("documentNodeId requires materialVersionId");
        }
    }
}
