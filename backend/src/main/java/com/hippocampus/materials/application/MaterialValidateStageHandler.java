package com.hippocampus.materials.application;

import java.util.Objects;
import java.util.UUID;

import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.ProcessingJobType;
import com.hippocampus.materials.port.MaterialSourceValidator;
import org.springframework.stereotype.Component;

@Component
public final class MaterialValidateStageHandler implements ProcessingStageHandler {

    private final MaterialSourceValidator sourceValidator;

    public MaterialValidateStageHandler(MaterialSourceValidator sourceValidator) {
        this.sourceValidator = Objects.requireNonNull(sourceValidator);
    }

    @Override
    public ProcessingJobType jobType() {
        return ProcessingJobType.MATERIAL_VALIDATE;
    }

    @Override
    public void handle(ClaimedProcessingJob job) {
        Objects.requireNonNull(job, "Claimed processing job must not be null");
        UUID versionId = job.materialVersionId();
        if (versionId == null) {
            throw new IllegalArgumentException("Material version is required for validation");
        }

        sourceValidator.validate(versionId);
    }
}
