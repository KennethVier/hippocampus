package com.hippocampus.materials.port;

import java.util.UUID;

import com.hippocampus.materials.domain.PdfPageBatch;

public interface PdfExtractionPersistence {
    void persistPageBatch(UUID materialVersionId, PdfPageBatch batch);

    void finalizePdfExtraction(UUID materialVersionId, int pageCount);
}
