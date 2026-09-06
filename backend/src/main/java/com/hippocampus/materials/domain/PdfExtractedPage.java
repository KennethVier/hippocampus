package com.hippocampus.materials.domain;

import java.util.Objects;

public record PdfExtractedPage(
        int pageNumber,
        String content,
        TextBlockExtractionMethod extractionMethod,
        TextBlockQuality quality) {
    public PdfExtractedPage {
        if (pageNumber < 1) {
            throw new IllegalArgumentException("pageNumber must be positive");
        }
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(extractionMethod, "extractionMethod must not be null");
        if (extractionMethod == TextBlockExtractionMethod.NATIVE && quality != null) {
            throw new IllegalArgumentException("native page quality must be null");
        }
        if (extractionMethod == TextBlockExtractionMethod.OCR && quality == null) {
            throw new IllegalArgumentException("OCR page quality must not be null");
        }
        if (extractionMethod == TextBlockExtractionMethod.OCR && content.isBlank()
                && quality != TextBlockQuality.POOR) {
            throw new IllegalArgumentException("blank OCR content must have POOR quality");
        }
    }

    public static PdfExtractedPage nativePage(PdfNativePage page) {
        Objects.requireNonNull(page, "page must not be null");
        return new PdfExtractedPage(
                page.pageNumber(), page.nativeText(), TextBlockExtractionMethod.NATIVE, null);
    }
}
