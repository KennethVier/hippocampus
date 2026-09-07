package com.hippocampus.materials.application;

import java.util.List;

import com.hippocampus.materials.domain.DetectedDocumentStructure.Node;
import com.hippocampus.materials.domain.DocumentNodeDetectionOrigin;
import com.hippocampus.materials.domain.DocumentNodeType;
import com.hippocampus.materials.domain.PdfStructureSignals;
import com.hippocampus.materials.port.StructureFallbackRequest;
import com.hippocampus.materials.port.StructureFallbackResponse;

public final class StructureFallbackValidator {
    public Node convert(StructureFallbackRequest request, StructureFallbackResponse response,
            List<Node> nodes, int index) {
        if (response == null
                || !request.contractVersion().equals(response.contractVersion())
                || !request.promptVersion().equals(response.promptVersion())
                || !request.schemaVersion().equals(response.schemaVersion())
                || response.heading() == null) {
            throw new IllegalArgumentException("Unsupported structure response contract");
        }
        var heading = response.heading();
        Node original = nodes.get(index);
        if (!"LOW".equals(original.detectionConfidence())
                || heading.pageNumber() == null || heading.pageNumber() != request.headingPage()
                || original.startPage() != request.headingPage()
                || !original.title().equals(request.ambiguousHeading())
                || heading.title() == null
                || heading.title().length() > PdfStructureSignals.MAX_TITLE_CHARACTERS
                || heading.title().isBlank()
                || !heading.title().equals(heading.title().strip())
                || heading.title().chars().anyMatch(Character::isISOControl)
                || request.pages().stream().filter(page -> page.pageNumber() == request.headingPage())
                        .noneMatch(page -> page.text().lines().anyMatch(line -> line.strip().equals(heading.title())))) {
            throw new IllegalArgumentException("Heading must be grounded in the ambiguous page sample");
        }
        Node converted = new Node(original.parentIndex(), heading.nodeType(), heading.title(),
                original.ordinal(), original.startPage(), original.endPage(),
                DocumentNodeDetectionOrigin.AI_ASSISTED, "LOW");
        DocumentNodeType parent = original.parentIndex() == null
                ? DocumentNodeType.DOCUMENT : nodes.get(original.parentIndex()).nodeType();
        if (!validChild(parent, converted.nodeType())) {
            throw new IllegalArgumentException("Heading conflicts with existing parent");
        }
        for (Node child : nodes) {
            if (child != original && child.startPage() == converted.startPage()
                    && child.title().strip().equalsIgnoreCase(converted.title())) {
                throw new IllegalArgumentException("Heading duplicates another existing node");
            }
            if (Integer.valueOf(index).equals(child.parentIndex())
                    && !validChild(converted.nodeType(), child.nodeType())) {
                throw new IllegalArgumentException("Heading conflicts with existing children");
            }
        }
        return converted;
    }

    private static boolean validChild(DocumentNodeType parent, DocumentNodeType child) {
        return switch (parent) {
            case DOCUMENT -> child == DocumentNodeType.CHAPTER || child == DocumentNodeType.SECTION;
            case CHAPTER -> child == DocumentNodeType.SECTION;
            case SECTION -> child == DocumentNodeType.SUBSECTION;
            default -> false;
        };
    }
}
