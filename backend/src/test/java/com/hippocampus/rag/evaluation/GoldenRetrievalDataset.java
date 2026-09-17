package com.hippocampus.rag.evaluation;

import java.util.*;

public record GoldenRetrievalDataset(
        int schemaVersion,
        String datasetVersion,
        int primaryK,
        List<Integer> kValues,
        List<Case> cases) {

    public void validate() {
        if (schemaVersion != 1) throw new IllegalArgumentException("unsupported schema version: " + schemaVersion);
        if (datasetVersion == null || datasetVersion.isBlank()) throw new IllegalArgumentException("dataset version must be provided");
        if (primaryK <= 0) throw new IllegalArgumentException("primaryK must be positive");
        if (kValues == null || kValues.isEmpty()) throw new IllegalArgumentException("kValues must not be empty");
        if (kValues.contains(null)) throw new IllegalArgumentException("kValues must not contain nulls");
        if (kValues.stream().anyMatch(k -> k == null || k <= 0)) throw new IllegalArgumentException("kValues must be positive");
        if (kValues.stream().distinct().count() != kValues.size()) throw new IllegalArgumentException("kValues must not contain duplicates");
        if (!kValues.contains(primaryK)) throw new IllegalArgumentException("primaryK must be present in kValues");

        Set<String> caseIds = new HashSet<>();
        for (Case c : cases) {
            if (c.id() == null || c.id().isBlank()) throw new IllegalArgumentException("case id must be provided");
            if (!caseIds.add(c.id())) throw new IllegalArgumentException("duplicate case id: " + c.id());
            if (c.queryType() == null || !Set.of("EXACT", "SEMANTIC", "MIXED").contains(c.queryType())) throw new IllegalArgumentException("invalid queryType for case " + c.id());
            if (c.groundingMode() == null || !Set.of("STRICT_SOURCE").contains(c.groundingMode())) throw new IllegalArgumentException("invalid groundingMode for case " + c.id());
            if (c.queryVector() == null) throw new IllegalArgumentException("queryVector must be provided for case " + c.id());
            for (float v : c.queryVector()) if (!Float.isFinite(v)) throw new IllegalArgumentException("non-finite value in queryVector for case " + c.id());
            boolean allZero = true;
            for (float v : c.queryVector()) if (v != 0) allZero = false;
            if (allZero) throw new IllegalArgumentException("queryVector must be non-zero for case " + c.id());

            if (c.allowedSourceKeys() == null || c.allowedSourceKeys().isEmpty()) throw new IllegalArgumentException("allowed sources must be provided for case " + c.id());
            if (c.expectedChunkKeys() == null || c.expectedChunkKeys().isEmpty()) throw new IllegalArgumentException("expected chunks must be provided for case " + c.id());
            if (c.expectedSectionKeys() == null || c.expectedSectionKeys().isEmpty()) throw new IllegalArgumentException("expected sections must be provided for case " + c.id());

            // Pairwise disjoint check
            Set<String> allChunks = new HashSet<>(c.expectedChunkKeys());
            allChunks.addAll(c.acceptableAlternativeChunkKeys());
            allChunks.addAll(c.irrelevantChunkKeys());
            if (allChunks.size() != (c.expectedChunkKeys().size() + c.acceptableAlternativeChunkKeys().size() + c.irrelevantChunkKeys().size())) {
                throw new IllegalArgumentException("expected, acceptable, and irrelevant chunk sets must be pairwise disjoint for case " + c.id());
            }
        }
    }

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
