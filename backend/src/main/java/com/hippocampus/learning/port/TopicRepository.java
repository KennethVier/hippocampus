package com.hippocampus.learning.port;

import java.util.Optional;
import java.util.UUID;
import com.hippocampus.learning.domain.Topic;

public interface TopicRepository {
    Optional<Topic> createUnderActiveOwnedSubject(Topic topic, UUID ownerId);
    Optional<Topic> findOwnedById(UUID topicId, UUID ownerId);
    Optional<Topic> findOwnedByIdWithActiveSubject(UUID topicId, UUID ownerId);
    Optional<Topic> saveOwned(Topic topic, UUID ownerId);
    Optional<TopicPage> findActiveByOwnedActiveSubject(UUID subjectId, UUID ownerId, TopicPageRequest request);
}
