package com.hippocampus.ai.infrastructure.prompt;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import com.hippocampus.ai.application.prompt.PromptTokenCounter;

public final class Utf8ByteLengthPromptTokenCounter implements PromptTokenCounter {

    @Override
    public int count(String text) {
        Objects.requireNonNull(text, "text must not be null");
        return text.getBytes(StandardCharsets.UTF_8).length;
    }
}
