package com.hippocampus.learning.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataMissionMaterialRepository extends JpaRepository<MissionMaterialEntity, UUID> {
    List<MissionMaterialEntity> findAllByStudyMissionIdOrderById(UUID studyMissionId);
}
