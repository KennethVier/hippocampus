package com.hippocampus.materials.port;

import java.util.UUID;

import com.hippocampus.materials.domain.PdfPageBatch;

public interface PdfExtractionPersistence {
    void persistNativePageBatch(UUID materialVersionId, PdfPageBatch batch);

    void finalizeNativePdfExtraction(UUID materialVersionId, int pageCount);
}
