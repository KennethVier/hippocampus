package com.hippocampus.rag.evaluation;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GoldenRetrievalValidationTests {

    @Test
    void committedGoldenResourcesAreStrictlyValid() throws IOException {
        GoldenRetrievalDatasetLoader loader = new GoldenRetrievalDatasetLoader();
        GoldenRetrievalDataset dataset = loader.loadDataset("/rag/golden-retrieval/v1/dataset.json");

        assertDoesNotThrow(() -> loader.loadCorpus("/rag/golden-retrieval/v1/corpus.json", dataset));

        ObjectMapper objectMapper = new ObjectMapper();
        GoldenRetrievalBenchmarkContract.Baseline baseline = GoldenRetrievalBenchmarkContract.load(
                objectMapper, "/rag/golden-retrieval/v1/baseline.json");
        GoldenRetrievalBenchmarkContract.Baseline thresholds = GoldenRetrievalBenchmarkContract.load(
                objectMapper, "/rag/golden-retrieval/v1/thresholds.json");
        assertDoesNotThrow(() -> GoldenRetrievalBenchmarkContract.validate(baseline, dataset, "baseline.json"));
        assertDoesNotThrow(() -> GoldenRetrievalBenchmarkContract.validate(thresholds, dataset, "thresholds.json"));
        assertDoesNotThrow(() -> GoldenRetrievalBenchmarkContract.verifyBaselineAsThreshold(
                baseline, thresholds, 1.0e-12));
    }

    @Test
    void rejectsMissingBenchmarkMetricAndThresholdRelaxation() throws IOException {
        GoldenRetrievalDataset dataset = new GoldenRetrievalDatasetLoader()
                .loadDataset("/rag/golden-retrieval/v1/dataset.json");
        GoldenRetrievalBenchmarkContract.Baseline baseline = GoldenRetrievalBenchmarkContract.load(
                new ObjectMapper(), "/rag/golden-retrieval/v1/baseline.json");
        String caseId = baseline.cases().keySet().iterator().next();
        GoldenRetrievalBenchmarkContract.CaseBaseline original = baseline.cases().get(caseId);

        Map<String, GoldenRetrievalBenchmarkContract.CaseBaseline> missingMetricCases =
                new LinkedHashMap<>(baseline.cases());
        GoldenRetrievalBenchmarkContract.ChannelBaseline missingPlainMrr =
                new GoldenRetrievalBenchmarkContract.ChannelBaseline(
                        original.lexical().k1(), original.lexical().k3(), original.lexical().k5(), null);
        missingMetricCases.put(caseId, new GoldenRetrievalBenchmarkContract.CaseBaseline(
                missingPlainMrr, original.vector(), original.hybrid(),
                original.expectedSectionHitRate(), original.explicitIrrelevantContextRate()));
        GoldenRetrievalBenchmarkContract.Baseline missingMetric = new GoldenRetrievalBenchmarkContract.Baseline(
                baseline.datasetVersion(), baseline.primaryK(), baseline.kValues(), missingMetricCases);

        assertThrows(IllegalArgumentException.class,
                () -> GoldenRetrievalBenchmarkContract.validate(missingMetric, dataset, "thresholds.json"));

        Map<String, GoldenRetrievalBenchmarkContract.CaseBaseline> relaxedCases =
                new LinkedHashMap<>(baseline.cases());
        double changedPlainMrr = original.lexical().plainMrr() == 0.0 ? 0.25 : 0.75;
        GoldenRetrievalBenchmarkContract.ChannelBaseline relaxedLexical =
                new GoldenRetrievalBenchmarkContract.ChannelBaseline(
                        original.lexical().k1(), original.lexical().k3(), original.lexical().k5(), changedPlainMrr);
        relaxedCases.put(caseId, new GoldenRetrievalBenchmarkContract.CaseBaseline(
                relaxedLexical, original.vector(), original.hybrid(),
                original.expectedSectionHitRate(), original.explicitIrrelevantContextRate()));
        GoldenRetrievalBenchmarkContract.Baseline relaxed = new GoldenRetrievalBenchmarkContract.Baseline(
                baseline.datasetVersion(), baseline.primaryK(), baseline.kValues(), relaxedCases);

        assertThrows(AssertionError.class,
                () -> GoldenRetrievalBenchmarkContract.verifyBaselineAsThreshold(baseline, relaxed, 1.0e-12));
    }

    @Test
    void rejectsBlankQuery() {
        GoldenRetrievalDataset dataset = dataset("  ", new float[]{0.1f, 0.2f, 0.3f, 0.4f}, List.of(1));

        assertThrows(IllegalArgumentException.class, dataset::validate);
    }

    @Test
    void rejectsNonFiniteOrZeroQueryVector() {
        GoldenRetrievalDataset nonFinite = dataset("query", new float[]{0.1f, Float.NaN, 0.3f, 0.4f}, List.of(1));
        GoldenRetrievalDataset zero = dataset("query", new float[]{0.0f, 0.0f, 0.0f, 0.0f}, List.of(1));

        assertThrows(IllegalArgumentException.class, nonFinite::validate);
        assertThrows(IllegalArgumentException.class, zero::validate);
    }

    @Test
    void rejectsQueryVectorDimensionDifferentFromSyntheticGeneration() {
        GoldenRetrievalDataset dataset = dataset("query", new float[]{0.1f}, List.of(1));

        assertThrows(IllegalArgumentException.class, () -> corpus().validate(dataset));
    }

    @Test
    void rejectsMaterialVersionReferencingMissingMaterial() {
        GoldenRetrievalCorpus corpus = corpus(
                Map.of(),
                Map.of("ver-test-1", new GoldenRetrievalCorpus.MaterialVersion("mat-test", "1.0")),
                Map.of("node-test", new GoldenRetrievalCorpus.DocumentNode("ver-test-1", "Test")),
                oneChunk(),
                oneEmbedding());

        assertThrows(IllegalArgumentException.class, () -> corpus.validate(validDataset()));
    }

    @Test
    void rejectsDocumentNodeReferencingMissingMaterialVersion() {
        GoldenRetrievalCorpus corpus = corpus(
                oneMaterial(),
                Map.of(),
                Map.of("node-test", new GoldenRetrievalCorpus.DocumentNode("ver-test-1", "Test")),
                oneChunk(),
                oneEmbedding());

        assertThrows(IllegalArgumentException.class, () -> corpus.validate(validDataset()));
    }

    @Test
    void rejectsInsufficientAuthorizedCandidatesForMaximumK() {
        GoldenRetrievalDataset dataset = dataset(
                "query", new float[]{0.1f, 0.2f, 0.3f, 0.4f}, List.of(1, 3, 5));

        assertThrows(IllegalArgumentException.class, () -> corpus().validate(dataset));
    }

    @Test
    void rejectsDuplicateChunkIndexesWithinMaterialVersion() {
        Map<String, GoldenRetrievalCorpus.Chunk> chunks = new LinkedHashMap<>(oneChunk());
        chunks.put("chunk-test-2", new GoldenRetrievalCorpus.Chunk(
                "node-test", 1, "other", 2, 2, "Other", "STRONG", "TEXT", "NATIVE"));
        Map<String, float[]> embeddings = new LinkedHashMap<>(oneEmbedding());
        embeddings.put("chunk-test-2", new float[]{0.2f, 0.2f, 0.3f, 0.4f});
        GoldenRetrievalCorpus corpus = corpus(oneMaterial(), oneVersion(), oneNode(), chunks, embeddings);

        assertThrows(IllegalArgumentException.class, () -> corpus.validate(validDataset()));
    }

    @Test
    void rejectsNonFiniteEmbeddingAndInvalidChunkIndexOrExtractionMethod() {
        GoldenRetrievalCorpus nonFinite = corpus(
                oneMaterial(), oneVersion(), oneNode(), oneChunk(),
                Map.of("chunk-test", new float[]{0.1f, Float.POSITIVE_INFINITY, 0.3f, 0.4f}));
        GoldenRetrievalCorpus invalidIndex = corpus(
                oneMaterial(), oneVersion(), oneNode(),
                Map.of("chunk-test", new GoldenRetrievalCorpus.Chunk(
                        "node-test", 0, "test", 1, 1, "Test", "STRONG", "TEXT", "NATIVE")),
                oneEmbedding());
        GoldenRetrievalCorpus invalidExtraction = corpus(
                oneMaterial(), oneVersion(), oneNode(),
                Map.of("chunk-test", new GoldenRetrievalCorpus.Chunk(
                        "node-test", 1, "test", 1, 1, "Test", "STRONG", "TEXT", "INVALID")),
                oneEmbedding());

        assertThrows(IllegalArgumentException.class, () -> nonFinite.validate(validDataset()));
        assertThrows(IllegalArgumentException.class, () -> invalidIndex.validate(validDataset()));
        assertThrows(IllegalArgumentException.class, () -> invalidExtraction.validate(validDataset()));
    }

    private static GoldenRetrievalDataset validDataset() {
        return dataset("query", new float[]{0.1f, 0.2f, 0.3f, 0.4f}, List.of(1));
    }

    private static GoldenRetrievalDataset dataset(String query, float[] queryVector, List<Integer> kValues) {
        return new GoldenRetrievalDataset(
                1, "1.0.0", 1, kValues, List.of(new GoldenRetrievalDataset.Case(
                        "test-case", "TEST", "EASY", "EXACT", "STRICT_SOURCE", query,
                        Set.of("ver-test-1"), Set.of("node-test"), Set.of("chunk-test"),
                        Set.of(), Set.of(), queryVector)));
    }

    private static GoldenRetrievalCorpus corpus() {
        return corpus(oneMaterial(), oneVersion(), oneNode(), oneChunk(), oneEmbedding());
    }

    private static GoldenRetrievalCorpus corpus(
            Map<String, GoldenRetrievalCorpus.Material> materials,
            Map<String, GoldenRetrievalCorpus.MaterialVersion> versions,
            Map<String, GoldenRetrievalCorpus.DocumentNode> nodes,
            Map<String, GoldenRetrievalCorpus.Chunk> chunks,
            Map<String, float[]> embeddings
    ) {
        return new GoldenRetrievalCorpus(
                Map.of("user-eval", new GoldenRetrievalCorpus.User("test")),
                Map.of("anatomy", new GoldenRetrievalCorpus.Subject("Anatomy")),
                Map.of(), materials, versions, List.of(), nodes, chunks,
                Map.of("gen-v1", new GoldenRetrievalCorpus.IndexGeneration("synthetic-v1", 4)),
                embeddings);
    }

    private static Map<String, GoldenRetrievalCorpus.Material> oneMaterial() {
        return Map.of("mat-test", new GoldenRetrievalCorpus.Material("Test", "anatomy"));
    }

    private static Map<String, GoldenRetrievalCorpus.MaterialVersion> oneVersion() {
        return Map.of("ver-test-1", new GoldenRetrievalCorpus.MaterialVersion("mat-test", "1.0"));
    }

    private static Map<String, GoldenRetrievalCorpus.DocumentNode> oneNode() {
        return Map.of("node-test", new GoldenRetrievalCorpus.DocumentNode("ver-test-1", "Test"));
    }

    private static Map<String, GoldenRetrievalCorpus.Chunk> oneChunk() {
        return Map.of("chunk-test", new GoldenRetrievalCorpus.Chunk(
                "node-test", 1, "test content", 1, 1, "Test", "STRONG", "TEXT", "NATIVE"));
    }

    private static Map<String, float[]> oneEmbedding() {
        return Map.of("chunk-test", new float[]{0.1f, 0.2f, 0.3f, 0.4f});
    }
}
