package com.hippocampus.materials.port;

import java.time.Instant;

import com.hippocampus.materials.domain.ClaimedProcessingJob;

public interface ProcessingJobExecutionRepository {
    boolean heartbeat(ClaimedProcessingJob job, Instant now);
    boolean progress(ClaimedProcessingJob job, long current, Long total, Instant now);
    boolean retry(ClaimedProcessingJob job, String errorCode, Instant nextAttemptAt, Instant now);
    boolean fail(ClaimedProcessingJob job, String errorCode, Instant now);
}
