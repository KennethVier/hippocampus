package com.hippocampus.learning.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Subtopic(UUID id, UUID topicId, String name, String description, Integer sortOrder,
        SubtopicStatus status, Instant createdAt, Instant updatedAt) {
    public Subtopic {
        Objects.requireNonNull(topicId, "topicId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }

    public static Subtopic create(UUID topicId, String name, String description, Integer sortOrder) {
        return new Subtopic(null, topicId, name, description, sortOrder, SubtopicStatus.ACTIVE, null, null);
    }

    public Subtopic changeDetails(String newName, String newDescription, Integer newSortOrder) {
        return new Subtopic(id, topicId, newName, newDescription, newSortOrder, status, createdAt, updatedAt);
    }

    public Subtopic archive() {
        return status == SubtopicStatus.ARCHIVED ? this
                : new Subtopic(id, topicId, name, description, sortOrder, SubtopicStatus.ARCHIVED, createdAt, updatedAt);
    }
}
