package com.hippocampus.learning.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubtopicRepository extends JpaRepository<SubtopicEntity, UUID> {
    Optional<SubtopicEntity> findByIdAndTopicSubjectUserId(UUID id, UUID userId);
}
