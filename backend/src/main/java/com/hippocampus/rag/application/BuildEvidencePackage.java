package com.hippocampus.rag.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.hippocampus.rag.domain.EvidenceChunk;
import com.hippocampus.rag.domain.EvidenceLimitation;
import com.hippocampus.rag.domain.EvidenceLimitationCode;
import com.hippocampus.rag.domain.EvidencePackage;
import com.hippocampus.rag.domain.EvidencePackageBudget;
import com.hippocampus.rag.domain.EvidenceReferenceKind;
import com.hippocampus.rag.domain.EvidenceSourceReference;
import com.hippocampus.rag.domain.EvidenceVisual;
import com.hippocampus.rag.domain.RetrievalDiagnostics;
import com.hippocampus.rag.domain.RetrievalQuality;
import com.hippocampus.rag.domain.RetrievalScope;
import com.hippocampus.rag.port.EvidenceVisualRepository;
import com.hippocampus.rag.port.EvidenceVisualSource;

public final class BuildEvidencePackage {
    private final EvidenceVisualRepository visuals;

    public BuildEvidencePackage(EvidenceVisualRepository visuals) {
        this.visuals = Objects.requireNonNull(visuals);
    }

    public EvidencePackage execute(Command command) {
        Objects.requireNonNull(command, "command must not be null");
        validateCandidates(command.scope(), command.rankedCandidates());
        if (command.quality() == RetrievalQuality.FAILED) {
            if (!command.rankedCandidates().isEmpty()) {
                throw new IllegalArgumentException("FAILED retrieval must not contain candidates");
            }
            return emptyPackage(command, EvidenceLimitationCode.RETRIEVAL_FAILED);
        }

        int selectedCount = Math.min(command.budget().maxChunks(), command.rankedCandidates().size());
        if ((command.quality() == RetrievalQuality.STRONG || command.quality() == RetrievalQuality.LIMITED)
                && selectedCount == 0) {
            throw new IllegalArgumentException(command.quality() + " retrieval requires selected chunk evidence");
        }

        List<HybridCandidate> selectedCandidates = command.rankedCandidates().subList(0, selectedCount);
        List<EvidenceChunk> chunks = new ArrayList<>(selectedCount);
        LinkedHashSet<EvidenceLimitationCode> limitationCodes = new LinkedHashSet<>();
        if (selectedCount < command.rankedCandidates().size()) {
            limitationCodes.add(EvidenceLimitationCode.CHUNK_BUDGET_APPLIED);
        }
        for (int index = 0; index < selectedCandidates.size(); index++) {
            HybridCandidate candidate = selectedCandidates.get(index);
            chunks.add(toEvidenceChunk(candidate, index + 1));
            if ("LIMITED".equals(candidate.quality())) {
                limitationCodes.add(EvidenceLimitationCode.LIMITED_CHUNK_SOURCE_QUALITY);
            } else if ("POOR".equals(candidate.quality())) {
                limitationCodes.add(EvidenceLimitationCode.POOR_CHUNK_SOURCE_QUALITY);
            }
        }

        Map<UUID, SelectedChunk> selectedByChunk = new LinkedHashMap<>();
        selectedCandidates.forEach(candidate -> selectedByChunk.put(
                candidate.chunkId(), new SelectedChunk(selectedByChunk.size() + 1, candidate)));
        List<EvidenceVisual> eligibleVisuals = collectVisuals(command.scope(), selectedByChunk, limitationCodes);
        if (eligibleVisuals.size() > command.budget().maxVisuals()) {
            limitationCodes.add(EvidenceLimitationCode.VISUAL_BUDGET_APPLIED);
        }
        List<EvidenceVisual> selectedVisuals = List.copyOf(eligibleVisuals.subList(
                0, Math.min(command.budget().maxVisuals(), eligibleVisuals.size())));
        if (command.quality() == RetrievalQuality.INSUFFICIENT && chunks.isEmpty()) {
            limitationCodes.add(EvidenceLimitationCode.INSUFFICIENT_EVIDENCE);
        }

        List<EvidenceSourceReference> references = sourceReferences(chunks, selectedVisuals);
        RetrievalDiagnostics diagnostics = diagnostics(command, chunks, selectedVisuals, selectedCandidates);
        return new EvidencePackage(
                command.quality(), command.scope().groundingMode(), chunks, selectedVisuals,
                references, limitations(limitationCodes), diagnostics);
    }

    private EvidencePackage emptyPackage(Command command, EvidenceLimitationCode limitation) {
        RetrievalDiagnostics diagnostics = new RetrievalDiagnostics(
                command.rankedCandidates().size(), 0, List.of(), List.of(), Set.of(), Set.of(), command.quality());
        return new EvidencePackage(
                command.quality(), command.scope().groundingMode(), List.of(), List.of(), List.of(),
                List.of(new EvidenceLimitation(limitation)), diagnostics);
    }

    private static void validateCandidates(RetrievalScope scope, List<HybridCandidate> candidates) {
        Set<UUID> chunkIds = new LinkedHashSet<>();
        for (HybridCandidate candidate : candidates) {
            if (!chunkIds.add(candidate.chunkId())) {
                throw new IllegalArgumentException("rankedCandidates must not contain duplicate chunkIds");
            }
            if (!scope.allows(candidate.materialVersionId(), candidate.documentNodeId())) {
                throw new IllegalArgumentException("ranked candidate is outside RetrievalScope");
            }
        }
    }

    private static EvidenceChunk toEvidenceChunk(HybridCandidate candidate, int rank) {
        return new EvidenceChunk(
                rank, candidate.chunkId(), candidate.materialId(), candidate.materialVersionId(),
                candidate.documentNodeId(), candidate.chunkIndex(), candidate.content(), candidate.pageStart(),
                candidate.pageEnd(), candidate.headingPath(), candidate.contentType(), candidate.extractionMethod(),
                candidate.quality());
    }

    private List<EvidenceVisual> collectVisuals(
            RetrievalScope scope,
            Map<UUID, SelectedChunk> selectedByChunk,
            LinkedHashSet<EvidenceLimitationCode> limitations) {
        if (selectedByChunk.isEmpty()) {
            return List.of();
        }
        List<EvidenceVisualSource> sources = Objects.requireNonNull(
                visuals.findLinkedVisuals(scope, Set.copyOf(selectedByChunk.keySet())),
                "visual repository must not return null");
        Map<UUID, VisualAccumulator> byVisual = new LinkedHashMap<>();
        Map<UUID, EvidenceVisualSource> canonicalSources = new LinkedHashMap<>();
        for (EvidenceVisualSource source : sources) {
            Objects.requireNonNull(source, "visual repository rows must not contain null");
            SelectedChunk selected = selectedByChunk.get(source.selectedChunkId());
            if (selected == null
                    || !selected.candidate().materialId().equals(source.materialId())
                    || !selected.candidate().materialVersionId().equals(source.materialVersionId())
                    || !scope.allows(source.materialVersionId(), source.documentNodeId())) {
                throw new IllegalStateException("visual repository returned evidence outside selected authorized scope");
            }
            EvidenceVisualSource canonical = canonicalSources.putIfAbsent(source.visualId(), source);
            if (canonical != null && !VisualAccumulator.sameMetadata(canonical, source)) {
                throw new IllegalStateException("conflicting metadata for linked visual " + source.visualId());
            }
            switch (source.interpretationStatus()) {
                case "SUPPORTED" -> byVisual.computeIfAbsent(
                        source.visualId(), ignored -> new VisualAccumulator(source, selected.rank()))
                        .add(source, selected.rank());
                case "LIMITED" -> {
                    limitations.add(EvidenceLimitationCode.LIMITED_VISUAL_INTERPRETATION);
                    byVisual.computeIfAbsent(
                            source.visualId(), ignored -> new VisualAccumulator(source, selected.rank()))
                            .add(source, selected.rank());
                }
                case "UNASSESSED", "UNSUPPORTED", "FAILED" ->
                        limitations.add(EvidenceLimitationCode.VISUAL_EXCLUDED_BY_INTERPRETATION_STATUS);
                default -> throw new IllegalStateException("visual repository returned unknown interpretation status");
            }
        }
        return byVisual.values().stream()
                .map(VisualAccumulator::toEvidenceVisual)
                .sorted(Comparator.comparingInt(VisualAccumulatorResult::firstRank)
                        .thenComparingInt(result -> result.visual().pageNumber())
                        .thenComparing(result -> result.visual().visualId()))
                .map(VisualAccumulatorResult::visual)
                .toList();
    }

    private static List<EvidenceSourceReference> sourceReferences(
            List<EvidenceChunk> chunks, List<EvidenceVisual> visuals) {
        List<EvidenceSourceReference> references = new ArrayList<>(chunks.size() + visuals.size());
        for (EvidenceChunk chunk : chunks) {
            Integer page = Objects.equals(chunk.pageStart(), chunk.pageEnd()) ? chunk.pageStart() : null;
            references.add(new EvidenceSourceReference(
                    EvidenceReferenceKind.CHUNK, chunk.materialId(), chunk.materialVersionId(),
                    chunk.documentNodeId(), chunk.chunkId(), null, page));
        }
        for (EvidenceVisual visual : visuals) {
            references.add(new EvidenceSourceReference(
                    EvidenceReferenceKind.VISUAL, visual.materialId(), visual.materialVersionId(),
                    visual.documentNodeId(), null, visual.visualId(), visual.pageNumber()));
        }
        return List.copyOf(references);
    }

    private static RetrievalDiagnostics diagnostics(
            Command command,
            List<EvidenceChunk> chunks,
            List<EvidenceVisual> visuals,
            List<HybridCandidate> selectedCandidates) {
        LinkedHashSet<UUID> sourceMaterials = new LinkedHashSet<>();
        chunks.forEach(chunk -> sourceMaterials.add(chunk.materialId()));
        visuals.forEach(visual -> sourceMaterials.add(visual.materialId()));
        LinkedHashSet<UUID> generations = new LinkedHashSet<>();
        selectedCandidates.forEach(candidate -> candidate.vectorSignal()
                .ifPresent(signal -> generations.add(signal.indexGenerationId())));
        return new RetrievalDiagnostics(
                command.rankedCandidates().size(), chunks.size(),
                chunks.stream().map(EvidenceChunk::chunkId).toList(),
                visuals.stream().map(EvidenceVisual::visualId).toList(),
                sourceMaterials, generations, command.quality());
    }

    private static List<EvidenceLimitation> limitations(Set<EvidenceLimitationCode> codes) {
        return codes.stream().map(EvidenceLimitation::new).toList();
    }

    public record Command(
            RetrievalScope scope,
            List<HybridCandidate> rankedCandidates,
            RetrievalQuality quality,
            EvidencePackageBudget budget) {
        public Command {
            Objects.requireNonNull(scope, "scope must not be null");
            rankedCandidates = List.copyOf(Objects.requireNonNull(
                    rankedCandidates, "rankedCandidates must not be null"));
            Objects.requireNonNull(quality, "quality must not be null");
            Objects.requireNonNull(budget, "budget must not be null");
        }
    }

    private static final class VisualAccumulator {
        private final EvidenceVisualSource canonical;
        private final Set<UUID> linkedChunkIds = new LinkedHashSet<>();
        private final Set<String> relationshipTypes = new LinkedHashSet<>();
        private int firstRank;

        private VisualAccumulator(EvidenceVisualSource canonical, int firstRank) {
            this.canonical = canonical;
            this.firstRank = firstRank;
        }

        private void add(EvidenceVisualSource source, int rank) {
            if (!sameMetadata(canonical, source)) {
                throw new IllegalStateException("conflicting metadata for linked visual " + canonical.visualId());
            }
            linkedChunkIds.add(source.selectedChunkId());
            relationshipTypes.add(source.relationshipType());
            firstRank = Math.min(firstRank, rank);
        }

        private VisualAccumulatorResult toEvidenceVisual() {
            return new VisualAccumulatorResult(firstRank, new EvidenceVisual(
                    canonical.visualId(), canonical.materialId(), canonical.materialVersionId(),
                    canonical.documentNodeId(), canonical.pageNumber(), canonical.visualType(), canonical.caption(),
                    canonical.nearbyText(), canonical.interpretationStatus(), linkedChunkIds, relationshipTypes));
        }

        private static boolean sameMetadata(EvidenceVisualSource left, EvidenceVisualSource right) {
            return left.visualId().equals(right.visualId())
                    && left.materialId().equals(right.materialId())
                    && left.materialVersionId().equals(right.materialVersionId())
                    && Objects.equals(left.documentNodeId(), right.documentNodeId())
                    && left.pageNumber() == right.pageNumber()
                    && left.visualType().equals(right.visualType())
                    && Objects.equals(left.caption(), right.caption())
                    && Objects.equals(left.nearbyText(), right.nearbyText())
                    && left.interpretationStatus().equals(right.interpretationStatus());
        }
    }

    private record VisualAccumulatorResult(int firstRank, EvidenceVisual visual) {}

    private record SelectedChunk(int rank, HybridCandidate candidate) {}
}
