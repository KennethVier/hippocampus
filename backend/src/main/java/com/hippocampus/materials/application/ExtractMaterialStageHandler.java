package com.hippocampus.materials.application;

import java.util.Objects;

import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.PdfDocumentMetadata;
import com.hippocampus.materials.domain.ProcessingJobType;

public final class ExtractMaterialStageHandler implements ProcessingStageHandler {
    private final ExtractPdfNativeText extraction;
    private final PersistPdfPageBatch batches;
    private final FinalizePdfExtraction finalization;

    public ExtractMaterialStageHandler(
            ExtractPdfNativeText extraction,
            PersistPdfPageBatch batches,
            FinalizePdfExtraction finalization) {
        this.extraction = Objects.requireNonNull(extraction);
        this.batches = Objects.requireNonNull(batches);
        this.finalization = Objects.requireNonNull(finalization);
    }

    @Override
    public ProcessingJobType jobType() {
        return ProcessingJobType.MATERIAL_EXTRACT;
    }

    @Override
    public void handle(ClaimedProcessingJob job) {
        PdfDocumentMetadata metadata = extraction.execute(
                job, batch -> batches.execute(job.materialVersionId(), batch));
        finalization.execute(job.materialVersionId(), metadata.pageCount());
    }
}
