package com.hippocampus.learning.port;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.hippocampus.learning.domain.MissionMaterial;

public interface ActivitySourceReferenceAuthorization {
    boolean allOwnedAndWithinMissionScope(
            UUID userId,
            List<MissionMaterial> missionMaterials,
            Set<UUID> sourceReferenceIds);
}
