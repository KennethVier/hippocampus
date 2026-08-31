package com.hippocampus.learning.port;

public record SubtopicPageRequest(int page, int size) {
    public SubtopicPageRequest {
        if (page < 0) throw new IllegalArgumentException("page must not be negative");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
    }
}
