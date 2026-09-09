package com.hippocampus.materials.application;

import org.springframework.transaction.annotation.Transactional;
import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.ProcessingFailure;
import com.hippocampus.materials.port.ProcessingJobExecutionRepository;

public class FinalizeProcessingFailure {
    private final ProcessingJobExecutionRepository jobs;
    private final ProcessingRetryPolicy retries;
    public FinalizeProcessingFailure(ProcessingJobExecutionRepository jobs, ProcessingRetryPolicy retries) {
        this.jobs = jobs; this.retries = retries;
    }
    @Transactional
    public void execute(ClaimedProcessingJob job, ProcessingFailure failure) {
        if (failure.kind() == ProcessingFailure.Kind.PARTIAL) {
            throw new IllegalArgumentException("Partial limitations are durable artifact outcomes, not job failures");
        }
        boolean updated = failure.kind() == ProcessingFailure.Kind.TRANSIENT && job.attemptNumber() < job.maxAttempts()
                ? jobs.retry(job, failure.errorCode(), retries.delayAfter(job.attemptNumber()))
                : jobs.fail(job, failure.errorCode());
        if (!updated) throw new ProcessingJobOwnershipLostException();
    }
}
