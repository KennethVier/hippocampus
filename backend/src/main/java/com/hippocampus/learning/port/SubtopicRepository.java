package com.hippocampus.learning.port;

import java.util.Optional;
import java.util.UUID;
import com.hippocampus.learning.domain.Subtopic;

public interface SubtopicRepository {
    Optional<Subtopic> createUnderActiveOwnedTopic(Subtopic subtopic, UUID ownerId);
    Optional<Subtopic> findOwnedById(UUID subtopicId, UUID ownerId);
    Optional<Subtopic> findOwnedByIdWithActiveAncestors(UUID subtopicId, UUID ownerId);
    Optional<Subtopic> saveOwned(Subtopic subtopic, UUID ownerId);
    Optional<SubtopicPage> findActiveByOwnedActiveTopic(UUID topicId, UUID ownerId, SubtopicPageRequest request);
}
