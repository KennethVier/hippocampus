package com.hippocampus.ai.port;

public interface AiStructuredOutputDecoder {

    <T> T decode(String rawOutput, Class<T> resultType);
}
