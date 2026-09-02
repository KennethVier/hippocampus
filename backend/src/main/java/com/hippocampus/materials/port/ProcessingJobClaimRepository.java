package com.hippocampus.materials.port;

import java.util.Optional;

import com.hippocampus.materials.domain.ClaimedProcessingJob;

public interface ProcessingJobClaimRepository {

    Optional<ClaimedProcessingJob> claimNextEligible(String workerId);
}
