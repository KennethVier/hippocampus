package com.hippocampus.bootstrap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.hippocampus.learning.domain.MissionMaterial;
import com.hippocampus.learning.port.ActivityEvidencePort;
import com.hippocampus.materials.domain.SourceReference;
import com.hippocampus.rag.application.BuildEvidencePackage;
import com.hippocampus.rag.application.BuildRetrievalScope;
import com.hippocampus.rag.application.HybridCandidate;
import com.hippocampus.rag.application.HybridCandidateMerger;
import com.hippocampus.rag.application.MaterializeEvidenceSourceReferences;
import com.hippocampus.rag.domain.EvidencePackage;
import com.hippocampus.rag.domain.EvidencePackageBudget;
import com.hippocampus.rag.domain.GroundingMode;
import com.hippocampus.rag.domain.RetrievalQuality;
import com.hippocampus.rag.domain.RetrievalScope;
import com.hippocampus.rag.domain.RetrievalScopeTarget;
import com.hippocampus.rag.port.ActiveIndexGenerationRepository;
import com.hippocampus.rag.port.EmbeddingBatchRequest;
import com.hippocampus.rag.port.EmbeddingBatchResult;
import com.hippocampus.rag.port.EmbeddingFailureException;
import com.hippocampus.rag.port.EmbeddingInput;
import com.hippocampus.rag.port.EmbeddingPort;
import com.hippocampus.rag.port.EmbeddingVector;
import com.hippocampus.rag.port.IndexGeneration;
import com.hippocampus.rag.port.LexicalSearchHit;
import com.hippocampus.rag.port.LexicalSearchRepository;
import com.hippocampus.rag.port.LexicalSearchRequest;
import com.hippocampus.rag.port.VectorSearchHit;
import com.hippocampus.rag.port.VectorSearchRepository;
import com.hippocampus.rag.port.VectorSearchRequest;

public final class LearningActivityEvidenceAdapter implements ActivityEvidencePort {

    private final BuildRetrievalScope buildScope;
    private final LexicalSearchRepository lexicalSearch;
    private final VectorSearchRepository vectorSearch;
    private final ActiveIndexGenerationRepository generations;
    private final Optional<EmbeddingPort> embeddingPort;
    private final BuildEvidencePackage buildEvidencePackage;
    private final MaterializeEvidenceSourceReferences materializeSourceReferences;
    private final ActivityEvidenceRetrievalOptions options;
    private final HybridCandidateMerger merger = new HybridCandidateMerger();

    public LearningActivityEvidenceAdapter(
            BuildRetrievalScope buildScope,
            LexicalSearchRepository lexicalSearch,
            VectorSearchRepository vectorSearch,
            ActiveIndexGenerationRepository generations,
            Optional<EmbeddingPort> embeddingPort,
            BuildEvidencePackage buildEvidencePackage,
            MaterializeEvidenceSourceReferences materializeSourceReferences,
            ActivityEvidenceRetrievalOptions options) {
        this.buildScope = Objects.requireNonNull(buildScope, "buildScope must not be null");
        this.lexicalSearch = Objects.requireNonNull(lexicalSearch, "lexicalSearch must not be null");
        this.vectorSearch = Objects.requireNonNull(vectorSearch, "vectorSearch must not be null");
        this.generations = Objects.requireNonNull(generations, "generations must not be null");
        this.embeddingPort = Objects.requireNonNull(embeddingPort, "embeddingPort must not be null");
        this.buildEvidencePackage = Objects.requireNonNull(
                buildEvidencePackage, "buildEvidencePackage must not be null");
        this.materializeSourceReferences = Objects.requireNonNull(
                materializeSourceReferences, "materializeSourceReferences must not be null");
        this.options = Objects.requireNonNull(options, "options must not be null");
    }

    @Override
    public Evidence retrieve(Request request) {
        Objects.requireNonNull(request, "request must not be null");
        GroundingMode groundingMode = GroundingMode.valueOf(request.groundingMode().name());
        RetrievalScope authorized = buildScope.execute(
                new BuildRetrievalScope.Query(request.topicId(), groundingMode));
        if (!authorized.userId().equals(request.userId())) {
            throw new IllegalStateException("authenticated retrieval scope does not match mission owner");
        }
        RetrievalScope frozenScope = frozenMissionScope(authorized, request.materialScopes());
        String query = retrievalQuery(request);
        if (query.length() > options.maxQueryLength()) {
            throw new IllegalArgumentException("activity evidence query exceeds configured maximum length");
        }

        List<HybridCandidate> candidates = retrieveCandidates(frozenScope, query);
        RetrievalQuality quality = retrievalQuality(candidates);
        EvidencePackage evidencePackage = buildEvidencePackage.execute(new BuildEvidencePackage.Command(
                frozenScope,
                candidates,
                quality,
                new EvidencePackageBudget(options.maxChunks(), options.maxVisuals())));
        Set<UUID> sourceReferenceIds = materializeSourceReferences.execute(evidencePackage).stream()
                .map(SourceReference::sourceReferenceId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new Evidence(sourceReferenceIds, new RagActivityEvidencePayload(evidencePackage));
    }

    private List<HybridCandidate> retrieveCandidates(RetrievalScope scope, String query) {
        if (scope.isEmpty()) {
            return List.of();
        }
        List<LexicalSearchHit> lexical = List.copyOf(lexicalSearch.search(
                new LexicalSearchRequest(scope, query, options.lexicalLimit())));
        List<VectorSearchHit> vector = vectorHits(scope, query);
        return merger.merge(lexical, vector, options.hybridLimit());
    }

    private List<VectorSearchHit> vectorHits(RetrievalScope scope, String query) {
        Optional<IndexGeneration> active = generations.findActiveGeneration();
        if (active.isEmpty() || embeddingPort.isEmpty()) {
            return List.of();
        }
        UUID correlationId = UUID.randomUUID();
        EmbeddingBatchResult result;
        try {
            result = embeddingPort.orElseThrow().embed(new EmbeddingBatchRequest(
                    List.of(new EmbeddingInput(correlationId, query))));
        } catch (EmbeddingFailureException failure) {
            return List.of();
        }
        IndexGeneration generation = active.orElseThrow();
        if (!generation.model().equals(result.model())
                || result.vectors().size() != 1
                || !result.vectors().getFirst().referenceId().equals(correlationId)) {
            return List.of();
        }
        EmbeddingVector vector = result.vectors().getFirst().vector();
        if (vector.dimension() != generation.model().dimension()
                || vector.values().stream().allMatch(value -> value == 0.0F)) {
            return List.of();
        }
        return List.copyOf(vectorSearch.search(new VectorSearchRequest(
                scope, generation.id(), vector, options.vectorLimit())));
    }

    private static RetrievalScope frozenMissionScope(
            RetrievalScope authorized, List<MissionMaterial> materialScopes) {
        Map<UUID, List<MissionMaterial>> missionByVersion = new HashMap<>();
        for (MissionMaterial material : materialScopes) {
            missionByVersion.computeIfAbsent(material.materialVersionId(), ignored -> new ArrayList<>())
                    .add(material);
        }
        List<RetrievalScopeTarget> targets = new ArrayList<>();
        for (RetrievalScopeTarget authorizedTarget : authorized.targets()) {
            List<MissionMaterial> missionTargets = missionByVersion.get(authorizedTarget.materialVersionId());
            if (missionTargets == null) {
                continue;
            }
            boolean missionAllowsWholeVersion = missionTargets.stream()
                    .anyMatch(material -> material.documentNodeId() == null);
            if (authorizedTarget.allowsWholeMaterialVersion()) {
                Set<UUID> nodes = missionAllowsWholeVersion
                        ? Set.of()
                        : missionNodes(missionTargets);
                targets.add(new RetrievalScopeTarget(authorizedTarget.materialVersionId(), nodes));
                continue;
            }
            Set<UUID> nodes = new HashSet<>(authorizedTarget.documentNodeIds());
            if (!missionAllowsWholeVersion) {
                nodes.retainAll(missionNodes(missionTargets));
            }
            if (!nodes.isEmpty()) {
                targets.add(new RetrievalScopeTarget(authorizedTarget.materialVersionId(), nodes));
            }
        }
        return new RetrievalScope(
                authorized.userId(), authorized.topicId(), authorized.groundingMode(), targets);
    }

    private static Set<UUID> missionNodes(List<MissionMaterial> missionTargets) {
        LinkedHashSet<UUID> nodes = new LinkedHashSet<>();
        missionTargets.stream()
                .map(MissionMaterial::documentNodeId)
                .filter(Objects::nonNull)
                .forEach(nodes::add);
        return Set.copyOf(nodes);
    }

    private static String retrievalQuery(Request request) {
        String objective = request.objective().objectiveText();
        String conceptKey = request.action().conceptKey();
        if (conceptKey == null || conceptKey.isBlank() || objective.contains(conceptKey)) {
            return objective;
        }
        return objective + " " + conceptKey;
    }

    private static RetrievalQuality retrievalQuality(List<HybridCandidate> candidates) {
        if (candidates.isEmpty()) {
            return RetrievalQuality.INSUFFICIENT;
        }
        boolean limited = candidates.stream().anyMatch(candidate ->
                "LIMITED".equals(candidate.quality()) || "POOR".equals(candidate.quality()));
        return limited ? RetrievalQuality.LIMITED : RetrievalQuality.STRONG;
    }
}
