package com.hippocampus.rag.application;

import java.util.Objects;

import com.hippocampus.materials.application.ProcessingStageHandler;
import com.hippocampus.materials.application.ReportProcessingJobProgress;
import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.ProcessingJobType;

public final class EmbeddingMaterialStageHandler implements ProcessingStageHandler {
    private final EmbedMaterialVersion embedding;
    private final ReportProcessingJobProgress progress;

    public EmbeddingMaterialStageHandler(
            EmbedMaterialVersion embedding, ReportProcessingJobProgress progress) {
        this.embedding = Objects.requireNonNull(embedding);
        this.progress = Objects.requireNonNull(progress);
    }

    @Override
    public ProcessingJobType jobType() {
        return ProcessingJobType.EMBED;
    }

    @Override
    public void handle(ClaimedProcessingJob job) {
        if (job.materialVersionId() == null) {
            throw new IllegalArgumentException("EMBED requires a material version");
        }
        embedding.execute(
                job.materialVersionId(),
                () -> progress.verifyOwnership(job),
                (current, total) -> progress.report(job, current, total));
    }
}
