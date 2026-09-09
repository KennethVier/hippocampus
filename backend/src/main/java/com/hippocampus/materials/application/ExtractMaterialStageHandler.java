package com.hippocampus.materials.application;

import java.util.Objects;

import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.PdfDocumentMetadata;
import com.hippocampus.materials.domain.ProcessingJobType;

public final class ExtractMaterialStageHandler implements ProcessingStageHandler {
    private final ExtractPdfPages extraction;
    private final PersistPdfPageBatch batches;
    private final FinalizePdfExtraction finalization;
    private final ReportProcessingJobProgress progress;

    public ExtractMaterialStageHandler(
            ExtractPdfPages extraction,
            PersistPdfPageBatch batches,
            FinalizePdfExtraction finalization) {
        this(extraction, batches, finalization, null);
    }
    public ExtractMaterialStageHandler(ExtractPdfPages extraction, PersistPdfPageBatch batches,
            FinalizePdfExtraction finalization, ReportProcessingJobProgress progress) {
        this.extraction = Objects.requireNonNull(extraction);
        this.batches = Objects.requireNonNull(batches);
        this.finalization = Objects.requireNonNull(finalization);
        this.progress = progress;
    }

    @Override
    public ProcessingJobType jobType() {
        return ProcessingJobType.MATERIAL_EXTRACT;
    }

    @Override
    public void handle(ClaimedProcessingJob job) {
        PdfDocumentMetadata metadata = extraction.execute(
                job, batch -> {
                    if (progress != null) progress.verifyOwnership(job);
                    batches.execute(job.materialVersionId(), batch);
                    if (progress != null) progress.report(job, batch.lastPage(), null);
                });
        if (progress != null) progress.verifyOwnership(job);
        finalization.execute(job.materialVersionId(), metadata.pageCount());
        if (progress != null) progress.report(job, metadata.pageCount(), (long) metadata.pageCount());
    }
}
