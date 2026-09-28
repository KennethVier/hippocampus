package com.hippocampus.learning.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataStudyMissionRepository extends JpaRepository<StudyMissionEntity, UUID> {
    Optional<StudyMissionEntity> findByIdAndUserId(UUID id, UUID userId);
}
