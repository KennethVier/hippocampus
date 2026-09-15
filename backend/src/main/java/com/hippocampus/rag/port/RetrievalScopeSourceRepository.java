package com.hippocampus.rag.port;

import java.util.List;
import java.util.UUID;

public interface RetrievalScopeSourceRepository {
    List<RetrievalScopeSource> findActiveAuthorizedTargets(UUID userId, UUID topicId);
}
