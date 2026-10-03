package com.hippocampus.ai.domain;

/**
 * Server-owned repair context. {@code originalTaskPromptVersion} is the versioned prompt identity
 * name of the task prompt that produced the malformed output; it is set by the orchestrator from
 * trusted execution state and resolved against the prompt registry (never inferred from output).
 */
public record StructuredOutputRepairInput(String previousResponse, String originalTaskPromptVersion)
        implements AiTaskContext {

    public StructuredOutputRepairInput {
        previousResponse = ContractChecks.requiredText(previousResponse, "previousResponse");
        originalTaskPromptVersion =
                ContractChecks.requiredText(originalTaskPromptVersion, "originalTaskPromptVersion");
    }
}
