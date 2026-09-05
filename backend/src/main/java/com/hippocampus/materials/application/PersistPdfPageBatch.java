package com.hippocampus.materials.application;

import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.materials.domain.PdfPageBatch;
import com.hippocampus.materials.port.PdfExtractionPersistence;

public class PersistPdfPageBatch {
    private final PdfExtractionPersistence persistence;

    public PersistPdfPageBatch(PdfExtractionPersistence persistence) {
        this.persistence = Objects.requireNonNull(persistence);
    }

    @Transactional
    public void execute(UUID materialVersionId, PdfPageBatch batch) {
        persistence.persistNativePageBatch(materialVersionId, batch);
    }
}
