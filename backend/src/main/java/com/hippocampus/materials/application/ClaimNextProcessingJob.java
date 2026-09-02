package com.hippocampus.materials.application;

import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.port.ProcessingJobClaimRepository;

public class ClaimNextProcessingJob {
    private static final int MAXIMUM_WORKER_ID_LENGTH = 128;
    private static final Pattern WORKER_ID = Pattern.compile("[A-Za-z0-9._:-]+");

    private final ProcessingJobClaimRepository jobs;

    public ClaimNextProcessingJob(ProcessingJobClaimRepository jobs) {
        this.jobs = jobs;
    }

    @Transactional
    public Optional<ClaimedProcessingJob> execute(String workerId) {
        validateWorkerId(workerId);
        return jobs.claimNextEligible(workerId);
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
