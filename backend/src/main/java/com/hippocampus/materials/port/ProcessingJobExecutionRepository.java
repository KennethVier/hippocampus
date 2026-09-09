package com.hippocampus.materials.port;

import java.time.Duration;

import com.hippocampus.materials.domain.ClaimedProcessingJob;

public interface ProcessingJobExecutionRepository {
    boolean heartbeat(ClaimedProcessingJob job);
    boolean progress(ClaimedProcessingJob job, long current, Long total);
    boolean retry(ClaimedProcessingJob job, String errorCode, Duration delay);
    boolean fail(ClaimedProcessingJob job, String errorCode);
}
