package com.hippocampus.learning.port;

import java.util.List;

import com.hippocampus.learning.domain.LearningActivityType;

public interface GeneratedActivityContentDecoder {

    DecodedContent decode(
            LearningActivityType activityType,
            String artifactType,
            String taskType,
            String contentPayload);

    sealed interface DecodedContent permits Explanation, Retrieval, Connection, Application {}

    record Explanation(
            String concept,
            String explanation,
            List<String> keyPoints,
            List<String> limitations) implements DecodedContent {}

    record Retrieval(
            String subtype,
            String concept,
            String question,
            List<Option> options,
            String difficulty,
            List<String> limitations) implements DecodedContent {}

    record Option(String id, String text) {}

    record Connection(
            String fromConcept,
            String toConcept,
            String relationshipType,
            String relationship,
            String whyItMatters,
            String question,
            List<String> limitations) implements DecodedContent {}

    record Application(
            String scenario,
            String question,
            String targetConcept,
            String difficulty,
            List<String> limitations) implements DecodedContent {}
}
