package com.hippocampus.rag.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import com.hippocampus.rag.port.ChunkEmbedding;
import com.hippocampus.rag.port.EmbeddableChunk;
import com.hippocampus.rag.port.EmbeddingBatchRequest;
import com.hippocampus.rag.port.EmbeddingBatchResult;
import com.hippocampus.rag.port.EmbeddingFailureException;
import com.hippocampus.rag.port.EmbeddingInput;
import com.hippocampus.rag.port.EmbeddingJobRepository;
import com.hippocampus.rag.port.EmbeddingPort;
import com.hippocampus.rag.port.EmbeddingVectorResult;
import com.hippocampus.rag.port.IndexGeneration;

public final class EmbedMaterialVersion {
    public static final String CHUNKING_VERSION = "CHUNKER_V1";

    private final EmbeddingJobRepository repository;
    private final EmbeddingPort provider;
    private final PersistEmbeddingBatch persistence;
    private final int batchSize;

    public EmbedMaterialVersion(
            EmbeddingJobRepository repository,
            EmbeddingPort provider,
            PersistEmbeddingBatch persistence,
            int batchSize) {
        this.repository = Objects.requireNonNull(repository);
        this.provider = Objects.requireNonNull(provider);
        this.persistence = Objects.requireNonNull(persistence);
        if (batchSize < 1) {
            throw new IllegalArgumentException("Embedding batch size must be positive");
        }
        this.batchSize = batchSize;
    }

    public void execute(
            UUID materialVersionId,
            Runnable verifyOwnership,
            BiConsumer<Long, Long> progress) {
        Objects.requireNonNull(materialVersionId, "materialVersionId must not be null");
        Objects.requireNonNull(verifyOwnership, "verifyOwnership must not be null");
        Objects.requireNonNull(progress, "progress must not be null");

        long total = repository.countEligibleChunks(materialVersionId);
        IndexGeneration generation = repository.findPreferredGeneration().orElse(null);
        if (generation != null && !CHUNKING_VERSION.equals(generation.chunkingVersion())) {
            throw new IllegalStateException("Selected index generation uses an incompatible chunking version");
        }
        long durable = generation == null
                ? 0
                : repository.countDurableEmbeddings(materialVersionId, generation.id());
        progress.accept(durable, total);

        while (durable < total) {
            verifyOwnership.run();
            List<EmbeddableChunk> chunks = repository.findMissingChunks(
                    materialVersionId, generation == null ? null : generation.id(), batchSize);
            if (chunks.isEmpty()) {
                throw new IllegalStateException("Embedding progress conflicts with eligible chunks");
            }

            EmbeddingBatchResult result = provider.embed(new EmbeddingBatchRequest(chunks.stream()
                    .map(chunk -> new EmbeddingInput(chunk.id(), chunk.content()))
                    .toList()));
            List<ChunkEmbedding> embeddings = validateBatch(chunks, result, generation);
            verifyOwnership.run();
            generation = persistence.execute(
                    materialVersionId, generation, result.model(), CHUNKING_VERSION, embeddings);
            durable = repository.countDurableEmbeddings(materialVersionId, generation.id());
            progress.accept(durable, total);
        }
    }

    private static List<ChunkEmbedding> validateBatch(
            List<EmbeddableChunk> chunks,
            EmbeddingBatchResult result,
            IndexGeneration generation) {
        if (result == null || generation != null
                && !generation.isCompatibleWith(result.model(), CHUNKING_VERSION)) {
            throw invalidResponse();
        }

        Set<UUID> requested = new HashSet<>();
        for (EmbeddableChunk chunk : chunks) {
            requested.add(chunk.id());
        }
        if (result.vectors().size() != chunks.size()) {
            throw invalidResponse();
        }

        Map<UUID, EmbeddingVectorResult> byReference = new HashMap<>();
        for (EmbeddingVectorResult vector : result.vectors()) {
            if (!requested.contains(vector.referenceId())
                    || byReference.put(vector.referenceId(), vector) != null) {
                throw invalidResponse();
            }
        }

        List<ChunkEmbedding> embeddings = new ArrayList<>(chunks.size());
        for (EmbeddableChunk chunk : chunks) {
            EmbeddingVectorResult vector = byReference.get(chunk.id());
            if (vector == null) {
                throw invalidResponse();
            }
            embeddings.add(new ChunkEmbedding(chunk.id(), vector.vector()));
        }
        return List.copyOf(embeddings);
    }

    private static EmbeddingFailureException invalidResponse() {
        return new EmbeddingFailureException(EmbeddingFailureException.Reason.INVALID_RESPONSE);
    }
}
