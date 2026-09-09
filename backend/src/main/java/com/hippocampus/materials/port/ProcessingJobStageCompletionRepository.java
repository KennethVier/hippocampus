package com.hippocampus.materials.port;

import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.ProcessingJobType;

public interface ProcessingJobStageCompletionRepository {

    boolean completeSuccessfulStage(
            ClaimedProcessingJob job,
            ProcessingJobType nextDurableStage);
}
