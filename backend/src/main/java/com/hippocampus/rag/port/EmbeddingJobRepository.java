package com.hippocampus.rag.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmbeddingJobRepository {
    Optional<IndexGeneration> findPreferredGeneration();

    long countEligibleChunks(UUID materialVersionId);

    long countDurableEmbeddings(UUID materialVersionId, UUID indexGenerationId);

    List<EmbeddableChunk> findMissingChunks(
            UUID materialVersionId, UUID indexGenerationId, int batchSize);

    IndexGeneration getOrCreateInitialGeneration(
            EmbeddingModelMetadata model, String chunkingVersion);

    void persistBatch(
            UUID materialVersionId, UUID indexGenerationId, List<ChunkEmbedding> embeddings);
}
