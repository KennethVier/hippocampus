package com.hippocampus.rag.evaluation;

import java.util.*;

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
        for (Map.Entry<String, Chunk> entry : chunks.entrySet()) {
            String chunkKey = entry.getKey();
            Chunk chunk = entry.getValue();

            DocumentNode node = documentNodes.get(chunk.node());
            if (node == null) throw new IllegalArgumentException("chunk " + chunkKey + " references missing node " + chunk.node());

            MaterialVersion version = materialVersions.get(node.version());
            if (version == null) throw new IllegalArgumentException("node " + node.version() + " references missing version");

            if (!chunkEmbeddings.containsKey(chunkKey)) throw new IllegalArgumentException("missing embedding for chunk " + chunkKey);
            float[] vec = chunkEmbeddings.get(chunkKey);
            for (float v : vec) if (!Float.isFinite(v)) throw new IllegalArgumentException("non-finite value in embedding for chunk " + chunkKey);
        }

        for (GoldenRetrievalDataset.Case c : dataset.cases()) {
            for (String sourceKey : c.allowedSourceKeys()) {
                if (!materialVersions.containsKey(sourceKey)) throw new IllegalArgumentException("allowed source " + sourceKey + " missing in corpus for case " + c.id());
            }
            for (String chunkKey : c.expectedChunkKeys()) {
                if (!chunks.containsKey(chunkKey)) throw new IllegalArgumentException("expected chunk " + chunkKey + " missing in corpus for case " + c.id());
            }
            for (String chunkKey : c.acceptableAlternativeChunkKeys()) {
                if (!chunks.containsKey(chunkKey)) throw new IllegalArgumentException("acceptable chunk " + chunkKey + " missing in corpus for case " + c.id());
            }
            for (String chunkKey : c.irrelevantChunkKeys()) {
                if (!chunks.containsKey(chunkKey)) throw new IllegalArgumentException("irrelevant chunk " + chunkKey + " missing in corpus for case " + c.id());
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
