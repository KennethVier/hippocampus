package com.hippocampus.ai.infrastructure.provider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hippocampus.ai.domain.AiOutputContract;

public final class ProviderStructuredOutputSchema {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Map<String, Object> STRING_SCHEMA = Map.of("type", "string");
    private static final Map<String, Object> STRING_ARRAY_SCHEMA = Map.of(
            "type", "array",
            "items", STRING_SCHEMA);
    private static final List<String> EXPLANATION_REQUIRED_PROPERTIES = List.of(
            "concept",
            "explanation",
            "keyPoints",
            "prerequisitesUsed",
            "sourceReferences",
            "supplementalKnowledgeUsed",
            "limitations");
    private static final Map<String, Object> EXPLANATION_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "concept", STRING_SCHEMA,
                    "explanation", STRING_SCHEMA,
                    "keyPoints", STRING_ARRAY_SCHEMA,
                    "prerequisitesUsed", STRING_ARRAY_SCHEMA,
                    "sourceReferences", STRING_ARRAY_SCHEMA,
                    "supplementalKnowledgeUsed", Map.of("type", "boolean"),
                    "limitations", STRING_ARRAY_SCHEMA),
            "required", EXPLANATION_REQUIRED_PROPERTIES,
            "additionalProperties", false);
    private static final String GEMINI_EXPLANATION_SCHEMA = geminiExplanationSchema();

    private ProviderStructuredOutputSchema() {}

    public static String geminiSchema(AiOutputContract outputContract) {
        return outputContract == AiOutputContract.EXPLANATION ? GEMINI_EXPLANATION_SCHEMA : null;
    }

    public static Object ollamaFormat(AiOutputContract outputContract) {
        return outputContract == AiOutputContract.EXPLANATION ? EXPLANATION_SCHEMA : "json";
    }

    private static String geminiExplanationSchema() {
        Map<String, Object> schema = new LinkedHashMap<>(EXPLANATION_SCHEMA);
        schema.remove("additionalProperties");
        try {
            return OBJECT_MAPPER.writeValueAsString(schema);
        } catch (JsonProcessingException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
