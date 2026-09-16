package com.hippocampus.rag.evaluation;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;

public class GoldenRetrievalDatasetLoader {
    private final ObjectMapper mapper = new ObjectMapper();

    public GoldenRetrievalDataset loadDataset(String path) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) throw new IOException("Dataset not found: " + path);
            GoldenRetrievalDataset dataset = mapper.readValue(is, GoldenRetrievalDataset.class);
            validate(dataset);
            return dataset;
        }
    }

    public GoldenRetrievalCorpus loadCorpus(String path) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) throw new IOException("Corpus not found: " + path);
            return mapper.readValue(is, GoldenRetrievalCorpus.class);
        }
    }

    private void validate(GoldenRetrievalDataset dataset) {
        if (dataset.primaryK() <= 0) throw new IllegalStateException("primaryK must be positive");
        if (dataset.kValues() == null || dataset.kValues().isEmpty()) {
            throw new IllegalStateException("kValues must not be empty");
        }
        if (!dataset.kValues().contains(dataset.primaryK())) {
            throw new IllegalStateException("primaryK must be present in kValues");
        }
        for (Integer k : dataset.kValues()) {
            if (k <= 0) throw new IllegalStateException("all K values must be positive");
        }
        Set<Integer> uniqueK = new HashSet<>(dataset.kValues());
        if (uniqueK.size() != dataset.kValues().size()) {
            throw new IllegalStateException("kValues must be unique");
        }

        Set<String> ids = new HashSet<>();
        for (GoldenRetrievalDataset.Case c : dataset.cases()) {
            if (!ids.add(c.id())) throw new IllegalStateException("duplicate case ID: " + c.id());
            if (c.query() == null || c.query().isBlank()) throw new IllegalStateException("blank query in case: " + c.id());
            if (c.expectedChunkKeys() == null || c.expectedChunkKeys().isEmpty()) {
                throw new IllegalStateException("no expected chunks in case: " + c.id());
            }

            // Pairwise disjoint sets
            Set<String> expected = c.expectedChunkKeys();
            Set<String> acceptable = c.acceptableAlternativeChunkKeys() != null ? c.acceptableAlternativeChunkKeys() : Set.of();
            Set<String> irrelevant = c.irrelevantChunkKeys() != null ? c.irrelevantChunkKeys() : Set.of();

            for (String s : expected) {
                if (acceptable.contains(s)) throw new IllegalStateException("chunk " + s + " is both expected and acceptable in case " + c.id());
                if (irrelevant.contains(s)) throw new IllegalStateException("chunk " + s + " is both expected and irrelevant in case " + c.id());
            }
            for (String s : acceptable) {
                if (irrelevant.contains(s)) throw new IllegalStateException("chunk " + s + " is both acceptable and irrelevant in case " + c.id());
            }
        }
    }
}
