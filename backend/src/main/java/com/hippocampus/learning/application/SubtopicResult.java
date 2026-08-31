package com.hippocampus.learning.application;

import java.time.Instant;
import java.util.UUID;
import com.hippocampus.learning.domain.Subtopic;

public record SubtopicResult(UUID id, UUID topicId, String name, String description, Integer sortOrder,
        Status status, Instant createdAt, Instant updatedAt) {
    static SubtopicResult from(Subtopic subtopic) {
        return new SubtopicResult(subtopic.id(), subtopic.topicId(), subtopic.name(), subtopic.description(),
                subtopic.sortOrder(), Status.valueOf(subtopic.status().name()), subtopic.createdAt(), subtopic.updatedAt());
    }
    public enum Status { ACTIVE, ARCHIVED }
}
