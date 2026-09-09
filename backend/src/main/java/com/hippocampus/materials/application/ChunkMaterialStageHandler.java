package com.hippocampus.materials.application;

import java.util.Objects;

import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.ProcessingJobType;

public final class ChunkMaterialStageHandler implements ProcessingStageHandler {
    private final ChunkMaterialText chunking;
    private final ReportProcessingJobProgress progress;

    public ChunkMaterialStageHandler(ChunkMaterialText chunking) {
        this(chunking, null);
    }
    public ChunkMaterialStageHandler(ChunkMaterialText chunking, ReportProcessingJobProgress progress) {
        this.chunking = Objects.requireNonNull(chunking);
        this.progress = progress;
    }

    @Override
    public ProcessingJobType jobType() {
        return ProcessingJobType.CHUNK;
    }

    @Override
    public void handle(ClaimedProcessingJob job) {
        if (job.materialVersionId() == null) {
            throw new IllegalArgumentException("CHUNK requires a material version");
        }
        if (progress == null) chunking.execute(job.materialVersionId());
        else chunking.execute(job.materialVersionId(), (current, total) -> progress.report(job, current, total));
    }
}
