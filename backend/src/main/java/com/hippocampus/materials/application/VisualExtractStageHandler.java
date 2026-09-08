package com.hippocampus.materials.application;

import java.util.Objects;

import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.ProcessingJobType;

public final class VisualExtractStageHandler implements ProcessingStageHandler {
    private final ExtractPdfVisuals extraction;
    private final AssociateVisualContext association;
    private final ExtractPdfTables tables;

    public VisualExtractStageHandler(
            ExtractPdfVisuals extraction, AssociateVisualContext association, ExtractPdfTables tables) {
        this.extraction = Objects.requireNonNull(extraction);
        this.association = Objects.requireNonNull(association);
        this.tables = Objects.requireNonNull(tables);
    }

    @Override
    public ProcessingJobType jobType() {
        return ProcessingJobType.VISUAL_EXTRACT;
    }

    @Override
    public void handle(ClaimedProcessingJob job) {
        extraction.execute(job);
        association.execute(job);
        tables.execute(job);
    }
}
