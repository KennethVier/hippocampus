package com.hippocampus.materials.port;

import java.util.UUID;

public interface PdfExtractionSourceRepository {
    PdfExtractionSource requireExtractablePdf(UUID materialVersionId);
}
