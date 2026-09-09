package com.hippocampus.materials.application;
import java.util.Objects;
import com.hippocampus.materials.domain.ClaimedProcessingJob; import com.hippocampus.materials.domain.ProcessingJobType;
public final class NormalizeMaterialStageHandler implements ProcessingStageHandler {
    private final NormalizeMaterialText normalization;
    public NormalizeMaterialStageHandler(NormalizeMaterialText normalization) { this.normalization = Objects.requireNonNull(normalization); }
    @Override public ProcessingJobType jobType() { return ProcessingJobType.NORMALIZE; }
    @Override public void handle(ClaimedProcessingJob job) {
        if (job.materialVersionId() == null) throw new IllegalArgumentException("NORMALIZE requires a material version");
        normalization.execute(job.materialVersionId());
    }
}
