package com.hippocampus.materials.port;

import java.util.UUID;

import com.hippocampus.materials.domain.ProcessingJobType;

public interface ProcessingJobStageCompletionRepository {

    boolean completeSuccessfulStage(
            UUID jobId,
            ProcessingJobType executedStage,
            ProcessingJobType nextDurableStage);
}
