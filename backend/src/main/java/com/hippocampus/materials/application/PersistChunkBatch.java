package com.hippocampus.materials.application;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.materials.domain.ChunkDraft;
import com.hippocampus.materials.port.ChunkPersistence;

public class PersistChunkBatch {
    private final ChunkPersistence persistence;

    public PersistChunkBatch(ChunkPersistence persistence) {
        this.persistence = Objects.requireNonNull(persistence);
    }

    @Transactional
    public void execute(UUID materialVersionId, List<ChunkDraft> chunks) {
        persistence.persistOrVerify(materialVersionId, chunks);
    }
}
