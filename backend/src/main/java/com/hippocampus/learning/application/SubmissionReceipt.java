package com.hippocampus.learning.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public record SubmissionReceipt(
        UUID missionId, UUID activityId, UUID attemptId, String outcome,
        List<String> correctConcepts, List<String> missingConcepts,
        List<String> misconceptions, String feedback, String limitationCode,
        String missionStatus, String stage, String updatedAt,
        boolean continuationAvailable) {
    private static final ObjectMapper JSON = new ObjectMapper();
    public SubmissionReceipt {
        Objects.requireNonNull(missionId);
        Objects.requireNonNull(activityId);
        Objects.requireNonNull(attemptId);
        Objects.requireNonNull(outcome);
        correctConcepts = List.copyOf(correctConcepts);
        missingConcepts = List.copyOf(missingConcepts);
        misconceptions = List.copyOf(misconceptions);
        Objects.requireNonNull(feedback);
        Objects.requireNonNull(missionStatus);
        Objects.requireNonNull(stage);
        Instant.parse(updatedAt);
    }

    public Instant updatedInstant() {
        return Instant.parse(updatedAt);
    }

    public String toJson() {
        try {
            return JSON.writeValueAsString(this);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("submission receipt could not be encoded", failure);
        }
    }

    public static SubmissionReceipt fromJson(String json) {
        try {
            return JSON.readValue(json, SubmissionReceipt.class);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("submission receipt could not be decoded", failure);
        }
    }
}
