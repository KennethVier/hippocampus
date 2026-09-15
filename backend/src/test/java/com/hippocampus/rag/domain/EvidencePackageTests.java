package com.hippocampus.rag.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EvidencePackageTests {

    @ParameterizedTest
    @CsvSource({"0,0", "-1,0", "1,-1"})
    void rejectsInvalidBudgets(int maxChunks, int maxVisuals) {
        assertThatThrownBy(() -> new EvidencePackageBudget(maxChunks, maxVisuals))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defensivelyCopiesEveryPackageCollection() {
        EvidenceChunk chunk = chunk();
        EvidenceSourceReference reference = chunkReference(chunk);
        ArrayList<EvidenceChunk> chunks = new ArrayList<>(List.of(chunk));
        ArrayList<EvidenceSourceReference> references = new ArrayList<>(List.of(reference));
        ArrayList<EvidenceLimitation> limitations = new ArrayList<>();
        ArrayList<UUID> selectedChunks = new ArrayList<>(List.of(chunk.chunkId()));
        LinkedHashSet<UUID> materials = new LinkedHashSet<>(Set.of(chunk.materialId()));
        RetrievalDiagnostics diagnostics = new RetrievalDiagnostics(
                1, 1, selectedChunks, List.of(), materials, Set.of(), RetrievalQuality.STRONG);

        EvidencePackage evidence = new EvidencePackage(
                RetrievalQuality.STRONG, GroundingMode.SOURCE_FIRST, chunks, List.of(), references,
                limitations, diagnostics);
        chunks.clear();
        references.clear();
        limitations.add(new EvidenceLimitation(EvidenceLimitationCode.CHUNK_BUDGET_APPLIED));
        selectedChunks.clear();
        materials.clear();

        assertThat(evidence.chunks()).containsExactly(chunk);
        assertThat(evidence.sourceReferences()).containsExactly(reference);
        assertThat(evidence.limitations()).isEmpty();
        assertThat(evidence.retrievalDiagnostics().selectedChunkIds()).containsExactly(chunk.chunkId());
        assertThat(evidence.retrievalDiagnostics().sourceMaterialIds()).containsExactly(chunk.materialId());
        assertThatThrownBy(() -> evidence.chunks().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> evidence.retrievalDiagnostics().sourceMaterialIds().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsDanglingDuplicateAndMismatchedReferences() {
        EvidenceChunk chunk = chunk();
        RetrievalDiagnostics diagnostics = diagnostics(chunk);
        EvidenceSourceReference valid = chunkReference(chunk);
        EvidenceSourceReference dangling = new EvidenceSourceReference(
                EvidenceReferenceKind.CHUNK, chunk.materialId(), chunk.materialVersionId(),
                chunk.documentNodeId(), UUID.randomUUID(), null, 4);
        EvidenceSourceReference wrongPage = new EvidenceSourceReference(
                EvidenceReferenceKind.CHUNK, chunk.materialId(), chunk.materialVersionId(),
                chunk.documentNodeId(), chunk.chunkId(), null, 5);

        assertThatThrownBy(() -> evidence(chunk, List.of()))
                .hasMessageContaining("exactly one source reference");
        assertThatThrownBy(() -> evidence(chunk, List.of(valid, valid)))
                .hasMessageContaining("duplicate chunk source reference");
        assertThatThrownBy(() -> evidence(chunk, List.of(dangling)))
                .hasMessageContaining("dangling");
        assertThatThrownBy(() -> evidence(chunk, List.of(wrongPage)))
                .hasMessageContaining("inconsistent");
        assertThat(diagnostics.selectedChunkCount()).isEqualTo(1);
    }

    @Test
    void rejectsDiagnosticsThatDoNotExactlyMatchEvidence() {
        EvidenceChunk chunk = chunk();
        RetrievalDiagnostics wrong = new RetrievalDiagnostics(
                1, 1, List.of(UUID.randomUUID()), List.of(), Set.of(chunk.materialId()), Set.of(),
                RetrievalQuality.STRONG);

        assertThatThrownBy(() -> new EvidencePackage(
                RetrievalQuality.STRONG, GroundingMode.STRICT_SOURCE, List.of(chunk), List.of(),
                List.of(chunkReference(chunk)), List.of(), wrong))
                .hasMessageContaining("diagnostics");
    }

    @Test
    void chunkReferenceRequiresOnlyChunkTargetAndVisualReferenceRequiresOnlyVisualTarget() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> new EvidenceSourceReference(
                EvidenceReferenceKind.CHUNK, id, id, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EvidenceSourceReference(
                EvidenceReferenceKind.VISUAL, id, id, null, id, id, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static EvidencePackage evidence(EvidenceChunk chunk, List<EvidenceSourceReference> references) {
        return new EvidencePackage(
                RetrievalQuality.STRONG, GroundingMode.STRICT_SOURCE, List.of(chunk), List.of(), references,
                List.of(), diagnostics(chunk));
    }

    private static RetrievalDiagnostics diagnostics(EvidenceChunk chunk) {
        return new RetrievalDiagnostics(
                1, 1, List.of(chunk.chunkId()), List.of(), Set.of(chunk.materialId()), Set.of(),
                RetrievalQuality.STRONG);
    }

    private static EvidenceChunk chunk() {
        return new EvidenceChunk(
                1, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1,
                "canonical", 4, 4, List.of("Heading"), "TEXT", "NATIVE", "STRONG");
    }

    private static EvidenceSourceReference chunkReference(EvidenceChunk chunk) {
        return new EvidenceSourceReference(
                EvidenceReferenceKind.CHUNK, chunk.materialId(), chunk.materialVersionId(),
                chunk.documentNodeId(), chunk.chunkId(), null, 4);
    }
}
