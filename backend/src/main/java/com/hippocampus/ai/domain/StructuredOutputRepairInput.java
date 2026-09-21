package com.hippocampus.ai.domain;

public record StructuredOutputRepairInput(String previousResponse) implements AiTaskContext {

    public StructuredOutputRepairInput {
        previousResponse = ContractChecks.requiredText(previousResponse, "previousResponse");
    }
}
