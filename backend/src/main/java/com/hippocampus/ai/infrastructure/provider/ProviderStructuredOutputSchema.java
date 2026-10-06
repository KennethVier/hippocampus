package com.hippocampus.ai.infrastructure.provider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hippocampus.ai.application.prompt.PromptId;
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
    private static final Map<String, Object> RESPONSE_EVALUATION_JUDGMENT_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "expectedConceptIndex", Map.of("type", "integer"),
                    "expectedConcept", STRING_SCHEMA,
                    "studentClaims", STRING_ARRAY_SCHEMA,
                    "status", enumSchema("SUPPORTED", "PARTIAL", "MISSING", "CONTRADICTED"),
                    "supportedComponents", STRING_ARRAY_SCHEMA,
                    "missingComponents", STRING_ARRAY_SCHEMA,
                    "demonstratedMisconceptions", STRING_ARRAY_SCHEMA),
            "required", List.of(
                    "expectedConceptIndex",
                    "expectedConcept",
                    "studentClaims",
                    "status",
                    "supportedComponents",
                    "missingComponents",
                    "demonstratedMisconceptions"));
    private static final Map<String, Object> RESPONSE_EVALUATION_ATOMIC_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "judgments", Map.of(
                            "type", "array",
                            "items", RESPONSE_EVALUATION_JUDGMENT_SCHEMA),
                    "assessability", enumSchema(
                            "EVALUABLE",
                            "AMBIGUOUS_RESPONSE",
                            "INSUFFICIENT_EXPECTED_EVIDENCE"),
                    "feedback", STRING_SCHEMA,
                    "recommendedAction", enumSchema(
                            "CONTINUE",
                            "RETRY",
                            "TARGETED_EXPLANATION",
                            "PREREQUISITE_SUPPORT",
                            "CONNECTION_SUPPORT",
                            "GUIDED_REASONING",
                            "MANUAL_REVIEW"),
                    "sourceReferences", STRING_ARRAY_SCHEMA,
                    "limitations", STRING_ARRAY_SCHEMA),
            "required", List.of(
                    "judgments",
                    "assessability",
                    "feedback",
                    "recommendedAction",
                    "sourceReferences",
                    "limitations"));
    private static final String GEMINI_RESPONSE_EVALUATION_ATOMIC_SCHEMA =
            toJson(RESPONSE_EVALUATION_ATOMIC_SCHEMA);

    private ProviderStructuredOutputSchema() {}

    public static String geminiSchema(AiOutputContract outputContract) {
        return outputContract == AiOutputContract.EXPLANATION ? GEMINI_EXPLANATION_SCHEMA : null;
    }

    public static String geminiSchema(AiOutputContract outputContract, PromptId promptId) {
        if (outputContract == AiOutputContract.EXPLANATION) {
            return GEMINI_EXPLANATION_SCHEMA;
        }
        if (outputContract == AiOutputContract.RESPONSE_EVALUATION
                && (promptId == PromptId.RESPONSE_EVALUATION_V4
                        || promptId == PromptId.RESPONSE_EVALUATION_V5
                        || promptId == PromptId.RESPONSE_EVALUATION_V6)) {
            return GEMINI_RESPONSE_EVALUATION_ATOMIC_SCHEMA;
        }
        return null;
    }

    public static Object ollamaFormat(AiOutputContract outputContract) {
        return outputContract == AiOutputContract.EXPLANATION ? EXPLANATION_SCHEMA : "json";
    }

    private static String geminiExplanationSchema() {
        Map<String, Object> schema = new LinkedHashMap<>(EXPLANATION_SCHEMA);
        schema.remove("additionalProperties");
        return toJson(schema);
    }

    private static Map<String, Object> enumSchema(String... values) {
        return Map.of(
                "type", "string",
                "enum", List.of(values));
    }

    private static String toJson(Map<String, Object> schema) {
        try {
            return OBJECT_MAPPER.writeValueAsString(schema);
        } catch (JsonProcessingException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
