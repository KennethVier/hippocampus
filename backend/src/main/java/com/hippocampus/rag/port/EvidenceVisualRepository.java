package com.hippocampus.rag.port;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.hippocampus.rag.domain.RetrievalScope;

public interface EvidenceVisualRepository {
    List<EvidenceVisualSource> findLinkedVisuals(RetrievalScope scope, Set<UUID> selectedChunkIds);
}
