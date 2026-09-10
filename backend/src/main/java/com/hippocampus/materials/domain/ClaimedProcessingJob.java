package com.hippocampus.materials.domain;

import java.util.UUID;

public record ClaimedProcessingJob(
        UUID jobId,
        ProcessingJobType jobType,
        UUID materialVersionId,
        String processingVersion,
        String workerId,
        int attemptNumber,
        int maxAttempts) {
    public ClaimedProcessingJob(UUID jobId, ProcessingJobType jobType, UUID materialVersionId, String processingVersion) {
        this(jobId, jobType, materialVersionId, processingVersion, "test-worker", 1, 3);
    }
    public ClaimedProcessingJob {
        if (workerId == null || workerId.isBlank() || attemptNumber < 1 || maxAttempts < attemptNumber) {
            throw new IllegalArgumentException("A claimed job requires a valid ownership fence");
        }
    }
}
