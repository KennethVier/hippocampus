package com.hippocampus.materials.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record DetectedDocumentStructure(
        UUID materialVersionId,
        UUID rootId,
        int pageCount,
        List<Node> nodes) {
    public DetectedDocumentStructure {
        Objects.requireNonNull(materialVersionId);
        Objects.requireNonNull(rootId);
        if (pageCount < 1) {
            throw new IllegalArgumentException("pageCount must be positive");
        }
        nodes = List.copyOf(Objects.requireNonNull(nodes));
        for (int index = 0; index < nodes.size(); index++) {
            Node node = nodes.get(index);
            if (node.parentIndex() != null && (node.parentIndex() < 0 || node.parentIndex() >= index)) {
                throw new IllegalArgumentException("node parent must precede its child");
            }
            if (node.startPage() > pageCount || node.endPage() > pageCount) {
                throw new IllegalArgumentException("node page range must fit the document");
            }
        }
    }

    public record Node(
            Integer parentIndex,
            DocumentNodeType nodeType,
            String title,
            int ordinal,
            int startPage,
            int endPage,
            DocumentNodeDetectionOrigin detectionOrigin,
            String detectionConfidence) {
        public Node {
            if (nodeType != DocumentNodeType.CHAPTER
                    && nodeType != DocumentNodeType.SECTION
                    && nodeType != DocumentNodeType.SUBSECTION) {
                throw new IllegalArgumentException("unsupported detected node type");
            }
            if (title == null || title.isBlank() || title.length() > PdfStructureSignals.MAX_TITLE_CHARACTERS) {
                throw new IllegalArgumentException("node title must be bounded and non-blank");
            }
            if (ordinal < 1 || startPage < 1 || endPage < startPage) {
                throw new IllegalArgumentException("node order and range must be valid");
            }
            if (detectionOrigin != DocumentNodeDetectionOrigin.NATIVE
                    && detectionOrigin != DocumentNodeDetectionOrigin.HEURISTIC
                    && detectionOrigin != DocumentNodeDetectionOrigin.AI_ASSISTED) {
                throw new IllegalArgumentException("unsupported detected provenance");
            }
            if (!"HIGH".equals(detectionConfidence)
                    && !"MEDIUM".equals(detectionConfidence)
                    && !"LOW".equals(detectionConfidence)) {
                throw new IllegalArgumentException("unsupported structure confidence");
            }
        }
    }
}
