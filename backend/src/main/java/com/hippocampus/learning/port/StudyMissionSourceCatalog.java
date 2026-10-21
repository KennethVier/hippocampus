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

    default Optional<Resolution> resolveLocked(UUID ownerId,UUID topicId,List<SourceSelection> selections) {
        throw new IllegalStateException("Locked source freeze is not configured");
    }
    default SourcePage eligible(UUID ownerId,UUID topicId,int limit,UUID afterMaterialId) {
        throw new IllegalStateException("Eligible source listing is not configured");
    }
    record EligibleSource(UUID materialId,String title,UUID activeVersionId,String processingStatus,
                          SourceReadiness readiness) {}
    record SourcePage(List<EligibleSource> items,UUID nextAfterMaterialId) {
        public SourcePage { items=List.copyOf(items); }
    }

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
