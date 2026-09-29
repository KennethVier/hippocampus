package com.hippocampus.learning.port;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface GeneratedArtifactRepository {

    GeneratedArtifact save(GeneratedArtifact artifact);

    void addSources(UUID artifactId, Set<UUID> sourceReferenceIds);

    Optional<GeneratedArtifact> findById(UUID artifactId);

    Set<UUID> findSourceReferenceIds(UUID artifactId);

    record GeneratedArtifact(
            UUID id,
            UUID userId,
            String artifactType,
            String taskType,
            String contentText,
            String contentPayload,
            String groundingMode,
            String classification,
            String promptId,
            String promptVersion,
            String provider,
            String model,
            String modelVersion,
            String validationStatus,
            boolean reusable,
            Instant createdAt) {

        public GeneratedArtifact {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(userId, "userId must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
        }
    }
}
