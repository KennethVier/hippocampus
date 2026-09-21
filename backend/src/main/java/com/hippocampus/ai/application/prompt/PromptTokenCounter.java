package com.hippocampus.ai.application.prompt;

@FunctionalInterface
public interface PromptTokenCounter {

    int count(String text);
}
