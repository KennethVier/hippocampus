package com.hippocampus.rag.evaluation;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Negative validation tests for GoldenRetrievalDataset and GoldenRetrievalCorpus.
 * ponytail: These tests validate that malformed data is rejected without silent repair.
 */
public class GoldenRetrievalValidationTests {

    @Test
    void testEmbeddingDimensionValidation() {
        // Create a valid corpus and verify it validates correctly
        Map<String, float[]> embeddings = new HashMap<>();
        embeddings.put("chunk-test", new float[]{0.1f, 0.2f, 0.3f, 0.4f});

        GoldenRetrievalCorpus corpus = new GoldenRetrievalCorpus(
                Map.of("user-eval", new GoldenRetrievalCorpus.User("test")),
                Map.of("anatomy", new GoldenRetrievalCorpus.Subject("Anatomy")),
                Map.of(), // topics - empty is OK for this test
                Map.of(), // materials
                Map.of("ver-test-1", new GoldenRetrievalCorpus.MaterialVersion("mat-test", "1.0")),
                List.of(), // materialTopicLinks
                Map.of("node-test", new GoldenRetrievalCorpus.DocumentNode("ver-test-1", "Test")),
                Map.of("chunk-test", new GoldenRetrievalCorpus.Chunk(
                        "node-test", 1, "test content", 1, 10, "Test", "STRONG", "TEXT", "NATIVE")
                ),
                Map.of("gen-v1", new GoldenRetrievalCorpus.IndexGeneration("synthetic", 4)),
                embeddings
        );

        // Create a minimal dataset - the validation will check embedding dimension
        GoldenRetrievalDataset dataset = new GoldenRetrievalDataset(
                1, "v1.0", 3, List.of(1, 3, 5), List.of(new GoldenRetrievalDataset.Case(
                        "test-case", "TEST", "EASY", "EXACT",
                        "STRICT_SOURCE", "test query", Set.of("ver-test-1"),
                        Set.of("node-test"), Set.of("chunk-test"),
                        Set.of(), Set.of(), new float[]{0.1f}
                )));

        assertDoesNotThrow(() -> corpus.validate(dataset), "Valid corpus should pass validation");
    }

    @Test
    void testRejectNonFiniteEmbeddingValues() {
        // Create a corpus with non-finite embedding value - should be rejected
        Map<String, float[]> invalidEmbeddings = new HashMap<>();
        invalidEmbeddings.put("chunk-test", new float[]{0.1f, Float.POSITIVE_INFINITY});

        GoldenRetrievalCorpus corpus = new GoldenRetrievalCorpus(
                Map.of("user-eval", new GoldenRetrievalCorpus.User("test")),
                Map.of("anatomy", new GoldenRetrievalCorpus.Subject("Anatomy")),
                Map.of(),
                Map.of(),
                Map.of("ver-test-1", new GoldenRetrievalCorpus.MaterialVersion("mat-test", "1.0")),
                List.of(),
                Map.of("node-test", new GoldenRetrievalCorpus.DocumentNode("ver-test-1", "Test")),
                Map.of("chunk-test", new GoldenRetrievalCorpus.Chunk(
                        "node-test", 1, "test content", 1, 10, "Test", "STRONG", "TEXT", "NATIVE")
                ),
                Map.of("gen-v1", new GoldenRetrievalCorpus.IndexGeneration("synthetic", 4)),
                invalidEmbeddings
        );

        // Create a minimal dataset
        GoldenRetrievalDataset dataset = new GoldenRetrievalDataset(
                1, "v1.0", 3, List.of(1, 3, 5), List.of(new GoldenRetrievalDataset.Case(
                        "test-case", "TEST", "EASY", "EXACT",
                        "STRICT_SOURCE", "test query", Set.of("ver-test-1"),
                        Set.of("node-test"), Set.of("chunk-test"),
                        Set.of(), Set.of(), new float[]{0.1f}
                )));

        assertThrows(IllegalArgumentException.class, () -> corpus.validate(dataset),
                "Should reject non-finite embedding values");
    }

    @Test
    void testRejectNegativeChunkIndex() {
        // Create a corpus with negative chunk index - should be rejected
        Map<String, float[]> embeddings = new HashMap<>();
        embeddings.put("chunk-test", new float[]{0.1f, 0.2f, 0.3f, 0.4f});

        GoldenRetrievalCorpus corpus = new GoldenRetrievalCorpus(
                Map.of("user-eval", new GoldenRetrievalCorpus.User("test")),
                Map.of("anatomy", new GoldenRetrievalCorpus.Subject("Anatomy")),
                Map.of(),
                Map.of(),
                Map.of("ver-test-1", new GoldenRetrievalCorpus.MaterialVersion("mat-test", "1.0")),
                List.of(),
                Map.of("node-test", new GoldenRetrievalCorpus.DocumentNode("ver-test-1", "Test")),
                Map.of("chunk-test", new GoldenRetrievalCorpus.Chunk(
                        "node-test", -1, "test content", 1, 10, "Test", "STRONG", "TEXT", "NATIVE") // negative index
                ),
                Map.of("gen-v1", new GoldenRetrievalCorpus.IndexGeneration("synthetic", 4)),
                embeddings
        );

        // Create a minimal dataset
        GoldenRetrievalDataset dataset = new GoldenRetrievalDataset(
                1, "v1.0", 3, List.of(1, 3, 5), List.of(new GoldenRetrievalDataset.Case(
                        "test-case", "TEST", "EASY", "EXACT",
                        "STRICT_SOURCE", "test query", Set.of("ver-test-1"),
                        Set.of("node-test"), Set.of("chunk-test"),
                        Set.of(), Set.of(), new float[]{0.1f}
                )));

        assertThrows(IllegalArgumentException.class, () -> corpus.validate(dataset),
                "Should reject chunk with negative index");
    }

    @Test
    void testRejectInvalidExtractionMethod() {
        // Create a corpus with invalid extraction method - should be rejected
        Map<String, float[]> embeddings = new HashMap<>();
        embeddings.put("chunk-test", new float[]{0.1f, 0.2f, 0.3f, 0.4f});

        GoldenRetrievalCorpus corpus = new GoldenRetrievalCorpus(
                Map.of("user-eval", new GoldenRetrievalCorpus.User("test")),
                Map.of("anatomy", new GoldenRetrievalCorpus.Subject("Anatomy")),
                Map.of(),
                Map.of(),
                Map.of("ver-test-1", new GoldenRetrievalCorpus.MaterialVersion("mat-test", "1.0")),
                List.of(),
                Map.of("node-test", new GoldenRetrievalCorpus.DocumentNode("ver-test-1", "Test")),
                Map.of("chunk-test", new GoldenRetrievalCorpus.Chunk(
                        "node-test", 1, "test content", 1, 10, "Test", "STRONG", "TEXT", "INVALID_METHOD")
                ),
                Map.of("gen-v1", new GoldenRetrievalCorpus.IndexGeneration("synthetic", 4)),
                embeddings
        );

        // Create a minimal dataset
        GoldenRetrievalDataset dataset = new GoldenRetrievalDataset(
                1, "v1.0", 3, List.of(1, 3, 5), List.of(new GoldenRetrievalDataset.Case(
                        "test-case", "TEST", "EASY", "EXACT",
                        "STRICT_SOURCE", "test query", Set.of("ver-test-1"),
                        Set.of("node-test"), Set.of("chunk-test"),
                        Set.of(), Set.of(), new float[]{0.1f}
                )));

        assertThrows(IllegalArgumentException.class, () -> corpus.validate(dataset),
                "Should reject invalid extractionMethod");
    }
}
