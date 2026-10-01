package com.hippocampus.learning.port;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.hippocampus.learning.domain.MissionMaterial;

public interface StudyMissionSourcePresentationRepository {

    Optional<List<SourcePresentation>> resolveAuthorized(
            UUID ownerId,
            List<MissionMaterial> missionMaterials,
            Set<UUID> sourceReferenceIds);

    record SourcePresentation(
            UUID sourceReferenceId,
            String materialTitle,
            Integer pageNumber,
            String displayLabel) {}
}
