package com.hippocampus.rag.domain;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record EvidencePackage(
        RetrievalQuality quality,
        GroundingMode groundingMode,
        List<EvidenceChunk> chunks,
        List<EvidenceVisual> visuals,
        List<EvidenceSourceReference> sourceReferences,
        List<EvidenceLimitation> limitations,
        RetrievalDiagnostics retrievalDiagnostics) {

    public EvidencePackage {
        Objects.requireNonNull(quality, "quality must not be null");
        Objects.requireNonNull(groundingMode, "groundingMode must not be null");
        chunks = immutableList(chunks, "chunks");
        visuals = immutableList(visuals, "visuals");
        sourceReferences = immutableList(sourceReferences, "sourceReferences");
        limitations = immutableList(limitations, "limitations");
        Objects.requireNonNull(retrievalDiagnostics, "retrievalDiagnostics must not be null");
        validateEvidence(chunks, visuals, sourceReferences, quality, retrievalDiagnostics);
    }

    private static void validateEvidence(
            List<EvidenceChunk> chunks,
            List<EvidenceVisual> visuals,
            List<EvidenceSourceReference> references,
            RetrievalQuality quality,
            RetrievalDiagnostics diagnostics) {
        Map<UUID, EvidenceChunk> chunksById = uniqueChunks(chunks);
        Map<UUID, EvidenceVisual> visualsById = uniqueVisuals(visuals);
        if (quality == RetrievalQuality.FAILED
                && (!chunks.isEmpty() || !visuals.isEmpty() || !references.isEmpty())) {
            throw new IllegalArgumentException("FAILED package must not contain evidence or references");
        }
        if (diagnostics.retrievalQuality() != quality
                || diagnostics.selectedChunkCount() != chunks.size()
                || !diagnostics.selectedChunkIds().equals(chunks.stream().map(EvidenceChunk::chunkId).toList())
                || !diagnostics.selectedVisualIds().equals(visuals.stream().map(EvidenceVisual::visualId).toList())) {
            throw new IllegalArgumentException("retrieval diagnostics do not match package evidence");
        }
        Set<UUID> actualMaterials = new HashSet<>();
        chunks.forEach(chunk -> actualMaterials.add(chunk.materialId()));
        visuals.forEach(visual -> actualMaterials.add(visual.materialId()));
        if (!diagnostics.sourceMaterialIds().equals(actualMaterials)) {
            throw new IllegalArgumentException("sourceMaterialIds do not match package evidence");
        }

        Set<UUID> chunkTargets = new HashSet<>();
        Set<UUID> visualTargets = new HashSet<>();
        for (EvidenceSourceReference reference : references) {
            if (reference.kind() == EvidenceReferenceKind.CHUNK) {
                if (!chunkTargets.add(reference.chunkId())) {
                    throw new IllegalArgumentException("duplicate chunk source reference");
                }
                EvidenceChunk chunk = chunksById.get(reference.chunkId());
                if (chunk == null || !matches(reference, chunk)) {
                    throw new IllegalArgumentException("dangling or inconsistent chunk source reference");
                }
            } else {
                if (!visualTargets.add(reference.visualId())) {
                    throw new IllegalArgumentException("duplicate visual source reference");
                }
                EvidenceVisual visual = visualsById.get(reference.visualId());
                if (visual == null || !matches(reference, visual)) {
                    throw new IllegalArgumentException("dangling or inconsistent visual source reference");
                }
            }
        }
        if (!chunkTargets.equals(chunksById.keySet()) || !visualTargets.equals(visualsById.keySet())) {
            throw new IllegalArgumentException("every evidence item must have exactly one source reference");
        }
    }

    private static Map<UUID, EvidenceChunk> uniqueChunks(List<EvidenceChunk> chunks) {
        Map<UUID, EvidenceChunk> result = new HashMap<>();
        for (int index = 0; index < chunks.size(); index++) {
            EvidenceChunk chunk = chunks.get(index);
            if (chunk.rank() != index + 1) {
                throw new IllegalArgumentException("chunk ranks must be one-based package positions");
            }
            if (result.put(chunk.chunkId(), chunk) != null) {
                throw new IllegalArgumentException("duplicate evidence chunk");
            }
        }
        return result;
    }

    private static Map<UUID, EvidenceVisual> uniqueVisuals(List<EvidenceVisual> visuals) {
        Map<UUID, EvidenceVisual> result = new HashMap<>();
        for (EvidenceVisual visual : visuals) {
            if (result.put(visual.visualId(), visual) != null) {
                throw new IllegalArgumentException("duplicate evidence visual");
            }
        }
        return result;
    }

    private static boolean matches(EvidenceSourceReference reference, EvidenceChunk chunk) {
        Integer page = Objects.equals(chunk.pageStart(), chunk.pageEnd()) ? chunk.pageStart() : null;
        return reference.materialId().equals(chunk.materialId())
                && reference.materialVersionId().equals(chunk.materialVersionId())
                && Objects.equals(reference.documentNodeId(), chunk.documentNodeId())
                && Objects.equals(reference.pageNumber(), page);
    }

    private static boolean matches(EvidenceSourceReference reference, EvidenceVisual visual) {
        return reference.materialId().equals(visual.materialId())
                && reference.materialVersionId().equals(visual.materialVersionId())
                && Objects.equals(reference.documentNodeId(), visual.documentNodeId())
                && Objects.equals(reference.pageNumber(), visual.pageNumber());
    }

    private static <T> List<T> immutableList(List<T> values, String name) {
        Objects.requireNonNull(values, name + " must not be null");
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException(name + " must not contain null");
        }
        return List.copyOf(values);
    }
}
