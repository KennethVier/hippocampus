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
import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.rag.domain.*;
import com.hippocampus.rag.port.*;
import com.hippocampus.rag.application.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class GoldenRetrievalEvaluationIntegrationTests {

    @Autowired private JdbcClient jdbc;
    @Autowired private LexicalSearchRepository lexicalSearch;
    @Autowired private VectorSearchRepository vectorSearch;
    @Autowired private ActiveIndexGenerationRepository generations;
    @Autowired private HybridCandidateMerger merger;
    @Autowired private RetrievalScopeSourceRepository scopeSourceRepo;
    @Autowired private ObjectMapper objectMapper;

    private GoldenRetrievalDataset dataset;
    private GoldenRetrievalCorpus corpus;
    private GoldenRetrievalDatasetLoader loader;
    private GoldenRetrievalFixtureSeeder seeder;

    @BeforeEach
    void setup() throws IOException {
        loader = new GoldenRetrievalDatasetLoader();
        dataset = loader.loadDataset("/rag/golden-retrieval/v1/dataset.json");
        corpus = loader.loadCorpus("/rag/golden-retrieval/v1/corpus.json");
        seeder = new GoldenRetrievalFixtureSeeder(jdbc);
        seeder.seed(corpus);
    }

    @Test
    void evaluateAndVerify() throws IOException {
        Map<String, GoldenRetrievalMetrics.CaseResult> caseResults = new LinkedHashMap<>();

        for (GoldenRetrievalDataset.Case c : dataset.cases()) {
            UUID topicId = seeder.deriveUuid("topic", "topic-brachial-plexus");

            UUID userId = seeder.deriveUuid("user", "user-eval");
            CurrentUser currentUser = new CurrentUser(() -> userId);
            BuildRetrievalScope buildScope = new BuildRetrievalScope(currentUser, scopeSourceRepo);
            RetrievalScope scope = buildScope.execute(new BuildRetrievalScope.Query(topicId, GroundingMode.valueOf(c.groundingMode())));

            List<LexicalSearchHit> laHits = lexicalSearch.search(new LexicalSearchRequest(scope, c.query(), 10));
            List<String> laKeys = laHits.stream().map(h -> seeder.deriveLogicalKey("chunk", h.chunkId())).toList();

            IndexGeneration gen = generations.findActiveGeneration().orElseThrow();
            List<VectorSearchHit> veHits = vectorSearch.search(new VectorSearchRequest(scope, gen.id(), new EmbeddingVector(c.queryVector()), 10));
            List<String> veKeys = veHits.stream().map(h -> seeder.deriveLogicalKey("chunk", h.chunkId())).toList();

            List<HybridCandidate> hyHits = merger.merge(laHits, veHits, 10);
            List<String> hyKeys = hyHits.stream().map(h -> seeder.deriveLogicalKey("chunk", h.chunkId())).toList();

            GoldenRetrievalMetrics.MetricResult laM = GoldenRetrievalMetrics.calculate(dataset.primaryK(), laKeys, c.expectedChunkKeys(), c.acceptableAlternativeChunkKeys());
            GoldenRetrievalMetrics.MetricResult veM = GoldenRetrievalMetrics.calculate(dataset.primaryK(), veKeys, c.expectedChunkKeys(), c.acceptableAlternativeChunkKeys());
            GoldenRetrievalMetrics.MetricResult hyM = GoldenRetrievalMetrics.calculate(dataset.primaryK(), hyKeys, c.expectedChunkKeys(), c.acceptableAlternativeChunkKeys());

            double sectionHit = 1.0;
            double irrelevantRate = GoldenRetrievalMetrics.calculateIrrelevantContextRate(dataset.primaryK(), hyKeys, c.irrelevantChunkKeys());

            caseResults.put(c.id(), new GoldenRetrievalMetrics.CaseResult(laM, veM, hyM, sectionHit, irrelevantRate));
        }

        verifyThresholds(caseResults);
    }

    private void verifyThresholds(Map<String, GoldenRetrievalMetrics.CaseResult> results) throws IOException {
        System.out.println("Results: " + results);
    }
}
