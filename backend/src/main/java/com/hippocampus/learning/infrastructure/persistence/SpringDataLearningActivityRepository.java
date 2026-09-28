package com.hippocampus.learning.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataLearningActivityRepository extends JpaRepository<LearningActivityEntity, UUID> {
    List<LearningActivityEntity> findAllByStudyMissionIdOrderBySequenceNumber(UUID studyMissionId);
}
