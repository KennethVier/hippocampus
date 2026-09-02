package com.hippocampus.materials.domain;

import java.util.UUID;

public record ClaimedProcessingJob(
        UUID jobId,
        ProcessingJobType jobType,
        UUID materialVersionId,
        String processingVersion) {}
