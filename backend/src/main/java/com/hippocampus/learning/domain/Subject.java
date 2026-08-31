package com.hippocampus.learning.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Subject(
        UUID id,
        UUID ownerId,
        String name,
        String description,
        Integer sortOrder,
        SubjectStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public Subject {
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }

    public static Subject create(UUID ownerId, String name, String description, Integer sortOrder) {
        return new Subject(null, ownerId, name, description, sortOrder, SubjectStatus.ACTIVE, null, null);
    }

    public Subject changeDetails(String newName, String newDescription, Integer newSortOrder) {
        return new Subject(id, ownerId, newName, newDescription, newSortOrder, status, createdAt, updatedAt);
    }

    public Subject archive() {
        if (status == SubjectStatus.ARCHIVED) {
            return this;
        }
        return new Subject(id, ownerId, name, description, sortOrder, SubjectStatus.ARCHIVED, createdAt, updatedAt);
    }
}
