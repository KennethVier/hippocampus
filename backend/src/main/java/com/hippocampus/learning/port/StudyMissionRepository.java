package com.hippocampus.learning.port;

import java.util.Optional;
import java.util.UUID;

import com.hippocampus.learning.domain.StudyMission;

public interface StudyMissionRepository {
    StudyMission save(StudyMission mission);

    Optional<StudyMission> findOwnedById(UUID missionId, UUID ownerId);
}
