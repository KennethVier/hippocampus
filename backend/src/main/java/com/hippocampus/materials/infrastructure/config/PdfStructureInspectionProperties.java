package com.hippocampus.materials.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hippocampus.materials.processing.pdf.structure")
public record PdfStructureInspectionProperties(
        int maxOutlineItems,
        int maxOutlineDepth,
        int maxTextPositionsPerPage,
        int maxLayoutLinesPerPage,
        int maxCandidatesPerDocument,
        int maxNodesPerDocument) {
    public PdfStructureInspectionProperties {
        if (maxOutlineItems <= 0 || maxOutlineDepth <= 0 || maxTextPositionsPerPage <= 0
                || maxLayoutLinesPerPage <= 0 || maxCandidatesPerDocument <= 0 || maxNodesPerDocument <= 0) {
            throw new IllegalArgumentException("PDF structure limits must be positive");
        }
        if (maxNodesPerDocument > maxCandidatesPerDocument) {
            throw new IllegalArgumentException("max-nodes-per-document must not exceed max-candidates-per-document");
        }
    }
}
