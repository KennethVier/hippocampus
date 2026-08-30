package com.hippocampus.learning.api;

import java.time.Instant;
import java.util.UUID;

import com.hippocampus.learning.application.SubjectResult;

public record SubjectResponse(
        UUID id,
        String name,
        String description,
        Integer sortOrder,
        Status status,
        Instant createdAt,
        Instant updatedAt) {

    static SubjectResponse from(SubjectResult result) {
        return new SubjectResponse(result.id(), result.name(), result.description(), result.sortOrder(),
                Status.valueOf(result.status().name()), result.createdAt(), result.updatedAt());
    }

    public enum Status { ACTIVE, ARCHIVED }
}
