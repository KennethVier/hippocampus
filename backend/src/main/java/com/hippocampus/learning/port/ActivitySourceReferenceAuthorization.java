package com.hippocampus.learning.port;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.hippocampus.learning.domain.MissionMaterial;

public interface ActivitySourceReferenceAuthorization {
    /** Owner/frozen-scope readiness projection only; must not acquire row locks or write state. */
    default boolean availableForRead(UUID userId, List<MissionMaterial> missionMaterials,
            Set<UUID> sourceReferenceIds) {
        return missionMaterials.isEmpty() && sourceReferenceIds.isEmpty();
    }

    /** Fail closed unless the adapter can revalidate and lock the frozen source versions. */
    default boolean allFrozenSourcesAvailable(UUID userId, List<MissionMaterial> materials) {
        return materials.isEmpty();
    }

    boolean allOwnedAndWithinMissionScope(
            UUID userId,
            List<MissionMaterial> missionMaterials,
            Set<UUID> sourceReferenceIds);

    /** Must join the caller's READ COMMITTED write transaction and retain locks until its end. */
    default boolean lockAndValidateForCompletion(UUID userId, List<MissionMaterial> missionMaterials,
            Set<UUID> sourceReferenceIds, boolean sourceRequired) {
        throw new IllegalStateException("Completion source verification is not configured");
    }

    /** Retain consulted follow-up dependency locks in the same canonical pass, without a new readiness rule. */
    default boolean lockAndValidateForCompletion(UUID userId, List<MissionMaterial> missionMaterials,
            Set<UUID> sourceReferenceIds, boolean sourceRequired,
            List<MissionMaterial> consultedMaterials, Set<UUID> consultedReferences) {
        if (!consultedMaterials.isEmpty() || !consultedReferences.isEmpty())
            throw new IllegalStateException("Completion follow-up dependency locking is not configured");
        return lockAndValidateForCompletion(userId,missionMaterials,sourceReferenceIds,sourceRequired);
    }
}
