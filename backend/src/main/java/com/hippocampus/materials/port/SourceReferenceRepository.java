package com.hippocampus.materials.port;

import java.util.Optional;
import java.util.UUID;

import com.hippocampus.materials.domain.SourceReference;
import com.hippocampus.materials.domain.SourceReferenceTarget;

public interface SourceReferenceRepository {
    record FrozenMissionBinding(UUID missionId,long generation,UUID claimToken) {
        public FrozenMissionBinding {
            java.util.Objects.requireNonNull(missionId);java.util.Objects.requireNonNull(claimToken);
            if(generation<=0 || missionId.equals(new UUID(0,0)) || claimToken.equals(new UUID(0,0)))
                throw new IllegalArgumentException("Invalid initial source binding");
        }
    }
    default java.util.List<SourceReference> createInitialMissionBatch(UUID owner,FrozenMissionBinding binding,
            java.util.List<com.hippocampus.materials.domain.SourceReferenceTarget> targets) {
        throw new IllegalStateException("Initial provenance creation is not configured");
    }

    Optional<SourceReferenceSeed> findAuthorizedTarget(UUID userId, SourceReferenceTarget target);

    default Optional<SourceReferenceSeed> findOwnedFrozenChunk(
            UUID userId, com.hippocampus.materials.domain.ChunkSourceTarget target) {
        return findAuthorizedTarget(userId, target);
    }

    SourceReference upsert(SourceReferenceSeed seed, String displayLabel);

    Optional<SourceReference> resolveAuthorized(UUID userId, UUID sourceReferenceId);
}
