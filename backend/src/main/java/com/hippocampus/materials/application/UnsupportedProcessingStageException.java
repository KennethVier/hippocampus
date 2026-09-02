package com.hippocampus.materials.application;

import com.hippocampus.materials.domain.ProcessingJobType;

public final class UnsupportedProcessingStageException extends IllegalArgumentException {

    public UnsupportedProcessingStageException(ProcessingJobType jobType) {
        super("Processing stage is not supported by the Phase 3 dispatcher: " + jobType);
    }
}
