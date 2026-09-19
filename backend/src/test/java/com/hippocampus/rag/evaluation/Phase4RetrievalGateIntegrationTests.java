package com.hippocampus.rag.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.hippocampus.identity.infrastructure.security.HippocampusPrincipal;
import com.hippocampus.materials.application.ResolveSourceReference;
import com.hippocampus.materials.domain.SourceReference;
import com.hippocampus.rag.application.BuildEvidencePackage;
import com.hippocampus.rag.application.BuildRetrievalScope;
import com.hippocampus.rag.application.HybridCandidate;
import com.hippocampus.rag.application.HybridCandidateMerger;
import com.hippocampus.rag.application.MaterializeEvidenceSourceReferences;
import com.hippocampus.rag.domain.EvidencePackage;
import com.hippocampus.rag.domain.EvidencePackageBudget;
import com.hippocampus.rag.domain.EvidenceReferenceKind;
import com.hippocampus.rag.domain.EvidenceSourceReference;
import com.hippocampus.rag.domain.GroundingMode;
import com.hippocampus.rag.domain.RetrievalQuality;
import com.hippocampus.rag.domain.RetrievalScope;
import com.hippocampus.rag.infrastructure.persistence.JdbcActiveIndexGenerationRepository;
import com.hippocampus.rag.port.EmbeddingVector;
import com.hippocampus.rag.port.IndexGeneration;
import com.hippocampus.rag.port.LexicalSearchRepository;
import com.hippocampus.rag.port.LexicalSearchRequest;
import com.hippocampus.rag.port.VectorSearchRepository;
import com.hippocampus.rag.port.VectorSearchRequest;
import com.hippocampus.testing.PostgresIntegrationTestSupport;

class Phase4RetrievalGateIntegrationTests extends PostgresIntegrationTestSupport {
    private static final String DATASET_PATH = "/rag/golden-retrieval/v1/dataset.json";
    private static final String CORPUS_PATH = "/rag/golden-retrieval/v1/corpus.json";
    private static final String BASELINE_PATH = "/rag/golden-retrieval/v1/baseline.json";
    private static final int SEARCH_LIMIT = 10;

    private ConfigurableApplicationContext context;
    private JdbcClient jdbc;
    private GoldenRetrievalDataset dataset;
    private GoldenRetrievalCorpus corpus;
    private GoldenRetrievalFixtureSeeder seeder;
    private GoldenRetrievalBenchmarkContract.Baseline baseline;
    private UUID ownerId;

    @BeforeEach
    void setUp() throws IOException, SQLException {
        resetPostgresSchema();
        context = startApplicationWithFlywayAndArguments(new Class<?>[0],
                "--hippocampus.materials.processing.recovery.enabled=false");
        jdbc = context.getBean(JdbcClient.class);

        GoldenRetrievalDatasetLoader loader = new GoldenRetrievalDatasetLoader();
        dataset = loader.loadDataset(DATASET_PATH);
        corpus = loader.loadCorpus(CORPUS_PATH, dataset);
        baseline = GoldenRetrievalBenchmarkContract.load(
                new com.fasterxml.jackson.databind.ObjectMapper(), BASELINE_PATH);
        seeder = new GoldenRetrievalFixtureSeeder(jdbc);
        seeder.seed(corpus);
        ownerId = seeder.deriveUuid("user", corpus.users().keySet().iterator().next());
        authenticate(ownerId);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        if (context != null) {
            context.close();
        }
    }

    @Test
    void provesAuthorizedAnatomyPhysiologyAndVisualEvidenceThroughCanonicalResolution() {
        GateResult anatomy = executeGateCase("case-anatomy-exact-roots");
        assertResolvedEvidence(anatomy);

        GateResult physiology = executeGateCase("case-physio-semantic-sa-node");
        assertResolvedEvidence(physiology);

        GoldenRetrievalDataset.Case visualCase = caseById("case-anatomy-mixed-radial-nerve");
        UUID radialChunkId = seeder.deriveUuid("chunk", "chunk-radial-1");
        UUID radialNodeId = seeder.deriveUuid("node", "node-radial-nerve");
        UUID anatomyVersionId = seeder.deriveUuid("version", "ver-anatomy-1");
        UUID visualId = insertSupportedVisual(anatomyVersionId, radialNodeId, radialChunkId);

        GateResult visual = executeGateCase(visualCase);
        assertThat(visual.evidence().chunks()).extracting(chunk -> chunk.chunkId()).contains(radialChunkId);
        assertThat(visual.evidence().visuals()).singleElement().satisfies(evidenceVisual -> {
            assertThat(evidenceVisual.visualId()).isEqualTo(visualId);
            assertThat(evidenceVisual.materialId())
                    .isEqualTo(seeder.deriveUuid("material", corpus.materialVersions()
                            .get("ver-anatomy-1").material()));
            assertThat(evidenceVisual.materialVersionId()).isEqualTo(anatomyVersionId);
            assertThat(evidenceVisual.documentNodeId()).isEqualTo(radialNodeId);
            assertThat(evidenceVisual.visualType()).isEqualTo("ANATOMY_DIAGRAM");
            assertThat(evidenceVisual.caption()).isEqualTo("Synthetic radial nerve diagram");
            assertThat(evidenceVisual.interpretationStatus()).isEqualTo("SUPPORTED");
            assertThat(evidenceVisual.linkedChunkIds()).containsExactly(radialChunkId);
        });
        assertThat(visual.evidence().retrievalDiagnostics().selectedVisualIds())
                .containsExactly(visualId);
        assertThat(visual.evidence().sourceReferences())
                .anySatisfy(reference -> {
                    assertThat(reference.kind()).isEqualTo(EvidenceReferenceKind.VISUAL);
                    assertThat(reference.visualId()).isEqualTo(visualId);
                });
        assertResolvedEvidence(visual);
    }

    private GateResult executeGateCase(String caseId) {
        return executeGateCase(caseById(caseId));
    }

    private GateResult executeGateCase(GoldenRetrievalDataset.Case gateCase) {
        UUID topicId = authorizeCase(gateCase);
        RetrievalScope scope = context.getBean(BuildRetrievalScope.class).execute(
                new BuildRetrievalScope.Query(topicId, GroundingMode.valueOf(gateCase.groundingMode())));
        Set<UUID> expectedVersions = gateCase.allowedSourceKeys().stream()
                .map(key -> seeder.deriveUuid("version", key))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<UUID> expectedMaterials = gateCase.allowedSourceKeys().stream()
                .map(corpus.materialVersions()::get)
                .map(GoldenRetrievalCorpus.MaterialVersion::material)
                .map(key -> seeder.deriveUuid("material", key))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        assertThat(scope.userId()).isEqualTo(ownerId);
        assertThat(scope.allowedMaterialVersionIds()).isEqualTo(expectedVersions);

        LexicalSearchRepository lexical = context.getBean(LexicalSearchRepository.class);
        VectorSearchRepository vector = context.getBean(VectorSearchRepository.class);
        List<com.hippocampus.rag.port.LexicalSearchHit> lexicalHits = lexical.search(
                new LexicalSearchRequest(scope, gateCase.query(), SEARCH_LIMIT));
        IndexGeneration generation = new JdbcActiveIndexGenerationRepository(jdbc)
                .findActiveGeneration().orElseThrow();
        List<Float> queryVector = IntStream.range(0, gateCase.queryVector().length)
                .mapToObj(index -> gateCase.queryVector()[index])
                .toList();
        List<com.hippocampus.rag.port.VectorSearchHit> vectorHits = vector.search(
                new VectorSearchRequest(scope, generation.id(), new EmbeddingVector(queryVector), SEARCH_LIMIT));
        List<HybridCandidate> ranked = new HybridCandidateMerger().merge(
                lexicalHits, vectorHits, SEARCH_LIMIT);

        List<String> rankedKeys = ranked.stream()
                .map(candidate -> seeder.deriveLogicalKey("chunk", candidate.chunkId()))
                .toList();
        List<String> primaryKeys = rankedKeys.stream().limit(dataset.primaryK()).toList();
        assertThat(primaryKeys).containsAll(gateCase.expectedChunkKeys());
        List<String> primarySectionKeys = ranked.stream().limit(dataset.primaryK())
                .map(candidate -> seeder.deriveLogicalKey("node", candidate.documentNodeId()))
                .toList();
        assertThat(primarySectionKeys)
                .anyMatch(gateCase.expectedSectionKeys()::contains);
        double irrelevantRate = GoldenRetrievalMetrics.calculateIrrelevantContextRate(
                dataset.primaryK(), rankedKeys, gateCase.irrelevantChunkKeys());
        assertThat(irrelevantRate).isEqualTo(
                baseline.cases().get(gateCase.id()).explicitIrrelevantContextRate());

        EvidencePackage evidence = context.getBean(BuildEvidencePackage.class).execute(
                new BuildEvidencePackage.Command(scope, ranked, RetrievalQuality.STRONG,
                        new EvidencePackageBudget(dataset.primaryK(), dataset.primaryK())));
        List<UUID> selectedCandidateIds = ranked.stream().limit(dataset.primaryK())
                .map(HybridCandidate::chunkId).toList();
        assertThat(evidence.quality()).isEqualTo(RetrievalQuality.STRONG);
        assertThat(evidence.groundingMode()).isEqualTo(GroundingMode.valueOf(gateCase.groundingMode()));
        assertThat(evidence.chunks()).isNotEmpty();
        assertThat(evidence.chunks()).allSatisfy(chunk -> {
            assertThat(scope.allows(chunk.materialVersionId(), chunk.documentNodeId())).isTrue();
            assertThat(expectedVersions).contains(chunk.materialVersionId());
            assertThat(expectedMaterials).contains(chunk.materialId());
        });
        assertThat(evidence.chunks()).extracting(chunk -> chunk.chunkId())
                .containsExactlyElementsOf(selectedCandidateIds);
        assertThat(evidence.retrievalDiagnostics().selectedChunkIds())
                .containsExactlyElementsOf(selectedCandidateIds);
        assertThat(evidence.retrievalDiagnostics().sourceMaterialIds())
                .isEqualTo(expectedMaterials);
        assertThat(evidence.sourceReferences()).hasSize(
                evidence.chunks().size() + evidence.visuals().size());

        List<SourceReference> materialized = context.getBean(MaterializeEvidenceSourceReferences.class)
                .execute(evidence);
        return new GateResult(scope, evidence, materialized);
    }

    private void assertResolvedEvidence(GateResult result) {
        ResolveSourceReference resolver = context.getBean(ResolveSourceReference.class);
        assertThat(result.materialized()).hasSameSizeAs(result.evidence().sourceReferences());
        for (int index = 0; index < result.materialized().size(); index++) {
            EvidenceSourceReference evidenceReference = result.evidence().sourceReferences().get(index);
            SourceReference persisted = result.materialized().get(index);
            SourceReference resolved = resolver.execute(
                    new ResolveSourceReference.Query(persisted.sourceReferenceId()));

            assertThat(resolved).isEqualTo(persisted);
            assertThat(resolved.materialId()).isEqualTo(evidenceReference.materialId());
            assertThat(resolved.materialVersionId()).isEqualTo(evidenceReference.materialVersionId());
            assertThat(resolved.documentNodeId()).isEqualTo(evidenceReference.documentNodeId());
            assertThat(result.scope().allows(resolved.materialVersionId(), resolved.documentNodeId())).isTrue();
            if (evidenceReference.kind() == EvidenceReferenceKind.CHUNK) {
                assertThat(resolved.chunkId()).isEqualTo(evidenceReference.chunkId());
                assertThat(resolved.visualAssetId()).isNull();
            } else {
                assertThat(resolved.visualAssetId()).isEqualTo(evidenceReference.visualId());
                assertThat(resolved.chunkId()).isNull();
            }
        }
    }

    private UUID authorizeCase(GoldenRetrievalDataset.Case gateCase) {
        UUID topicId = seeder.deriveUuid("topic", "phase4-gate-" + gateCase.id());
        UUID subjectId = seeder.deriveUuid("subject", corpus.subjects().keySet().iterator().next());
        jdbc.sql("""
                INSERT INTO topics(id,subject_id,name,status,created_at,updated_at)
                VALUES (:id,:subject,:name,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """).param("id", topicId).param("subject", subjectId)
                .param("name", "Phase 4 gate " + gateCase.id()).update();
        for (String sourceKey : gateCase.allowedSourceKeys()) {
            UUID versionId = seeder.deriveUuid("version", sourceKey);
            UUID materialId = seeder.deriveUuid(
                    "material", corpus.materialVersions().get(sourceKey).material());
            jdbc.sql("""
                    INSERT INTO material_topic_links(
                        id,topic_id,material_id,material_version_id,link_origin,status,created_at,updated_at)
                    VALUES (:id,:topic,:material,:version,'USER_SELECTED','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """).param("id", UUID.randomUUID()).param("topic", topicId)
                    .param("material", materialId).param("version", versionId).update();
        }
        return topicId;
    }

    private UUID insertSupportedVisual(UUID versionId, UUID nodeId, UUID chunkId) {
        UUID visualId = seeder.deriveUuid("visual", "phase4-radial-supported");
        jdbc.sql("""
                INSERT INTO visual_assets(
                    id,material_version_id,document_node_id,page_number,storage_key,visual_type,
                    caption,nearby_text,interpretation_status,content_hash,created_at)
                VALUES (:id,:version,:node,20,:storage,'ANATOMY_DIAGRAM',
                        'Synthetic radial nerve diagram','Radial nerve and wrist extension',
                        'SUPPORTED',:hash,CURRENT_TIMESTAMP)
                """).param("id", visualId).param("version", versionId).param("node", nodeId)
                .param("storage", "private/phase4-gate/" + visualId)
                .param("hash", visualId.toString()).update();
        jdbc.sql("""
                INSERT INTO chunk_visual_links(
                    chunk_id,visual_asset_id,material_version_id,relationship_type)
                VALUES (:chunk,:visual,:version,'NEARBY')
                """).param("chunk", chunkId).param("visual", visualId)
                .param("version", versionId).update();
        return visualId;
    }

    private GoldenRetrievalDataset.Case caseById(String caseId) {
        return dataset.cases().stream()
                .filter(candidate -> candidate.id().equals(caseId))
                .findFirst()
                .orElseThrow();
    }

    private static void authenticate(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        new HippocampusPrincipal(userId, userId + "@example.test"), null, List.of()));
    }

    private record GateResult(
            RetrievalScope scope,
            EvidencePackage evidence,
            List<SourceReference> materialized) {}
}
