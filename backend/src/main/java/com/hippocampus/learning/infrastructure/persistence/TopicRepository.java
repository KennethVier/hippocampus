package com.hippocampus.learning.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicRepository extends JpaRepository<TopicEntity, UUID> {
    Optional<TopicEntity> findByIdAndSubjectUserId(UUID id, UUID userId);
}
