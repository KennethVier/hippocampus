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
    private static final Map<String, Object> INTEGER_SCHEMA = Map.of("type", "integer");
    private static final Map<String, Object> INTEGER_ARRAY_SCHEMA = Map.of(
            "type", "array", "items", INTEGER_SCHEMA);
    private static final Map<String, Object> SOURCE_SPAN_SCHEMA = objectSchema(Map.of(
            "sourceReferenceId", STRING_SCHEMA,
            "sourceVersion", STRING_SCHEMA,
            "sourceStart", INTEGER_SCHEMA,
            "sourceEnd", INTEGER_SCHEMA,
            "sourceText", STRING_SCHEMA));
    private static final Map<String, Object> ASSERTION_SCHEMA = objectSchema(Map.of(
            "id", STRING_SCHEMA,
            "studentStart", INTEGER_SCHEMA,
            "studentEnd", INTEGER_SCHEMA,
            "studentText", STRING_SCHEMA,
            "proposition", STRING_SCHEMA,
            "polarity", enumSchema("AFFIRMED", "NEGATED", "UNCERTAIN"),
            "qualifiers", STRING_ARRAY_SCHEMA,
            "relatedExpectedConceptIndexes", INTEGER_ARRAY_SCHEMA));
    private static final Map<String, Object> CANDIDATE_SCHEMA = objectSchema(Map.of(
            "id", STRING_SCHEMA,
            "assertionIds", STRING_ARRAY_SCHEMA,
            "expectedConceptIndex", INTEGER_SCHEMA,
            "constituentMeaning", STRING_SCHEMA,
            "relationToExpectedConcept", STRING_SCHEMA,
            "sourceEvidence", arraySchema(SOURCE_SPAN_SCHEMA)));
    private static final String GEMINI_DISCOVERY_SCHEMA = toJson(objectSchema(Map.of(
            "assertions", arraySchema(ASSERTION_SCHEMA),
            "candidates", arraySchema(CANDIDATE_SCHEMA),
            "coveredExpectedConceptIndexes", INTEGER_ARRAY_SCHEMA,
            "complete", Map.of("type", "boolean"),
            "limitations", STRING_ARRAY_SCHEMA)));
    private static final String GEMINI_VERIFICATION_SCHEMA = toJson(objectSchema(Map.of(
            "assertions", arraySchema(objectSchema(Map.of(
                    "assertionId", STRING_SCHEMA,
                    "disposition", enumSchema("SUPPORTED", "UNSUPPORTED", "AMBIGUOUS"),
                    "reason", STRING_SCHEMA,
                    "fidelity", enumSchema("FAITHFUL", "MISMATCH", "AMBIGUOUS")))),
            "candidates", arraySchema(objectSchema(Map.of(
                    "candidateId", STRING_SCHEMA,
                    "disposition", enumSchema("SUPPORTED", "UNSUPPORTED", "AMBIGUOUS"),
                    "reason", STRING_SCHEMA))),
            "expectedConceptCoverage", arraySchema(objectSchema(Map.of(
                    "expectedConceptIndex", INTEGER_SCHEMA,
                    "coverage", enumSchema("COMPLETE", "MISSING", "AMBIGUOUS"),
                    "reason", STRING_SCHEMA))),
            "misconceptions", arraySchema(objectSchema(Map.of(
                    "assertionId", STRING_SCHEMA,
                    "description", STRING_SCHEMA))),
            "mechanismEvidence", SOURCE_SPAN_SCHEMA,
            "complete", Map.of("type", "boolean"),
            "limitations", STRING_ARRAY_SCHEMA)));

    private ProviderStructuredOutputSchema() {}

    public static String geminiSchema(AiOutputContract outputContract) {
        return outputContract == AiOutputContract.EXPLANATION ? GEMINI_EXPLANATION_SCHEMA : null;
    }

    public static String geminiSchema(AiOutputContract outputContract, PromptId promptId) {
        if (outputContract == AiOutputContract.EXPLANATION) {
            return GEMINI_EXPLANATION_SCHEMA;
        }
        if (outputContract == AiOutputContract.RESPONSE_EVALUATION
                && promptId == PromptId.RESPONSE_EVALUATION_DISCOVERY_V1) {
            return GEMINI_DISCOVERY_SCHEMA;
        }
        if (outputContract == AiOutputContract.RESPONSE_EVALUATION
                && promptId == PromptId.RESPONSE_EVALUATION_VERIFICATION_V1) {
            return GEMINI_VERIFICATION_SCHEMA;
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

    private static Map<String, Object> arraySchema(Map<String, Object> item) {
        return Map.of("type", "array", "items", item);
    }

    private static Map<String, Object> objectSchema(Map<String, Object> properties) {
        return Map.of("type", "object", "properties", properties,
                "required", List.copyOf(properties.keySet()));
    }

    private static String toJson(Map<String, Object> schema) {
        try {
            return OBJECT_MAPPER.writeValueAsString(schema);
        } catch (JsonProcessingException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
