package com.hippocampus.materials.port;

import java.util.Optional;
import java.util.UUID;

import com.hippocampus.materials.domain.SourceReference;
import com.hippocampus.materials.domain.SourceReferenceTarget;

public interface SourceReferenceRepository {
    Optional<SourceReferenceSeed> findAuthorizedTarget(UUID userId, SourceReferenceTarget target);

    SourceReference upsert(SourceReferenceSeed seed, String displayLabel);

    Optional<SourceReference> resolveAuthorized(UUID userId, UUID sourceReferenceId);
}
