package com.hippocampus.materials.application;

import java.util.Objects;

import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.ProcessingJobType;

public final class VisualExtractStageHandler implements ProcessingStageHandler {
    private final ExtractPdfVisuals extraction;

    public VisualExtractStageHandler(ExtractPdfVisuals extraction) {
        this.extraction = Objects.requireNonNull(extraction);
    }

    @Override
    public ProcessingJobType jobType() {
        return ProcessingJobType.VISUAL_EXTRACT;
    }

    @Override
    public void handle(ClaimedProcessingJob job) {
        extraction.execute(job);
    }
}
