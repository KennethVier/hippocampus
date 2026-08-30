package com.hippocampus.learning.application;

import java.time.Instant;
import java.util.UUID;

import com.hippocampus.learning.domain.Subject;

public record SubjectResult(
        UUID id,
        String name,
        String description,
        Integer sortOrder,
        Status status,
        Instant createdAt,
        Instant updatedAt) {

    static SubjectResult from(Subject subject) {
        return new SubjectResult(subject.id(), subject.name(), subject.description(), subject.sortOrder(),
                Status.valueOf(subject.status().name()), subject.createdAt(), subject.updatedAt());
    }

    public enum Status { ACTIVE, ARCHIVED }
}
