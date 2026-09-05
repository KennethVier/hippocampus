package com.hippocampus.materials.application;

import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.materials.port.PdfExtractionPersistence;

public class FinalizePdfExtraction {
    private final PdfExtractionPersistence persistence;

    public FinalizePdfExtraction(PdfExtractionPersistence persistence) {
        this.persistence = Objects.requireNonNull(persistence);
    }

    @Transactional
    public void execute(UUID materialVersionId, int pageCount) {
        persistence.finalizeNativePdfExtraction(materialVersionId, pageCount);
    }
}
