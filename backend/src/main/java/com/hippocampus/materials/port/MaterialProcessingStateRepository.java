package com.hippocampus.materials.port;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface MaterialProcessingStateRepository {
    Optional<DurableProcessingState> findCurrentPhaseThreeState(UUID materialVersionId);

    boolean isStructureDetectionComplete(UUID materialVersionId);

    record DurableProcessingState(
            String stage,
            BigDecimal progress,
            Long progressCurrent,
            Long progressTotal) {}
}
