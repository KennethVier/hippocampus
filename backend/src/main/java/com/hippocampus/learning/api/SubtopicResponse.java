package com.hippocampus.learning.api;
import java.time.Instant; import java.util.UUID; import com.hippocampus.learning.application.SubtopicResult;
public record SubtopicResponse(UUID id,UUID topicId,String name,String description,Integer sortOrder,Status status,Instant createdAt,Instant updatedAt) {
    static SubtopicResponse from(SubtopicResult r) { return new SubtopicResponse(r.id(),r.topicId(),r.name(),r.description(),r.sortOrder(),Status.valueOf(r.status().name()),r.createdAt(),r.updatedAt()); }
    public enum Status { ACTIVE, ARCHIVED }
}
