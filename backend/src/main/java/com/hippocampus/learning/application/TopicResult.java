package com.hippocampus.learning.application;

import java.time.Instant;
import java.util.UUID;
import com.hippocampus.learning.domain.Topic;

public record TopicResult(UUID id, UUID subjectId, String name, String description, Status status,
        Instant createdAt, Instant updatedAt) {
    static TopicResult from(Topic topic) {
        return new TopicResult(topic.id(), topic.subjectId(), topic.name(), topic.description(),
                Status.valueOf(topic.status().name()), topic.createdAt(), topic.updatedAt());
    }
    public enum Status { ACTIVE, ARCHIVED }
}
