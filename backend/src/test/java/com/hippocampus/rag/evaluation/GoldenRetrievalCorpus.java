package com.hippocampus.rag.evaluation;

import java.util.*;

/**
 * Golden Retrieval Corpus - a synthetic corpus for evaluation benchmarking.
 */
public record GoldenRetrievalCorpus(
        Map<String, User> users,
        Map<String, Subject> subjects,
        Map<String, Topic> topics,
        Map<String, Material> materials,
        Map<String, MaterialVersion> materialVersions,
        List<MaterialTopicLink> materialTopicLinks,
        Map<String, DocumentNode> documentNodes,
        Map<String, Chunk> chunks,
        Map<String, IndexGeneration> indexGenerations,
        Map<String, float[]> chunkEmbeddings) {

    public void validate(GoldenRetrievalDataset dataset) {
        if (users == null || users.isEmpty()) throw new IllegalArgumentException("corpus must contain at least one user");
        if (subjects == null || subjects.isEmpty()) throw new IllegalArgumentException("corpus must contain at least one subject");

        for (Map.Entry<String, Chunk> entry : chunks.entrySet()) {
            String chunkKey = entry.getKey();
            Chunk chunk = entry.getValue();

            DocumentNode node = documentNodes.get(chunk.node());
            if (node == null) throw new IllegalArgumentException("chunk " + chunkKey + " references missing node " + chunk.node());

            if (chunk.index() < 1) {
                throw new IllegalArgumentException("chunk " + chunkKey + " index must be >= 1");
            }
            if (chunk.extractionMethod() == null || !Set.of("NATIVE", "OCR").contains(chunk.extractionMethod())) {
                throw new IllegalArgumentException("chunk " + chunkKey + " invalid extractionMethod: " + chunk.extractionMethod());
            }


            if (!chunkEmbeddings.containsKey(chunkKey)) {
                throw new IllegalArgumentException("missing embedding for chunk " + chunkKey);
            }
            float[] vec = chunkEmbeddings.get(chunkKey);
            for (float v : vec) if (!Float.isFinite(v)) {
                throw new IllegalArgumentException("non-finite value in embedding for chunk " + chunkKey);
            }
        }

        for (Map.Entry<String, float[]> entry : chunkEmbeddings.entrySet()) {
            float[] vec = entry.getValue();
            int dim = indexGenerations.values().stream().findFirst().map(IndexGeneration::dimension).orElseThrow();
            if (vec.length != dim) throw new IllegalArgumentException("embedding for chunk " + entry.getKey() + " dimension mismatch");
            for (float v : vec) if (!Float.isFinite(v)) {
                throw new IllegalArgumentException("non-finite value in embedding for chunk " + entry.getKey());
            }
        }

        for (GoldenRetrievalDataset.Case c : dataset.cases()) {
            for (String sourceKey : c.allowedSourceKeys()) {
                if (!materialVersions.containsKey(sourceKey)) {
                    throw new IllegalArgumentException("allowed source " + sourceKey + " missing in corpus for case " + c.id());
                }
            }
            for (String sectionKey : c.expectedSectionKeys()) {
                DocumentNode node = documentNodes.get(sectionKey);
                if (node == null) throw new IllegalArgumentException("expected section " + sectionKey + " missing in corpus for case " + c.id());
                if (!c.allowedSourceKeys().contains(node.version())) {
                    throw new IllegalArgumentException("expected section " + sectionKey + " belongs to unauthorized source " + node.version() + " for case " + c.id());
                }
            }
            for (String chunkKey : c.expectedChunkKeys()) {
                Chunk chunk = chunks.get(chunkKey);
                if (chunk == null) throw new IllegalArgumentException("expected chunk " + chunkKey + " missing in corpus for case " + c.id());
                DocumentNode node = documentNodes.get(chunk.node());
                if (!c.allowedSourceKeys().contains(node.version())) {
                    throw new IllegalArgumentException("expected chunk " + chunkKey + " belongs to unauthorized source " + node.version() + " for case " + c.id());
                }
            }
            for (String chunkKey : c.acceptableAlternativeChunkKeys()) {
                Chunk chunk = chunks.get(chunkKey);
                if (chunk == null) throw new IllegalArgumentException("acceptable chunk " + chunkKey + " missing in corpus for case " + c.id());
                DocumentNode node = documentNodes.get(chunk.node());
                if (!c.allowedSourceKeys().contains(node.version())) {
                    throw new IllegalArgumentException("acceptable chunk " + chunkKey + " belongs to unauthorized source " + node.version() + " for case " + c.id());
                }
            }
            for (String chunkKey : c.irrelevantChunkKeys()) {
                Chunk chunk = chunks.get(chunkKey);
                if (chunk == null) throw new IllegalArgumentException("irrelevant chunk " + chunkKey + " missing in corpus for case " + c.id());
                DocumentNode node = documentNodes.get(chunk.node());
                if (!c.allowedSourceKeys().contains(node.version())) {
                    throw new IllegalArgumentException("irrelevant chunk " + chunkKey + " belongs to unauthorized source " + node.version() + " for case " + c.id());
                }
            }
        }
    }

    public record User(String name) {}
    public record Subject(String name) {}
    public record Topic(String name, String subject) {}
    public record Material(String title, String subject) {}
    public record MaterialVersion(String material, String version) {}
    public record MaterialTopicLink(String materialVersion, String topic) {}
    public record DocumentNode(String version, String title) {}
    public record Chunk(
            String node,
            int index,
            String content,
            Integer pageStart,
            Integer pageEnd,
            String heading,
            String quality,
            String contentType,
            String extractionMethod
    ) {}
    public record IndexGeneration(String model, int dimension) {}
}
