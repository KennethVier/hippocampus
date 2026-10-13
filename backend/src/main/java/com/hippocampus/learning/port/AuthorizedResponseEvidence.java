package com.hippocampus.learning.port;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.hippocampus.learning.domain.StudyMissionGroundingMode;

public record AuthorizedResponseEvidence(
        StudyMissionGroundingMode groundingMode,
        List<Source> sources,
        Limitation limitation) {

    public AuthorizedResponseEvidence {
        Objects.requireNonNull(groundingMode, "groundingMode must not be null");
        sources = List.copyOf(Objects.requireNonNull(sources, "sources must not be null"));
        Objects.requireNonNull(limitation, "limitation must not be null");
    }

    public AuthorizedResponseEvidence(StudyMissionGroundingMode groundingMode,
            List<Source> sources) {
        this(groundingMode, sources, Limitation.NONE);
    }

    public enum Limitation { NONE, NON_CHUNK_REFERENCE_UNSUPPORTED }

    public record Source(
            UUID sourceReferenceId, UUID chunkId, UUID materialId,
            UUID materialVersionId, UUID documentNodeId, int chunkIndex,
            String content, Integer pageStart, Integer pageEnd,
            String contentType, String extractionMethod, String quality) {
        public Source {
            Objects.requireNonNull(sourceReferenceId, "sourceReferenceId must not be null");
            Objects.requireNonNull(chunkId, "chunkId must not be null");
            Objects.requireNonNull(materialId, "materialId must not be null");
            Objects.requireNonNull(materialVersionId, "materialVersionId must not be null");
            Objects.requireNonNull(content, "content must not be null");
            if (content.isBlank() || chunkIndex < 1) {
                throw new IllegalArgumentException("source content and index must be valid");
            }
        }
    }
}
