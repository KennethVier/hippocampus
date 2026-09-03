package com.hippocampus.materials.domain;

import java.util.Objects;

public record PdfNativePage(int pageNumber, float widthPoints, float heightPoints, String nativeText) {
    public PdfNativePage {
        if (pageNumber < 1) {
            throw new IllegalArgumentException("pageNumber must be positive");
        }
        if (!Float.isFinite(widthPoints) || widthPoints <= 0
                || !Float.isFinite(heightPoints) || heightPoints <= 0) {
            throw new IllegalArgumentException("page dimensions must be positive and finite");
        }
        Objects.requireNonNull(nativeText, "nativeText must not be null");
    }
}
