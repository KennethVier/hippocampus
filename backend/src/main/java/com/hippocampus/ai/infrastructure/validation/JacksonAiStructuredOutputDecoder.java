package com.hippocampus.ai.infrastructure.validation;

import java.util.Objects;

import com.hippocampus.ai.port.AiOutputDecodingException;
import com.hippocampus.ai.port.AiStructuredOutputDecoder;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;

public final class JacksonAiStructuredOutputDecoder implements AiStructuredOutputDecoder {

    private final ObjectMapper objectMapper;

    public JacksonAiStructuredOutputDecoder() {
        this.objectMapper = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .build();
    }

    @Override
    public <T> T decode(String rawOutput, Class<T> resultType) {
        Objects.requireNonNull(rawOutput, "rawOutput must not be null");
        Objects.requireNonNull(resultType, "resultType must not be null");

        JsonNode root;
        try {
            root = objectMapper.readTree(rawOutput);
        } catch (JacksonException exception) {
            throw new AiOutputDecodingException(AiOutputDecodingException.Kind.MALFORMED_JSON);
        }
        if (root == null || !root.isObject()) {
            throw new AiOutputDecodingException(AiOutputDecodingException.Kind.CONTRACT_MISMATCH);
        }

        try {
            T decoded = objectMapper.treeToValue(root, resultType);
            if (decoded == null) {
                throw new AiOutputDecodingException(AiOutputDecodingException.Kind.CONTRACT_MISMATCH);
            }
            return decoded;
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new AiOutputDecodingException(AiOutputDecodingException.Kind.CONTRACT_MISMATCH);
        }
    }
}
