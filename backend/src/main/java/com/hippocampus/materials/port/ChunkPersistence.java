package com.hippocampus.materials.port;

import java.util.List;
import java.util.UUID;

import com.hippocampus.materials.domain.ChunkDraft;
import com.hippocampus.materials.domain.ChunkingExecutionSummary;

public interface ChunkPersistence {
    void persistOrVerify(UUID materialVersionId, List<ChunkDraft> chunks);

    void finalizeChunking(UUID materialVersionId, ChunkingExecutionSummary summary);
}
