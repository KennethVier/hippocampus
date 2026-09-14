package com.hippocampus.rag.port;

import java.util.List;

public interface VectorSearchRepository {
    List<VectorSearchHit> search(VectorSearchRequest request);
}
