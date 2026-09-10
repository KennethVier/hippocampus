package com.hippocampus.materials.port;

import com.hippocampus.materials.domain.ProcessingClaimOutcome;

public interface ProcessingJobClaimRepository {

    ProcessingClaimOutcome claimNextEligible(String workerId, long staleTimeoutSeconds);
}
