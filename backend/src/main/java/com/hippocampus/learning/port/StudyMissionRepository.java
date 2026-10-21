package com.hippocampus.learning.port;

import java.util.Optional;
import java.util.UUID;

import com.hippocampus.learning.domain.StudyMission;

public interface StudyMissionRepository {
    StudyMission save(StudyMission mission);

    Optional<StudyMission> findOwnedById(UUID missionId, UUID ownerId);

    Optional<StudyMission> findOwnedByIdForUpdate(UUID missionId, UUID ownerId);

    /** Completion must not reuse values cached by an earlier request read. */
    default Optional<StudyMission> findFreshOwnedByIdForUpdate(UUID missionId, UUID ownerId) {
        throw new IllegalStateException("Fresh locked mission reads are not configured");
    }
}
