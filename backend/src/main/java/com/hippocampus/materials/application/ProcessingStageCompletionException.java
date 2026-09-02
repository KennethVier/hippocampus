package com.hippocampus.materials.application;

import java.util.UUID;

import com.hippocampus.materials.domain.ProcessingJobType;

public final class ProcessingStageCompletionException extends IllegalStateException {

    public ProcessingStageCompletionException(UUID jobId, ProcessingJobType jobType) {
        super("Processing job could not be completed from RUNNING state: " + jobId + " (" + jobType + ")");
    }
}
