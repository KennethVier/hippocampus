package com.hippocampus.learning.port;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.hippocampus.learning.domain.MissionMaterial;
import com.hippocampus.learning.domain.StudyMissionGroundingMode;

public interface ResponseEvidenceResolver {
    AuthorizedResponseEvidence resolve(
            UUID userId, UUID missionId, UUID activityId,
            StudyMissionGroundingMode mode, List<MissionMaterial> materials,
            Set<UUID> activitySourceReferenceIds);
}
