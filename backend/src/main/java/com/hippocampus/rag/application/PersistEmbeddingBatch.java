package com.hippocampus.rag.application;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.rag.port.ChunkEmbedding;
import com.hippocampus.rag.port.EmbeddingJobRepository;
import com.hippocampus.rag.port.EmbeddingFailureException;
import com.hippocampus.rag.port.EmbeddingModelMetadata;
import com.hippocampus.rag.port.IndexGeneration;

public class PersistEmbeddingBatch {
    private final EmbeddingJobRepository repository;

    public PersistEmbeddingBatch(EmbeddingJobRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Transactional
    public IndexGeneration execute(
            UUID materialVersionId,
            IndexGeneration generation,
            EmbeddingModelMetadata model,
            String chunkingVersion,
            List<ChunkEmbedding> embeddings) {
        IndexGeneration durableGeneration = generation == null
                ? repository.getOrCreateInitialGeneration(model, chunkingVersion)
                : generation;
        if (!durableGeneration.isCompatibleWith(model, chunkingVersion)) {
            throw new EmbeddingFailureException(EmbeddingFailureException.Reason.INVALID_RESPONSE);
        }
        repository.persistBatch(materialVersionId, durableGeneration.id(), embeddings);
        return durableGeneration;
    }
}
