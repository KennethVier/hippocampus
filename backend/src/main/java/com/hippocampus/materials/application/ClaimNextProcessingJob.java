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

    public ClaimNextProcessingJob(ProcessingJobClaimRepository jobs) {
        this(jobs, Duration.ofMinutes(1));
    }

    public ClaimNextProcessingJob(ProcessingJobClaimRepository jobs, Duration staleTimeout) {
        this.jobs = jobs;
        this.staleTimeout = staleTimeout;
    }

    @Transactional
    public Optional<ClaimedProcessingJob> execute(String workerId) {
        validateWorkerId(workerId);
        return jobs.claimNextEligible(workerId, staleTimeout.toSeconds());
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
