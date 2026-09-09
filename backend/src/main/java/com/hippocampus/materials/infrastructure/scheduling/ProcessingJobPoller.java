package com.hippocampus.materials.infrastructure.scheduling;

import org.springframework.scheduling.annotation.Scheduled;
import com.hippocampus.materials.application.RunNextProcessingJob;
import com.hippocampus.materials.application.ProcessingRunResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProcessingJobPoller {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessingJobPoller.class);
    private final RunNextProcessingJob runner;
    private final String workerId;
    public ProcessingJobPoller(RunNextProcessingJob runner, String workerId) { this.runner = runner; this.workerId = workerId; }
    @Scheduled(fixedDelayString = "${hippocampus.materials.processing.recovery.poll-interval:PT1S}")
    public void poll() {
        ProcessingRunResult result = runner.execute(workerId);
        if (result instanceof ProcessingRunResult.Failed failed) {
            LOGGER.warn("event=processing_job_execution_failed jobId={} stage={} errorCode={} worker={}",
                    failed.jobId(), failed.stage(), failed.errorCode(), workerId);
        }
    }
}
