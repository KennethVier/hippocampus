package com.hippocampus.learning.application;

import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.hippocampus.learning.port.*;
import com.hippocampus.learning.domain.*;
import com.hippocampus.progress.port.StudentAttemptRepository;

/** Explicit port fixture; production uses the JSON contract adapter and database locks. */
final class Sec004TestSupport {
    static StudyMissionLearningStateAssembler assembler(ActivityResponseContractRepository contracts) {
        var sources=mock(ActivitySourceReferenceAuthorization.class);
        when(sources.availableForRead(any(),anyList(),anySet())).thenReturn(true);
        when(sources.lockAndValidateForCompletion(any(),anyList(),anySet(),anyBoolean(),anyList(),anySet())).thenReturn(true);
        var artifacts=mock(GeneratedArtifactRepository.class);
        when(artifacts.findOwnedByIdForUpdate(any(),any())).thenReturn(Optional.of(mock(GeneratedArtifactRepository.GeneratedArtifact.class)));
        return new StudyMissionLearningStateAssembler(new AttemptEvidenceEligibility(contracts,sources,artifacts));
    }
    static PersistActivityResponse persistence(StudyMissionRepository missions, StudentAttemptRepository attempts,
            ActivityResponseContractRepository contracts, LearningEngine engine, FollowUpIntentLifecycle followUps) {
        return new PersistActivityResponse(missions,attempts,followUps,assembler(contracts),engine);
    }
}
