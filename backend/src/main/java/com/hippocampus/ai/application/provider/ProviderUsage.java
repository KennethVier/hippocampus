package com.hippocampus.ai.application.provider;

import java.util.Objects;
import java.util.Optional;

public record ProviderUsage(
        Optional<Integer> inputTokens,
        Optional<Integer> outputTokens,
        Optional<Integer> totalTokens) {

    public static final ProviderUsage NONE = new ProviderUsage(
            Optional.empty(), Optional.empty(), Optional.empty());

    public ProviderUsage {
        Objects.requireNonNull(inputTokens, "inputTokens must not be null");
        Objects.requireNonNull(outputTokens, "outputTokens must not be null");
        Objects.requireNonNull(totalTokens, "totalTokens must not be null");
        inputTokens.ifPresent(value -> requireNonNegative(value, "inputTokens"));
        outputTokens.ifPresent(value -> requireNonNegative(value, "outputTokens"));
        totalTokens.ifPresent(value -> requireNonNegative(value, "totalTokens"));
    }

    public static ProviderUsage of(Integer inputTokens, Integer outputTokens, Integer totalTokens) {
        return new ProviderUsage(
                Optional.ofNullable(inputTokens),
                Optional.ofNullable(outputTokens),
                Optional.ofNullable(totalTokens));
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
