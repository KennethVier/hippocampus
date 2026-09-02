package com.hippocampus.materials.application;

import com.hippocampus.materials.domain.ProcessingJobType;

public record ProcessingStageResult(
        ProcessingJobType executedStage,
        ProcessingJobType nextStage) {}
