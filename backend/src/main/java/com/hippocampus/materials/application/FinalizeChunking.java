package com.hippocampus.materials.application;

import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.materials.domain.ChunkingExecutionSummary;
import com.hippocampus.materials.port.ChunkPersistence;

public class FinalizeChunking {
    private final ChunkPersistence persistence;

    public FinalizeChunking(ChunkPersistence persistence) {
        this.persistence = Objects.requireNonNull(persistence);
    }

    @Transactional
    public void execute(UUID materialVersionId, ChunkingExecutionSummary summary) {
        persistence.finalizeChunking(materialVersionId, summary);
    }
}
