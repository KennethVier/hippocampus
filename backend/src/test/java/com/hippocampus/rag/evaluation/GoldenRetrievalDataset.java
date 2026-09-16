package com.hippocampus.rag.evaluation;

import java.util.*;

public record GoldenRetrievalDataset(
        int schemaVersion,
        String datasetVersion,
        int primaryK,
        List<Integer> kValues,
        List<Case> cases) {

    public record Case(
            String id,
            String subject,
            String difficulty,
            String queryType,
            String groundingMode,
            String query,
            Set<String> allowedSourceKeys,
            Set<String> expectedSectionKeys,
            Set<String> expectedChunkKeys,
            Set<String> acceptableAlternativeChunkKeys,
            Set<String> irrelevantChunkKeys,
            float[] queryVector
    ) {}
}
