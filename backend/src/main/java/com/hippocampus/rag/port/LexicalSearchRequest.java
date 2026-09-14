package com.hippocampus.rag.port;

import java.util.Objects;

public record LexicalSearchRequest(LexicalSearchScope scope, String query, int limit) {
    public LexicalSearchRequest {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(query, "query must not be null");
        if (query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
    }
}
