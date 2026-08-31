package com.hippocampus.learning.api;
import java.time.Instant; import java.util.UUID; import com.hippocampus.learning.application.TopicResult;
public record TopicResponse(UUID id,UUID subjectId,String name,String description,Status status,Instant createdAt,Instant updatedAt) {
    static TopicResponse from(TopicResult r) { return new TopicResponse(r.id(),r.subjectId(),r.name(),r.description(),Status.valueOf(r.status().name()),r.createdAt(),r.updatedAt()); }
    public enum Status { ACTIVE, ARCHIVED }
}
