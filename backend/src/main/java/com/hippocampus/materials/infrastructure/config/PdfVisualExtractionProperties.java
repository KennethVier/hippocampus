package com.hippocampus.materials.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hippocampus.materials.processing.pdf.visual")
public record PdfVisualExtractionProperties(
        int maxImagesPerPage,
        int maxImagesPerDocument,
        int maxSourceImageDimension,
        long maxSourceImagePixels,
        long maxPageSourceImagePixels,
        long maxDocumentSourceImagePixels,
        int maxEncodedImageBytes,
        long maxPageEncodedImageBytes,
        long maxDocumentEncodedImageBytes) {
    public PdfVisualExtractionProperties {
        if (maxImagesPerPage <= 0 || maxImagesPerDocument <= 0 || maxSourceImageDimension <= 0
                || maxSourceImagePixels <= 0 || maxPageSourceImagePixels <= 0
                || maxDocumentSourceImagePixels <= 0 || maxEncodedImageBytes <= 0
                || maxPageEncodedImageBytes <= 0 || maxDocumentEncodedImageBytes <= 0) {
            throw new IllegalArgumentException("PDF visual extraction limits must be positive");
        }
        if (maxImagesPerDocument < maxImagesPerPage
                || maxSourceImagePixels > Math.multiplyExact(
                        (long) maxSourceImageDimension, maxSourceImageDimension)
                || maxPageSourceImagePixels < maxSourceImagePixels
                || maxDocumentSourceImagePixels < maxPageSourceImagePixels
                || maxPageEncodedImageBytes < maxEncodedImageBytes
                || maxDocumentEncodedImageBytes < maxPageEncodedImageBytes) {
            throw new IllegalArgumentException("PDF visual extraction limits must be consistently ordered");
        }
    }
}
