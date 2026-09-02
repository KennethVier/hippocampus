package com.hippocampus.materials.application;

import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.ProcessingJobType;

public interface ProcessingStageHandler {

    ProcessingJobType jobType();

    void handle(ClaimedProcessingJob job);
}
