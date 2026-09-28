package com.hippocampus.learning.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataLearningObjectiveRepository extends JpaRepository<LearningObjectiveEntity, UUID> {
    List<LearningObjectiveEntity> findAllByStudyMissionIdOrderById(UUID studyMissionId);
}
