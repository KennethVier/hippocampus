package com.hippocampus.ai.application.prompt;

public record PromptTokenBudget(int maxContextTokens, int reservedOutputTokens) {

    public PromptTokenBudget {
        if (reservedOutputTokens <= 0) {
            throw new IllegalArgumentException("reservedOutputTokens must be positive");
        }
        if (maxContextTokens <= reservedOutputTokens) {
            throw new IllegalArgumentException(
                    "maxContextTokens must be greater than reservedOutputTokens");
        }
    }

    public int maxInputTokens() {
        return maxContextTokens - reservedOutputTokens;
    }
}
