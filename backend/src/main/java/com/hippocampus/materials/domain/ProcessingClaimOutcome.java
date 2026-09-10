package com.hippocampus.materials.domain;

import java.util.Optional;
import java.util.UUID;

/** Both durable transitions produced by one atomic claim/recovery operation. */
public record ProcessingClaimOutcome(Optional<ClaimedProcessingJob> claimed, Optional<UUID> exhaustedJobId) {
    public ProcessingClaimOutcome {
        java.util.Objects.requireNonNull(claimed);
        java.util.Objects.requireNonNull(exhaustedJobId);
    }
}
