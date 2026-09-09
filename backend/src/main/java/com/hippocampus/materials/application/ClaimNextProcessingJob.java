package com.hippocampus.materials.application;

import java.util.Optional;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.port.ProcessingJobClaimRepository;

public class ClaimNextProcessingJob {
    private static final int MAXIMUM_WORKER_ID_LENGTH = 128;
    private static final Pattern WORKER_ID = Pattern.compile("[A-Za-z0-9._:-]+");

    private final ProcessingJobClaimRepository jobs;
    private final Clock clock;
    private final Duration staleTimeout;

    public ClaimNextProcessingJob(ProcessingJobClaimRepository jobs) {
        this(jobs, Clock.systemUTC(), Duration.ofMinutes(1));
    }

    public ClaimNextProcessingJob(ProcessingJobClaimRepository jobs, Clock clock, Duration staleTimeout) {
        this.jobs = jobs;
        this.clock = clock;
        this.staleTimeout = staleTimeout;
    }

    @Transactional
    public Optional<ClaimedProcessingJob> execute(String workerId) {
        validateWorkerId(workerId);
        Instant now = clock.instant();
        return jobs.claimNextEligible(workerId, now, now.minus(staleTimeout));
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
