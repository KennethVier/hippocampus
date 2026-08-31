package com.hippocampus.learning.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Topic(UUID id, UUID subjectId, String name, String description, TopicStatus status,
        Instant createdAt, Instant updatedAt) {
    public Topic {
        Objects.requireNonNull(subjectId, "subjectId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }

    public static Topic create(UUID subjectId, String name, String description) {
        return new Topic(null, subjectId, name, description, TopicStatus.ACTIVE, null, null);
    }

    public Topic changeDetails(String newName, String newDescription) {
        return new Topic(id, subjectId, newName, newDescription, status, createdAt, updatedAt);
    }

    public Topic archive() {
        return status == TopicStatus.ARCHIVED ? this
                : new Topic(id, subjectId, name, description, TopicStatus.ARCHIVED, createdAt, updatedAt);
    }
}
