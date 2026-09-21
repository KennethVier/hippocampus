package com.hippocampus.ai.domain;

public record QuestionOption(String id, String text) {

    public QuestionOption {
        id = ContractChecks.requiredText(id, "id");
        text = ContractChecks.requiredText(text, "text");
    }
}
