package com.hippocampus.materials.port;

import java.util.Objects;
import java.util.UUID;

public record PdfExtractionSource(UUID materialVersionId, BinaryObjectKey objectKey, long fileSizeBytes) {
    public PdfExtractionSource {
        Objects.requireNonNull(materialVersionId, "materialVersionId must not be null");
        Objects.requireNonNull(objectKey, "objectKey must not be null");
        if (fileSizeBytes <= 0) {
            throw new IllegalArgumentException("fileSizeBytes must be positive");
        }
    }
}
