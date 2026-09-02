package com.hippocampus.materials.application;

import com.hippocampus.materials.domain.ProcessingJobType;

public final class MissingProcessingStageHandlerException extends IllegalStateException {

    public MissingProcessingStageHandlerException(ProcessingJobType jobType) {
        super("No processing stage handler is registered for: " + jobType);
    }
}
