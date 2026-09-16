package com.hippocampus.rag.port;

import java.util.Optional;

public interface ActiveIndexGenerationRepository {
    Optional<IndexGeneration> findActiveGeneration();
}
