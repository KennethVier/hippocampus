package com.hippocampus.materials.domain;

import java.util.Objects;

public record PdfDocumentMetadata(int pageCount, String pdfVersion) {
    public PdfDocumentMetadata {
        if (pageCount < 1) {
            throw new IllegalArgumentException("pageCount must be positive");
        }
        Objects.requireNonNull(pdfVersion, "pdfVersion must not be null");
    }
}
