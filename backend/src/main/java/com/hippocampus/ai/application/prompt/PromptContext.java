package com.hippocampus.ai.application.prompt;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PromptContext(
        PromptId systemPromptId,
        PromptId taskPromptId,
        String systemPrompt,
        String taskPrompt,
        int inputTokenCount,
        int reservedOutputTokens,
        List<IncludedSource> includedSources) {

    public PromptContext {
        Objects.requireNonNull(systemPromptId, "systemPromptId must not be null");
        Objects.requireNonNull(taskPromptId, "taskPromptId must not be null");
        Objects.requireNonNull(systemPrompt, "systemPrompt must not be null");
        Objects.requireNonNull(taskPrompt, "taskPrompt must not be null");
        Objects.requireNonNull(includedSources, "includedSources must not be null");
        if (!systemPromptId.isSystemPolicy()) {
            throw new IllegalArgumentException("systemPromptId must identify a system policy");
        }
        if (taskPromptId.isSystemPolicy()) {
            throw new IllegalArgumentException("taskPromptId must identify a task prompt");
        }
        if (systemPrompt.isBlank() || taskPrompt.isBlank()) {
            throw new IllegalArgumentException("prompt content must not be blank");
        }
        if (inputTokenCount < 0) {
            throw new IllegalArgumentException("inputTokenCount must not be negative");
        }
        if (reservedOutputTokens <= 0) {
            throw new IllegalArgumentException("reservedOutputTokens must be positive");
        }
        if (includedSources.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("includedSources must not contain null");
        }
        includedSources = List.copyOf(includedSources);
    }

    public record IncludedSource(
            int rank,
            UUID chunkId,
            UUID materialId,
            UUID materialVersionId,
            UUID documentNodeId,
            Integer pageStart,
            Integer pageEnd) {

        public IncludedSource {
            if (rank < 1) {
                throw new IllegalArgumentException("rank must be positive");
            }
            Objects.requireNonNull(chunkId, "chunkId must not be null");
            Objects.requireNonNull(materialId, "materialId must not be null");
            Objects.requireNonNull(materialVersionId, "materialVersionId must not be null");
        }
    }
}
