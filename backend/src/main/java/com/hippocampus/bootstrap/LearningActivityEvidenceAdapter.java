package com.hippocampus.bootstrap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
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

    private final com.hippocampus.identity.port.CurrentUser initialUser;
    private final com.hippocampus.learning.port.StudyMissionRepository initialMissions;
    private final com.hippocampus.learning.port.StudyMissionStartReceiptRepository initialReceipts;
    private final com.hippocampus.learning.port.InitialStudyMissionSourceAuthority initialSources;
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
        this(buildScope,lexicalSearch,vectorSearch,generations,embeddingPort,buildEvidencePackage,
                materializeSourceReferences,options,null,null,null,null);
    }

    public LearningActivityEvidenceAdapter(BuildRetrievalScope buildScope,LexicalSearchRepository lexicalSearch,
            VectorSearchRepository vectorSearch,ActiveIndexGenerationRepository generations,Optional<EmbeddingPort> embeddingPort,
            BuildEvidencePackage buildEvidencePackage,MaterializeEvidenceSourceReferences materializeSourceReferences,
            ActivityEvidenceRetrievalOptions options,com.hippocampus.identity.port.CurrentUser initialUser,
            com.hippocampus.learning.port.StudyMissionRepository initialMissions,
            com.hippocampus.learning.port.StudyMissionStartReceiptRepository initialReceipts,
            com.hippocampus.learning.port.InitialStudyMissionSourceAuthority initialSources) {
        this.initialUser=initialUser;this.initialMissions=initialMissions;
        this.initialReceipts=initialReceipts;this.initialSources=initialSources;
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
        return retrieve(request,frozenScope,null,()->{});
    }

    @Override
    public Evidence retrieve(Request request,com.hippocampus.learning.port.StudyMissionStartReceiptRepository.InitialContext context) {
        var mission=authorizeInitial(request,context);
        var byVersion=new LinkedHashMap<UUID,Set<UUID>>();
        Set<UUID> whole=new HashSet<>();
        for(var material:mission.materials()) {
            if(material.documentNodeId()==null) { whole.add(material.materialVersionId());byVersion.put(material.materialVersionId(),Set.of()); }
            else if(!whole.contains(material.materialVersionId())) byVersion.computeIfAbsent(material.materialVersionId(),v->new HashSet<>()).add(material.documentNodeId());
        }
        var scope=new RetrievalScope(mission.userId(),mission.topicId(),GroundingMode.valueOf(mission.groundingMode().name()),
                byVersion.entrySet().stream().map(e->new RetrievalScopeTarget(e.getKey(),e.getValue())).toList());
        var binding=new com.hippocampus.rag.port.FrozenMissionSourceBinding(context.missionId(),context.generation(),context.claimToken());
        return retrieve(request,scope,binding,()->authorizeInitial(request,context));
    }

    private com.hippocampus.learning.domain.StudyMission authorizeInitial(Request request,
            com.hippocampus.learning.port.StudyMissionStartReceiptRepository.InitialContext context) {
        if(initialUser==null || initialMissions==null || initialReceipts==null || initialSources==null)
            throw new IllegalStateException("Initial retrieval authorization is not configured");
        Objects.requireNonNull(request);Objects.requireNonNull(context);
        UUID owner=initialUser.authenticatedUser().userId();
        if(!owner.equals(request.userId()) || !owner.equals(context.ownerId()) || !request.missionId().equals(context.missionId()))
            throw initialUnavailable();
        var mission=initialMissions.findOwnedById(context.missionId(),owner).orElseThrow(LearningActivityEvidenceAdapter::initialUnavailable);
        var receipt=initialReceipts.findByMission(owner,mission.id()).orElseThrow(LearningActivityEvidenceAdapter::initialUnavailable);
        if(!receipt.matches(context,initialReceipts.databaseTime()) || mission.status()!=com.hippocampus.learning.domain.StudyMissionStatus.ACTIVE
                || mission.currentActivityId()!=null || !mission.activities().isEmpty() || mission.objectives().size()!=1
                || !mission.topicId().equals(request.topicId()) || mission.groundingMode()!=request.groundingMode()
                || !mission.updatedAt().equals(context.snapshot().updatedAt())
                || !mission.objectives().getFirst().equals(request.objective())
                || !mission.objectives().getFirst().id().equals(context.snapshot().objectiveId())
                || !com.hippocampus.learning.application.InitialStudyMissionStateAssembler.frozenFingerprint(mission)
                    .equals(context.snapshot().frozenBindingFingerprint())
                || request.materialScopes().size()!=mission.materials().size()
                || !new HashSet<>(request.materialScopes()).equals(new HashSet<>(mission.materials()))
                || !request.action().equals(context.selectedAction())) throw initialUnavailable();
        var identity=new com.hippocampus.learning.application.StartStudyMissionExperienceUseCase.Command(receipt.startRequestId(),
                mission.topicId(),mission.objectives().getFirst().objectiveText(),mission.availableTimeMinutes(),
                mission.materials().stream().map(MissionMaterial::materialId).toList());
        if(!receipt.requestFingerprint().equals(identity.fingerprint(owner))) throw initialUnavailable();
        initialSources.inspect(owner,mission.materials());
        return mission;
    }

    private static IllegalStateException initialUnavailable() { return new IllegalStateException("Initial source authorization changed"); }

    private Evidence retrieve(Request request,RetrievalScope frozenScope,
            com.hippocampus.rag.port.FrozenMissionSourceBinding binding,Runnable authorize) {
        authorize.run();
        String query = retrievalQuery(request);
        if (query.length() > options.maxQueryLength()) {
            throw new IllegalArgumentException("activity evidence query exceeds configured maximum length");
        }

        List<HybridCandidate> candidates = retrieveCandidates(frozenScope, query,binding,authorize);
        RetrievalQuality quality = retrievalQuality(candidates);
        authorize.run();
        EvidencePackage evidencePackage = buildEvidencePackage.execute(new BuildEvidencePackage.Command(
                frozenScope,
                candidates,
                quality,
                new EvidencePackageBudget(options.maxChunks(), options.maxVisuals()),binding));
        authorize.run();
        Set<UUID> sourceReferenceIds = (binding==null ? materializeSourceReferences.execute(evidencePackage)
                : materializeSourceReferences.execute(evidencePackage,binding)).stream()
                .map(SourceReference::sourceReferenceId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        authorize.run();
        return new Evidence(sourceReferenceIds, new RagActivityEvidencePayload(evidencePackage));
    }

    private List<HybridCandidate> retrieveCandidates(RetrievalScope scope,String query,
            com.hippocampus.rag.port.FrozenMissionSourceBinding binding,Runnable authorize) {
        if (scope.isEmpty()) {
            return List.of();
        }
        List<LexicalSearchHit> lexical = List.copyOf(lexicalSearch.search(
                new LexicalSearchRequest(scope, query, options.lexicalLimit(),binding)));
        List<VectorSearchHit> vector = vectorHits(scope, query,binding,authorize);
        return merger.merge(lexical, vector, options.hybridLimit());
    }

    private List<VectorSearchHit> vectorHits(RetrievalScope scope,String query,
            com.hippocampus.rag.port.FrozenMissionSourceBinding binding,Runnable authorize) {
        Optional<IndexGeneration> active = generations.findActiveGeneration();
        if (active.isEmpty() || embeddingPort.isEmpty()) {
            return List.of();
        }
        authorize.run();
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
                scope, generation.id(), vector, options.vectorLimit(),binding)));
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
