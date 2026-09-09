package com.hippocampus.materials.infrastructure.scheduling;

import org.springframework.scheduling.annotation.Scheduled;
import com.hippocampus.materials.application.RunNextProcessingJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProcessingJobPoller {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessingJobPoller.class);
    private final RunNextProcessingJob runner;
    private final String workerId;
    public ProcessingJobPoller(RunNextProcessingJob runner, String workerId) { this.runner = runner; this.workerId = workerId; }
    @Scheduled(fixedDelayString = "${hippocampus.materials.processing.recovery.poll-interval:PT1S}")
    public void poll() {
        try { runner.execute(workerId); }
        catch (RuntimeException ignored) {
            LOGGER.warn("event=processing_job_execution_failed worker={}", workerId);
        }
    }
}
