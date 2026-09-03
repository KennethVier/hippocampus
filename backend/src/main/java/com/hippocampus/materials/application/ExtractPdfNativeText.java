package com.hippocampus.materials.application;

import java.util.Objects;

import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.PdfDocumentMetadata;
import com.hippocampus.materials.domain.ProcessingJobType;
import com.hippocampus.materials.port.PdfExtractionSourceRepository;
import com.hippocampus.materials.port.PdfExtractionSource;
import com.hippocampus.materials.port.PdfNativeTextExtractor;
import com.hippocampus.materials.port.PdfPageBatchSink;

public final class ExtractPdfNativeText {
    private final PdfExtractionSourceRepository sources;
    private final PdfNativeTextExtractor extractor;

    public ExtractPdfNativeText(PdfExtractionSourceRepository sources, PdfNativeTextExtractor extractor) {
        this.sources = Objects.requireNonNull(sources);
        this.extractor = Objects.requireNonNull(extractor);
    }

    public PdfDocumentMetadata execute(ClaimedProcessingJob job, PdfPageBatchSink sink) {
        Objects.requireNonNull(job, "job must not be null");
        Objects.requireNonNull(sink, "sink must not be null");
        if (job.jobType() != ProcessingJobType.MATERIAL_EXTRACT || job.materialVersionId() == null) {
            throw new IllegalArgumentException("A MATERIAL_EXTRACT job with a material version is required");
        }
        PdfExtractionSource source = sources.requireExtractablePdf(job.materialVersionId());
        return extractor.extract(source, sink);
    }
}
