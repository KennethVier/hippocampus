package com.hippocampus.materials.application;

import java.util.Objects;

import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.ProcessingJobType;

public final class VisualExtractStageHandler implements ProcessingStageHandler {
    private final ExtractPdfVisuals extraction;
    private final AssociateVisualContext association;

    public VisualExtractStageHandler(ExtractPdfVisuals extraction, AssociateVisualContext association) {
        this.extraction = Objects.requireNonNull(extraction);
        this.association = Objects.requireNonNull(association);
    }

    @Override
    public ProcessingJobType jobType() {
        return ProcessingJobType.VISUAL_EXTRACT;
    }

    @Override
    public void handle(ClaimedProcessingJob job) {
        extraction.execute(job);
        association.execute(job);
    }
}
