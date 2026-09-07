package com.hippocampus.materials.application;

import java.util.ArrayList;
import java.util.Objects;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.hippocampus.materials.domain.DetectedDocumentStructure;
import com.hippocampus.materials.domain.StructureFallbackPolicy;
import com.hippocampus.materials.domain.TextBlockType;
import com.hippocampus.materials.port.DocumentStructureRepository;
import com.hippocampus.materials.port.StructureFallback;
import com.hippocampus.materials.port.StructureFallbackRequest;

public final class ApplyStructureFallback {
    private final DocumentStructureRepository repository;
    private final StructureFallback fallback;
    private final StructureFallbackPolicy policy = new StructureFallbackPolicy();
    private final StructureFallbackValidator validator = new StructureFallbackValidator();

    public ApplyStructureFallback(DocumentStructureRepository repository, StructureFallback fallback) {
        this.repository = Objects.requireNonNull(repository);
        this.fallback = Objects.requireNonNull(fallback);
    }

    public DetectedDocumentStructure execute(DetectedDocumentStructure deterministic) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Structure fallback must run outside a transaction");
        }
        var nodes = new ArrayList<>(deterministic.nodes());
        for (int index : policy.ambiguousNodes(deterministic)) {
            try {
                int page = nodes.get(index).startPage();
                var request = request(deterministic, page, nodes.get(index).title());
                var response = fallback.detect(request);
                if (response.isPresent()) {
                    nodes.set(index, validator.convert(request, response.get(), nodes, index));
                }
            } catch (RuntimeException ignored) {
                // Optional assistance must never prevent durable deterministic extraction.
                // Do not log provider output or source text, which may contain private material.
            }
        }
        return nodes.equals(deterministic.nodes()) ? deterministic : new DetectedDocumentStructure(
                deterministic.materialVersionId(), deterministic.rootId(), deterministic.pageCount(), nodes);
    }

    private StructureFallbackRequest request(DetectedDocumentStructure structure, int page, String ambiguousHeading) {
        int first = Math.max(1, page - 1);
        int last = Math.min(structure.pageCount(), page + 1);
        var blocks = repository.findTextBlocksByOrdinalRange(structure.materialVersionId(), first, last);
        if (blocks.size() != last - first + 1) {
            throw new IllegalArgumentException("Incomplete local page evidence");
        }
        var pages = new ArrayList<StructureFallbackRequest.PageText>();
        for (var block : blocks) {
            int expected = first + pages.size();
            if (!structure.materialVersionId().equals(block.materialVersionId())
                    || !structure.rootId().equals(block.documentNodeId())
                    || block.blockType() != TextBlockType.PAGE_TEXT || block.pageNumber() == null
                    || block.pageNumber() != expected || block.ordinal() != expected || block.content() == null) {
                throw new IllegalArgumentException("Invalid local page evidence");
            }
            String content = block.content();
            pages.add(new StructureFallbackRequest.PageText(expected,
                    content.substring(0, Math.min(content.length(), StructureFallbackRequest.MAX_CHARACTERS_PER_PAGE))));
        }
        return new StructureFallbackRequest(page, ambiguousHeading, pages);
    }
}
