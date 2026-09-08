package com.hippocampus.materials.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hippocampus.materials.processing.pdf.table")
public record PdfTableExtractionProperties(
        int maxTablesPerPage,
        int maxTablesPerDocument,
        int maxRowsPerTable,
        int maxColumnsPerTable,
        int maxTableTextChars) {
    public PdfTableExtractionProperties {
        if (maxTablesPerPage <= 0 || maxTablesPerDocument <= 0 || maxRowsPerTable <= 0
                || maxColumnsPerTable <= 0 || maxTableTextChars <= 0) {
            throw new IllegalArgumentException("PDF table extraction limits must be positive");
        }
        if (maxTablesPerDocument < maxTablesPerPage) {
            throw new IllegalArgumentException("max-tables-per-document must cover max-tables-per-page");
        }
    }
}
