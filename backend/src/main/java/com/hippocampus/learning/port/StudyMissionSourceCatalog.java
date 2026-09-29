package com.hippocampus.learning.port;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.hippocampus.learning.domain.SourceReadiness;

/**
 * Resolves source scopes that an authenticated learner may freeze into a new
 * Study Mission.
 */
public interface StudyMissionSourceCatalog {

    Optional<Resolution> resolve(UUID ownerId, UUID topicId, List<SourceSelection> selections);

    record SourceSelection(UUID materialId, UUID documentNodeId) {
        public SourceSelection {
            Objects.requireNonNull(materialId, "materialId must not be null");
        }
    }

    record ResolvedSource(
            UUID materialId,
            UUID materialVersionId,
            UUID documentNodeId,
            SourceReadiness readiness,
            boolean groundedTextAvailable,
            boolean visualAvailable,
            boolean visualReliable) {

        public ResolvedSource {
            Objects.requireNonNull(materialId, "materialId must not be null");
            Objects.requireNonNull(materialVersionId, "materialVersionId must not be null");
            Objects.requireNonNull(readiness, "readiness must not be null");
        }
    }

    record Resolution(List<ResolvedSource> sources) {
        public Resolution {
            sources = List.copyOf(Objects.requireNonNull(sources, "sources must not be null"));
        }
    }
}
