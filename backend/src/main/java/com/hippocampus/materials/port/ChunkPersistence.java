package com.hippocampus.materials.port;
import java.util.List; import java.util.UUID; import com.hippocampus.materials.domain.ChunkDraft;
public interface ChunkPersistence { void persistOrVerify(UUID version, List<ChunkDraft> chunks); void finalizeChunking(UUID version, int pageCount, List<ChunkDraft> expected); }
