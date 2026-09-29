package com.hippocampus.learning.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface SpringDataStudyMissionRepository extends JpaRepository<StudyMissionEntity, UUID> {
    Optional<StudyMissionEntity> findByIdAndUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT mission FROM StudyMissionEntity mission WHERE mission.id = :id AND mission.userId = :userId")
    Optional<StudyMissionEntity> findByIdAndUserIdForUpdate(
            @Param("id") UUID id,
            @Param("userId") UUID userId);
}
