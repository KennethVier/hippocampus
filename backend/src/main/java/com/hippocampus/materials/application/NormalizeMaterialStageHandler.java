package com.hippocampus.materials.application;
import java.util.Objects;
import com.hippocampus.materials.domain.ClaimedProcessingJob; import com.hippocampus.materials.domain.ProcessingJobType;
public final class NormalizeMaterialStageHandler implements ProcessingStageHandler {
    private final NormalizeMaterialText normalization;
    private final ReportProcessingJobProgress progress;
    public NormalizeMaterialStageHandler(NormalizeMaterialText normalization) { this(normalization, null); }
    public NormalizeMaterialStageHandler(NormalizeMaterialText normalization, ReportProcessingJobProgress progress) {
        this.normalization = Objects.requireNonNull(normalization); this.progress = progress;
    }
    @Override public ProcessingJobType jobType() { return ProcessingJobType.NORMALIZE; }
    @Override public void handle(ClaimedProcessingJob job) {
        if (job.materialVersionId() == null) throw new IllegalArgumentException("NORMALIZE requires a material version");
        if (progress == null) normalization.execute(job.materialVersionId());
        else normalization.execute(job.materialVersionId(), () -> progress.verifyOwnership(job),
                (current, total) -> progress.report(job, current, total));
    }
}
