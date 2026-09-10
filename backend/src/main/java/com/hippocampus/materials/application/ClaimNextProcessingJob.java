package com.hippocampus.materials.application;

import java.util.Optional;
import java.time.Duration;
import java.util.regex.Pattern;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.port.ProcessingJobClaimRepository;

public class ClaimNextProcessingJob {
    private static final int MAXIMUM_WORKER_ID_LENGTH = 128;
    private static final Pattern WORKER_ID = Pattern.compile("[A-Za-z0-9._:-]+");

    private final ProcessingJobClaimRepository jobs;
    private final Duration staleTimeout;

    private final DeriveMaterialReadiness readiness;
    public ClaimNextProcessingJob(ProcessingJobClaimRepository jobs, Duration staleTimeout,
            DeriveMaterialReadiness readiness) {
        this.jobs = jobs;
        this.staleTimeout = staleTimeout;
        this.readiness = readiness;
    }

    @Transactional
    public Optional<ClaimedProcessingJob> execute(String workerId) {
        validateWorkerId(workerId);
        var outcome = jobs.claimNextEligible(workerId, staleTimeout.toSeconds());
        outcome.exhaustedJobId().ifPresent(readiness::execute);
        outcome.claimed().ifPresent(job -> readiness.execute(job.jobId()));
        return outcome.claimed();
    }

    private static void validateWorkerId(String workerId) {
        if (workerId == null
                || workerId.isBlank()
                || workerId.length() > MAXIMUM_WORKER_ID_LENGTH
                || !WORKER_ID.matcher(workerId).matches()) {
            throw new IllegalArgumentException("Worker ID must be a valid internal operational identifier");
        }
    }
}
