package com.hippocampus.learning.application;

import com.hippocampus.learning.domain.*;
import com.hippocampus.learning.port.InitialStudyMissionSourceAuthority.ScopeFacts;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InitialStudyMissionStateAssemblerTests {
    static final Instant NOW=Instant.parse("2026-10-20T00:00:00Z");
    static final SourceCapability CAPABILITY=new SourceCapability(SourceReadiness.READY,true,false,false);

    @Test void delegatesShortBudgetSelectionToTheUnchangedEngineWithoutCreatingHistory() {
        var assembler=new InitialStudyMissionStateAssembler();
        for(int budget:List.of(1,2,8)) {
            var mission=mission(budget);
            var state=assembler.assemble(mission,CAPABILITY,NOW);
            var action=engine().decide(state);
            assertEquals(budget==1?LearningActionType.COMPLETE:budget==2?LearningActionType.REFLECT:LearningActionType.UNDERSTAND,action.actionType());
            assertEquals(List.of(),mission.activities());
            assertNull(mission.currentActivityId());assertNull(mission.completedAt());
            assertEquals(new LearningEvidenceSnapshot(Map.of()),state.evidence());
            assertTrue(state.recentActivityHistory().isEmpty());
        }
    }
    @Test void elapsedRecoveryKeepsOriginalBudgetAndDoesNotResetLearningTime() {
        var mission=mission(8);
        var state=new InitialStudyMissionStateAssembler().assemble(mission,CAPABILITY,NOW.plusSeconds(479));
        assertEquals(8,state.timeContext().availableMinutes());
        assertEquals(1,state.timeContext().remainingMinutes());
        assertEquals(7,state.timeContext().sessionAgeMinutes());
        assertEquals(NOW,mission.startedAt());
    }
    @Test void rawClockTicksCannotInvalidateAnUnchangedZeroTimeBlock() {
        var mission=mission(1);var assembler=new InitialStudyMissionStateAssembler();
        var scope=mission.materials().getFirst();
        var facts=List.of(new ScopeFacts(scope.materialId(),scope.materialVersionId(),null,"READY","READY",
                SourceReadiness.READY,true,false,false,Set.of(UUID.randomUUID()),Set.of()));
        assertEquals(assembler.blockingFingerprint(mission,facts,NOW.plusSeconds(120)),
                assembler.blockingFingerprint(mission,facts,NOW.plusSeconds(500)));
    }
    static StudyMission mission(int minutes) {
        UUID id=UUID.randomUUID(),owner=UUID.randomUUID(),topic=UUID.randomUUID();
        var objective=new LearningObjective(UUID.randomUUID(),"Explain the synthetic concept","topic:"+topic,
                "Synthetic concept",1,LearningObjectiveStatus.ACTIVE,NOW);
        return new StudyMission(id,owner,topic,null,StudyMissionStatus.ACTIVE,LearningStage.UNDERSTANDING,
                StudyMissionGroundingMode.STRICT_SOURCE,minutes,NOW,null,null,null,
                List.of(new MissionMaterial(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),null)),
                List.of(objective),List.of(),NOW,NOW);
    }
    static LearningEngine engine() {
        var durations=new EnumMap<LearningActionType,Integer>(LearningActionType.class);
        for(var action:LearningActionType.values()) durations.put(action,2);
        durations.put(LearningActionType.UNDERSTAND,8);
        return new LearningEngine(new LearningPolicyConfiguration(2,3,4,5,2,
                LearningPolicyConfiguration.V1_DUPLICATE_HISTORY_WINDOW,durations));
    }
}
