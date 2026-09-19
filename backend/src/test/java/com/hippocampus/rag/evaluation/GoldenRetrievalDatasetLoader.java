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
            dataset.validate();
            return dataset;
        }
    }

    public GoldenRetrievalCorpus loadCorpus(String path, GoldenRetrievalDataset dataset) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) throw new IOException("Corpus not found: " + path);
            GoldenRetrievalCorpus corpus = mapper.readValue(is, GoldenRetrievalCorpus.class);
            corpus.validate(dataset);
            return corpus;
        }
    }

}
