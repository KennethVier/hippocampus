package com.hippocampus.materials.application;

import java.util.Objects;

import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.ProcessingJobType;

public final class StructureDetectStageHandler implements ProcessingStageHandler {
    private final DetectDocumentStructure detection;

    public StructureDetectStageHandler(DetectDocumentStructure detection) {
        this.detection = Objects.requireNonNull(detection);
    }

    @Override
    public ProcessingJobType jobType() {
        return ProcessingJobType.STRUCTURE_DETECT;
    }

    @Override
    public void handle(ClaimedProcessingJob job) {
        detection.execute(job);
    }
}
