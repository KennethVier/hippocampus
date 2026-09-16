package com.hippocampus.rag.application;

public record RetrievalInspectorLimits(
        int lexicalLimit,
        int vectorLimit,
        int hybridLimit,
        int maxQueryLength) {
    private static final int MAX_SEARCH_LIMIT = 100;
    private static final int MAX_QUERY_LENGTH = 10_000;

    public RetrievalInspectorLimits {
        requireBounded(lexicalLimit, MAX_SEARCH_LIMIT, "lexicalLimit");
        requireBounded(vectorLimit, MAX_SEARCH_LIMIT, "vectorLimit");
        requireBounded(hybridLimit, MAX_SEARCH_LIMIT, "hybridLimit");
        requireBounded(maxQueryLength, MAX_QUERY_LENGTH, "maxQueryLength");
    }

    private static void requireBounded(int value, int maximum, String name) {
        if (value < 1 || value > maximum) {
            throw new IllegalArgumentException(name + " must be between 1 and " + maximum);
        }
    }
}
