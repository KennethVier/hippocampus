package com.hippocampus.ai.application.prompt;

import java.util.Objects;

public record PromptTemplate(
        PromptId promptId,
        int version,
        PromptAuthority authority,
        String content) {

    public PromptTemplate {
        Objects.requireNonNull(promptId, "promptId must not be null");
        Objects.requireNonNull(authority, "authority must not be null");
        Objects.requireNonNull(content, "content must not be null");
        if (version != promptId.version()) {
            throw new IllegalArgumentException("version must match promptId.version");
        }
        if (content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        if (promptId.isSystemPolicy() && authority != PromptAuthority.SYSTEM_POLICY) {
            throw new IllegalArgumentException("system prompt requires SYSTEM_POLICY authority");
        }
        if (!promptId.isSystemPolicy() && authority != PromptAuthority.TASK_CONTRACT) {
            throw new IllegalArgumentException("task prompt requires TASK_CONTRACT authority");
        }
    }
}
