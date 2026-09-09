package com.hippocampus.materials.application;

import java.time.Clock;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;
import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.ProcessingFailure;
import com.hippocampus.materials.port.ProcessingJobExecutionRepository;

public class FinalizeProcessingFailure {
    private final ProcessingJobExecutionRepository jobs;
    private final ProcessingRetryPolicy retries;
    private final Clock clock;
    public FinalizeProcessingFailure(ProcessingJobExecutionRepository jobs, ProcessingRetryPolicy retries, Clock clock) {
        this.jobs = jobs; this.retries = retries; this.clock = clock;
    }
    @Transactional
    public void execute(ClaimedProcessingJob job, ProcessingFailure failure) {
        Instant now = clock.instant();
        boolean updated = failure.kind() == ProcessingFailure.Kind.TRANSIENT && job.attemptNumber() < job.maxAttempts()
                ? jobs.retry(job, failure.errorCode(), retries.nextAttemptAt(job.attemptNumber(), now), now)
                : jobs.fail(job, failure.errorCode(), now);
        if (!updated) throw new ProcessingJobOwnershipLostException();
    }
}
