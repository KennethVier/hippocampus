package com.hippocampus.rag.evaluation;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hippocampus.identity.domain.AuthenticatedUser;
import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.rag.domain.*;
import com.hippocampus.rag.port.*;
import com.hippocampus.rag.application.*;
import com.hippocampus.testing.PostgresIntegrationTestSupport;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

public class GoldenRetrievalEvaluationIntegrationTests extends PostgresIntegrationTestSupport {
    private static final double NUMERIC_TOLERANCE = 1.0e-12;

    private JdbcClient jdbc;
    private LexicalSearchRepository lexicalSearch;
    private VectorSearchRepository vectorSearch;
    private ActiveIndexGenerationRepository generations;
    private HybridCandidateMerger merger;
    private RetrievalScopeSourceRepository scopeSourceRepo;
    private ObjectMapper objectMapper;

    private GoldenRetrievalDataset dataset;
    private GoldenRetrievalCorpus corpus;
    private GoldenRetrievalDatasetLoader loader;
    private GoldenRetrievalFixtureSeeder seeder;

    @BeforeEach
    void setup() throws IOException, java.sql.SQLException {
        resetPostgresSchema();
        loader = new GoldenRetrievalDatasetLoader();
        dataset = loader.loadDataset("/rag/golden-retrieval/v1/dataset.json");
        corpus = loader.loadCorpus("/rag/golden-retrieval/v1/corpus.json", dataset);
    }

    private record CaseEvaluationResult(
            GoldenRetrievalMetrics.CaseResult metrics,
            List<String> observedKeys
    ) {}

    @Test
    void generateBaseline() throws IOException {
        try (var context = startApplicationWithFlyway()) {
            this.jdbc = context.getBean(JdbcClient.class);
            this.lexicalSearch = context.getBean(LexicalSearchRepository.class);
            this.vectorSearch = context.getBean(VectorSearchRepository.class);
            this.generations = new com.hippocampus.rag.infrastructure.persistence.JdbcActiveIndexGenerationRepository(jdbc);
            this.merger = new com.hippocampus.rag.application.HybridCandidateMerger();
            this.scopeSourceRepo = context.getBean(RetrievalScopeSourceRepository.class);
            this.objectMapper = new ObjectMapper();

            seeder = new GoldenRetrievalFixtureSeeder(jdbc);
            seeder.seed(corpus);

            String firstUserKey = corpus.users().keySet().iterator().next();
            String firstSubjectKey = corpus.subjects().keySet().iterator().next();
            Map<String, CaseEvaluationResult> caseResults = new LinkedHashMap<>();

            for (GoldenRetrievalDataset.Case c : dataset.cases()) {
                UUID caseTopicId = seeder.deriveUuid("topic", "case-" + c.id());
                UUID userId = seeder.deriveUuid("user", firstUserKey);

                jdbc.sql("INSERT INTO topics (id, subject_id, name, status, created_at, updated_at) VALUES (:id, :sid, :name, 'ACTIVE', now(), now())")
                        .param("id", caseTopicId)
                        .param("sid", seeder.deriveUuid("subject", firstSubjectKey))
                        .param("name", "Topic for case " + c.id())
                        .update();

                for (String sourceKey : c.allowedSourceKeys()) {
                    UUID mvId = seeder.deriveUuid("version", sourceKey);
                    UUID matId = seeder.deriveUuid("material", corpus.materialVersions().get(sourceKey).material());
                    jdbc.sql("INSERT INTO material_topic_links (id, topic_id, material_id, material_version_id, link_origin, status, created_at, updated_at) VALUES (:id, :topic, :mat, :mv, 'USER_SELECTED', 'ACTIVE', now(), now())")
                            .param("id", UUID.randomUUID())
                            .param("topic", caseTopicId)
                            .param("mat", matId)
                            .param("mv", mvId)
                            .update();
                }

                CurrentUser currentUser = () -> new AuthenticatedUser(userId);
                BuildRetrievalScope buildScope = new BuildRetrievalScope(currentUser, scopeSourceRepo);
                RetrievalScope scope = buildScope.execute(new BuildRetrievalScope.Query(caseTopicId, GroundingMode.valueOf(c.groundingMode())));

                List<LexicalSearchHit> laHits = lexicalSearch.search(new LexicalSearchRequest(scope, c.query(), 10));
                List<String> laKeys = laHits.stream().map(h -> seeder.deriveLogicalKey("chunk", h.chunkId())).toList();

                IndexGeneration gen = generations.findActiveGeneration().orElseThrow();
                List<Float> queryVec = IntStream.range(0, c.queryVector().length).mapToObj(i -> c.queryVector()[i]).toList();
                List<VectorSearchHit> veHits = vectorSearch.search(new VectorSearchRequest(scope, gen.id(), new EmbeddingVector(queryVec), 10));
                List<String> veKeys = veHits.stream().map(h -> seeder.deriveLogicalKey("chunk", h.chunkId())).toList();

                List<HybridCandidate> hyHits = merger.merge(laHits, veHits, 10);
                List<String> hyKeys = hyHits.stream().map(h -> seeder.deriveLogicalKey("chunk", h.chunkId())).toList();

                GoldenRetrievalMetrics.ChannelResults laCR = new GoldenRetrievalMetrics.ChannelResults(
                        GoldenRetrievalMetrics.calculate(1, laKeys, c.expectedChunkKeys(), c.acceptableAlternativeChunkKeys()),
                        GoldenRetrievalMetrics.calculate(3, laKeys, c.expectedChunkKeys(), c.acceptableAlternativeChunkKeys()),
                        GoldenRetrievalMetrics.calculate(5, laKeys, c.expectedChunkKeys(), c.acceptableAlternativeChunkKeys()),
                        GoldenRetrievalMetrics.calculatePlainMrr(laKeys, c.expectedChunkKeys())
                );
                GoldenRetrievalMetrics.ChannelResults veCR = new GoldenRetrievalMetrics.ChannelResults(
                        GoldenRetrievalMetrics.calculate(1, veKeys, c.expectedChunkKeys(), c.acceptableAlternativeChunkKeys()),
                        GoldenRetrievalMetrics.calculate(3, veKeys, c.expectedChunkKeys(), c.acceptableAlternativeChunkKeys()),
                        GoldenRetrievalMetrics.calculate(5, veKeys, c.expectedChunkKeys(), c.acceptableAlternativeChunkKeys()),
                        GoldenRetrievalMetrics.calculatePlainMrr(veKeys, c.expectedChunkKeys())
                );
                GoldenRetrievalMetrics.ChannelResults hyCR = new GoldenRetrievalMetrics.ChannelResults(
                        GoldenRetrievalMetrics.calculate(1, hyKeys, c.expectedChunkKeys(), c.acceptableAlternativeChunkKeys()),
                        GoldenRetrievalMetrics.calculate(3, hyKeys, c.expectedChunkKeys(), c.acceptableAlternativeChunkKeys()),
                        GoldenRetrievalMetrics.calculate(5, hyKeys, c.expectedChunkKeys(), c.acceptableAlternativeChunkKeys()),
                        GoldenRetrievalMetrics.calculatePlainMrr(hyKeys, c.expectedChunkKeys())
                );

                List<String> topKHyKeys = hyKeys.stream().limit(dataset.primaryK()).toList();
                boolean sectionHit = false;
                for (String chunkKey : topKHyKeys) {
                    UUID chunkId = seeder.deriveUuid("chunk", chunkKey);
                    UUID nodeId = jdbc.sql("SELECT document_node_id FROM chunks WHERE id = ?").param(chunkId).query(UUID.class).single();
                    if (nodeId != null && c.expectedSectionKeys().contains(seeder.deriveLogicalKey("node", nodeId))) {
                        sectionHit = true;
                        break;
                    }
                }
                double sectionHitRate = sectionHit ? 1.0 : 0.0;
                double irrelevantRate = GoldenRetrievalMetrics.calculateIrrelevantContextRate(dataset.primaryK(), hyKeys, c.irrelevantChunkKeys());

                caseResults.put(c.id(), new CaseEvaluationResult(
                        new GoldenRetrievalMetrics.CaseResult(laCR, veCR, hyCR, sectionHitRate, irrelevantRate),
                        hyKeys
                ));
            }

            Map<String, Object> baselineMap = new LinkedHashMap<>();
            baselineMap.put("datasetVersion", dataset.datasetVersion());
            baselineMap.put("primaryK", dataset.primaryK());
            baselineMap.put("kValues", dataset.kValues());

            Map<String, Object> casesMap = new LinkedHashMap<>();
            caseResults.forEach((id, res) -> {
                Map<String, Object> caseMap = new LinkedHashMap<>();
                caseMap.put("lexical", res.metrics().lexical());
                caseMap.put("vector", res.metrics().vector());
                caseMap.put("hybrid", res.metrics().hybrid());
                caseMap.put("expectedSectionHitRate", res.metrics().expectedSectionHitRate());
                caseMap.put("explicitIrrelevantContextRate", res.metrics().explicitIrrelevantContextRate());
                casesMap.put(id, caseMap);
            });
            baselineMap.put("cases", casesMap);

            System.out.println("--- BASELINE JSON ---");
            System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(baselineMap));
            System.out.println("--- END BASELINE JSON ---");
        }
    }

    @Test
    void evaluateAndVerify() throws IOException {
        try (var context = startApplicationWithFlyway()) {
            this.jdbc = context.getBean(JdbcClient.class);
            this.lexicalSearch = context.getBean(LexicalSearchRepository.class);
            this.vectorSearch = context.getBean(VectorSearchRepository.class);
            this.generations = new com.hippocampus.rag.infrastructure.persistence.JdbcActiveIndexGenerationRepository(jdbc);
            this.merger = new com.hippocampus.rag.application.HybridCandidateMerger();
            this.scopeSourceRepo = context.getBean(RetrievalScopeSourceRepository.class);
            this.objectMapper = new ObjectMapper();

            seeder = new GoldenRetrievalFixtureSeeder(jdbc);
            seeder.seed(corpus);

            String firstUserKey = corpus.users().keySet().iterator().next();
            String firstSubjectKey = corpus.subjects().keySet().iterator().next();
            Map<String, CaseEvaluationResult> caseResults = new LinkedHashMap<>();

            for (GoldenRetrievalDataset.Case c : dataset.cases()) {
                // Case-specific authorization setup
                UUID caseTopicId = seeder.deriveUuid("topic", "case-" + c.id());
                UUID userId = seeder.deriveUuid("user", firstUserKey);

                // Ensure Topic exists and is owned by the user
                jdbc.sql("INSERT INTO topics (id, subject_id, name, status, created_at, updated_at) VALUES (:id, :sid, :name, 'ACTIVE', now(), now())")
                        .param("id", caseTopicId)
                        .param("sid", seeder.deriveUuid("subject", firstSubjectKey))
                        .param("name", "Topic for case " + c.id())
                        .update();

                // Link exactly the allowed source keys to this case-specific topic
                for (String sourceKey : c.allowedSourceKeys()) {
                    UUID mvId = seeder.deriveUuid("version", sourceKey);
                    UUID matId = seeder.deriveUuid("material", corpus.materialVersions().get(sourceKey).material());
                    jdbc.sql("INSERT INTO material_topic_links (id, topic_id, material_id, material_version_id, link_origin, status, created_at, updated_at) VALUES (:id, :topic, :mat, :mv, 'USER_SELECTED', 'ACTIVE', now(), now())")
                            .param("id", UUID.randomUUID())
                            .param("topic", caseTopicId)
                            .param("mat", matId)
                            .param("mv", mvId)
                            .update();
                }

                CurrentUser currentUser = () -> new AuthenticatedUser(userId);
                BuildRetrievalScope buildScope = new BuildRetrievalScope(currentUser, scopeSourceRepo);

                RetrievalScope scope = buildScope.execute(new BuildRetrievalScope.Query(caseTopicId, GroundingMode.valueOf(c.groundingMode())));

                // Assert RetrievalScope contains exactly the authorized versions
                Set<UUID> expectedVersionIds = c.allowedSourceKeys().stream()
                        .map(s -> seeder.deriveUuid("version", s)).collect(Collectors.toSet());
                assertEquals(expectedVersionIds, scope.allowedMaterialVersionIds(), "RetrievalScope should contain exactly the authorized material versions for case " + c.id());

                List<LexicalSearchHit> laHits = lexicalSearch.search(new LexicalSearchRequest(scope, c.query(), 10));
                List<String> laKeys = laHits.stream().map(h -> seeder.deriveLogicalKey("chunk", h.chunkId())).toList();

                IndexGeneration gen = generations.findActiveGeneration().orElseThrow();
                List<Float> queryVec = IntStream.range(0, c.queryVector().length).mapToObj(i -> c.queryVector()[i]).toList();
                List<VectorSearchHit> veHits = vectorSearch.search(new VectorSearchRequest(scope, gen.id(), new EmbeddingVector(queryVec), 10));
                List<String> veKeys = veHits.stream().map(h -> seeder.deriveLogicalKey("chunk", h.chunkId())).toList();

                List<HybridCandidate> hyHits = merger.merge(laHits, veHits, 10);
                List<String> hyKeys = hyHits.stream().map(h -> seeder.deriveLogicalKey("chunk", h.chunkId())).toList();

                // Calculate metrics for all channels at K=1, 3, 5
                GoldenRetrievalMetrics.ChannelResults laCR = new GoldenRetrievalMetrics.ChannelResults(
                        GoldenRetrievalMetrics.calculate(1, laKeys, c.expectedChunkKeys(), c.acceptableAlternativeChunkKeys()),
                        GoldenRetrievalMetrics.calculate(3, laKeys, c.expectedChunkKeys(), c.acceptableAlternativeChunkKeys()),
                        GoldenRetrievalMetrics.calculate(5, laKeys, c.expectedChunkKeys(), c.acceptableAlternativeChunkKeys()),
                        GoldenRetrievalMetrics.calculatePlainMrr(laKeys, c.expectedChunkKeys())
                );
                GoldenRetrievalMetrics.ChannelResults veCR = new GoldenRetrievalMetrics.ChannelResults(
                        GoldenRetrievalMetrics.calculate(1, veKeys, c.expectedChunkKeys(), c.acceptableAlternativeChunkKeys()),
                        GoldenRetrievalMetrics.calculate(3, veKeys, c.expectedChunkKeys(), c.acceptableAlternativeChunkKeys()),
                        GoldenRetrievalMetrics.calculate(5, veKeys, c.expectedChunkKeys(), c.acceptableAlternativeChunkKeys()),
                        GoldenRetrievalMetrics.calculatePlainMrr(veKeys, c.expectedChunkKeys())
                );
                GoldenRetrievalMetrics.ChannelResults hyCR = new GoldenRetrievalMetrics.ChannelResults(
                        GoldenRetrievalMetrics.calculate(1, hyKeys, c.expectedChunkKeys(), c.acceptableAlternativeChunkKeys()),
                        GoldenRetrievalMetrics.calculate(3, hyKeys, c.expectedChunkKeys(), c.acceptableAlternativeChunkKeys()),
                        GoldenRetrievalMetrics.calculate(5, hyKeys, c.expectedChunkKeys(), c.acceptableAlternativeChunkKeys()),
                        GoldenRetrievalMetrics.calculatePlainMrr(hyKeys, c.expectedChunkKeys())
                );

                // Calculate expected-section hit and irrelevant rate at primary K
                List<String> topKHyKeys = hyKeys.stream().limit(dataset.primaryK()).toList();
                boolean sectionHit = false;
                for (String chunkKey : topKHyKeys) {
                    UUID chunkId = seeder.deriveUuid("chunk", chunkKey);
                    UUID nodeId = jdbc.sql("SELECT document_node_id FROM chunks WHERE id = ?").param(chunkId).query(UUID.class).single();
                    if (nodeId != null && c.expectedSectionKeys().contains(seeder.deriveLogicalKey("node", nodeId))) {
                        sectionHit = true;
                        break;
                    }
                }
                double sectionHitRate = sectionHit ? 1.0 : 0.0;
                double irrelevantRate = GoldenRetrievalMetrics.calculateIrrelevantContextRate(dataset.primaryK(), hyKeys, c.irrelevantChunkKeys());

                caseResults.put(c.id(), new CaseEvaluationResult(
                        new GoldenRetrievalMetrics.CaseResult(laCR, veCR, hyCR, sectionHitRate, irrelevantRate),
                        hyKeys
                ));
            }

            verifyThresholds(caseResults);
        }
    }

    private void verifyThresholds(Map<String, CaseEvaluationResult> results) throws IOException {
        GoldenRetrievalBenchmarkContract.Baseline baseline = GoldenRetrievalBenchmarkContract.load(
                objectMapper, "/rag/golden-retrieval/v1/baseline.json");
        GoldenRetrievalBenchmarkContract.Baseline thresholds = GoldenRetrievalBenchmarkContract.load(
                objectMapper, "/rag/golden-retrieval/v1/thresholds.json");

        GoldenRetrievalBenchmarkContract.validate(baseline, dataset, "baseline.json");
        GoldenRetrievalBenchmarkContract.validate(thresholds, dataset, "thresholds.json");
        GoldenRetrievalBenchmarkContract.verifyBaselineAsThreshold(baseline, thresholds, NUMERIC_TOLERANCE);

        Map<String, GoldenRetrievalMetrics.CaseResult> observed = results.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().metrics(),
                        (left, right) -> { throw new IllegalStateException("duplicate observed case"); },
                        LinkedHashMap::new));
        GoldenRetrievalBenchmarkContract.verifyObserved(baseline, observed, NUMERIC_TOLERANCE);
    }
}
