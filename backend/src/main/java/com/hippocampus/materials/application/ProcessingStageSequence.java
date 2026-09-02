package com.hippocampus.materials.application;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import com.hippocampus.materials.domain.ProcessingJobType;

final class ProcessingStageSequence {
    private static final Map<ProcessingJobType, ProcessingJobType> NEXT_STAGES = nextStages();
    private static final Set<ProcessingJobType> SUPPORTED_STAGES = Set.copyOf(NEXT_STAGES.keySet());

    private ProcessingStageSequence() {}

    static boolean supports(ProcessingJobType jobType) {
        return jobType != null && SUPPORTED_STAGES.contains(jobType);
    }

    static ProcessingJobType nextStage(ProcessingJobType jobType) {
        ProcessingJobType nextStage = NEXT_STAGES.get(jobType);
        if (nextStage == null) {
            throw new UnsupportedProcessingStageException(jobType);
        }
        return nextStage;
    }

    static ProcessingJobType nextDurablePhaseThreeStage(ProcessingJobType jobType) {
        ProcessingJobType nextStage = nextStage(jobType);
        return nextStage == ProcessingJobType.EMBED ? null : nextStage;
    }

    private static Map<ProcessingJobType, ProcessingJobType> nextStages() {
        EnumMap<ProcessingJobType, ProcessingJobType> stages = new EnumMap<>(ProcessingJobType.class);
        stages.put(ProcessingJobType.MATERIAL_VALIDATE, ProcessingJobType.MATERIAL_EXTRACT);
        stages.put(ProcessingJobType.MATERIAL_EXTRACT, ProcessingJobType.STRUCTURE_DETECT);
        stages.put(ProcessingJobType.STRUCTURE_DETECT, ProcessingJobType.VISUAL_EXTRACT);
        stages.put(ProcessingJobType.VISUAL_EXTRACT, ProcessingJobType.NORMALIZE);
        stages.put(ProcessingJobType.NORMALIZE, ProcessingJobType.CHUNK);
        stages.put(ProcessingJobType.CHUNK, ProcessingJobType.EMBED);
        return Map.copyOf(stages);
    }
}
