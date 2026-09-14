package com.hippocampus.rag.port;

import java.util.List;

public interface LexicalSearchRepository {
    List<LexicalSearchHit> search(LexicalSearchRequest request);
}
