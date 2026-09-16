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
