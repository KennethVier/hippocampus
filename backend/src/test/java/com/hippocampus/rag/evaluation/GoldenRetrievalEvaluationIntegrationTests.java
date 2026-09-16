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

                String modeStr = c.groundingMode();
                if ("HYBRID".equals(modeStr)) modeStr = "STRICT_SOURCE";
                RetrievalScope scope = buildScope.execute(new BuildRetrievalScope.Query(caseTopicId, GroundingMode.valueOf(modeStr)));

                List<LexicalSearchHit> laHits = lexicalSearch.search(new LexicalSearchRequest(scope, c.query(), 10));
                List<String> laKeys = laHits.stream().map(h -> seeder.deriveLogicalKey("chunk", h.chunkId())).toList();

                IndexGeneration gen = generations.findActiveGeneration().orElseThrow();
                List<Float> queryVec = IntStream.range(0, c.queryVector().length).mapToObj(i -> c.queryVector()[i]).toList();
                List<VectorSearchHit> veHits = vectorSearch.search(new VectorSearchRequest(scope, gen.id(), new EmbeddingVector(queryVec), 10));
                List<String> veKeys = veHits.stream().map(h -> seeder.deriveLogicalKey("chunk", h.chunkId())).toList();

                List<HybridCandidate> hyHits = merger.merge(laHits, veHits, 10);
                List<String> hyKeys = hyHits.stream().map(h -> seeder.deriveLogicalKey("chunk", h.chunkId())).toList();

                GoldenRetrievalMetrics.MetricResult laM = GoldenRetrievalMetrics.calculate(dataset.primaryK(), laKeys, c.expectedChunkKeys(), c.acceptableAlternativeChunkKeys());
                GoldenRetrievalMetrics.MetricResult veM = GoldenRetrievalMetrics.calculate(dataset.primaryK(), veKeys, c.expectedChunkKeys(), c.acceptableAlternativeChunkKeys());
                GoldenRetrievalMetrics.MetricResult hyM = GoldenRetrievalMetrics.calculate(dataset.primaryK(), hyKeys, c.expectedChunkKeys(), c.acceptableAlternativeChunkKeys());

                // Calculate expected-section hit: any top-K chunk belongs to an expected section
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
                        new GoldenRetrievalMetrics.CaseResult(laM, veM, hyM, sectionHitRate, irrelevantRate),
                        hyKeys
                ));
            }

            verifyThresholds(caseResults);
        }
    }

    private void verifyThresholds(Map<String, CaseEvaluationResult> results) throws IOException {
        Map<String, Map<String, Object>> thresholds = objectMapper.readValue(
                getClass().getResourceAsStream("/rag/golden-retrieval/v1/thresholds.json"),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Map<String, Object>>>() {});

        for (var entry : results.entrySet()) {
            String caseId = entry.getKey();
            CaseEvaluationResult evalResult = entry.getValue();
            GoldenRetrievalMetrics.CaseResult result = evalResult.metrics();
            Map<String, Object> threshold = thresholds.get(caseId);
            if (threshold == null) continue;

            @SuppressWarnings("unchecked")
            Map<String, Double> hybridThreshold = (Map<String, Double>) threshold.get("hybrid");

            java.util.function.Supplier<String> diag = () -> {
                GoldenRetrievalDataset.Case c = dataset.cases().stream().filter(case_ -> case_.id().equals(caseId)).findFirst().orElseThrow();
                return String.format("\nExpected: %s\nObserved: %s",
                    c.expectedChunkKeys(), evalResult.observedKeys());
            };

            if (result.hybrid().recall() < hybridThreshold.get("recall")) {
                fail(String.format("Case %s: observed recall %.4f < threshold %.4f%s", caseId, result.hybrid().recall(), hybridThreshold.get("recall"), diag.get()));
            }
            if (result.hybrid().precision() < hybridThreshold.get("precision")) {
                fail(String.format("Case %s: observed precision %.4f < threshold %.4f%s", caseId, result.hybrid().precision(), hybridThreshold.get("precision"), diag.get()));
            }
            if (result.hybrid().mrr() < hybridThreshold.get("mrr")) {
                fail(String.format("Case %s: observed mrr %.4f < threshold %.4f%s", caseId, result.hybrid().mrr(), hybridThreshold.get("mrr"), diag.get()));
            }

            double minSectionHit = (double) threshold.get("sectionHitRate");
            if (result.expectedSectionHitRate() < minSectionHit) {
                fail(String.format("Case %s: observed sectionHitRate %.4f < threshold %.4f", caseId, result.expectedSectionHitRate(), minSectionHit));
            }

            double maxIrrelevant = (double) threshold.get("irrelevantRate");
            if (result.explicitIrrelevantContextRate() > maxIrrelevant) {
                fail(String.format("Case %s: observed irrelevantRate %.4f > threshold %.4f", caseId, result.explicitIrrelevantContextRate(), maxIrrelevant));
            }
        }
    }
}
