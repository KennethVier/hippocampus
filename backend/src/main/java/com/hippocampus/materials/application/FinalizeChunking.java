package com.hippocampus.materials.application;
import java.util.List; import java.util.Objects; import java.util.UUID; import org.springframework.transaction.annotation.Transactional; import com.hippocampus.materials.domain.ChunkDraft; import com.hippocampus.materials.port.ChunkPersistence;
public class FinalizeChunking { private final ChunkPersistence persistence; public FinalizeChunking(ChunkPersistence p){persistence=Objects.requireNonNull(p);} @Transactional public void execute(UUID v,int pages,List<ChunkDraft>d){persistence.finalizeChunking(v,pages,d);} }
