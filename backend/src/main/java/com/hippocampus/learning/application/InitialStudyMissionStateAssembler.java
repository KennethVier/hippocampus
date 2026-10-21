package com.hippocampus.learning.application;

import com.hippocampus.learning.domain.*;
import com.hippocampus.learning.port.StudyMissionStartReceiptRepository;
import com.hippocampus.learning.port.InitialStudyMissionSourceAuthority.ScopeFacts;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Empty mission state only; never synthesizes a current activity or attempt. */
public final class InitialStudyMissionStateAssembler {
    public LearningState assemble(StudyMission mission,SourceCapability capability,Instant now) {
        if (!mission.activities().isEmpty() || mission.currentActivityId()!=null
                || mission.objectives().size()!=1) throw new IllegalStateException("Mission is not initial");
        LearningObjective objective=mission.objectives().getFirst();
        int age=mission.startedAt()==null ? 0 : (int)Math.min(Integer.MAX_VALUE,
                Math.max(0,Duration.between(mission.startedAt(),now).toMinutes()));
        int budget=java.util.Objects.requireNonNull(mission.availableTimeMinutes(),"Initial mission budget is required");
        return new LearningState(mission.id(),objective.id(),objective.conceptKey(),
                MissionLifecycleState.valueOf(mission.status().name()),mission.learningState(),
                new LearningEvidenceSnapshot(Map.of()),capability,
                new LearningTimeContext(budget,Math.max(0,budget-age),age),
                List.of(),false,constraintsFor(mission.groundingMode()));
    }

    public static LearningActionConstraints constraintsFor(StudyMissionGroundingMode grounding) {
        return switch(grounding) {
            case STRICT_SOURCE -> new LearningActionConstraints(SourceRequirement.REQUIRED,false,false,
                    null,null,LearningActivityIntent.STANDARD);
            case SOURCE_FIRST -> new LearningActionConstraints(SourceRequirement.REQUIRED,false,true,
                    null,null,LearningActivityIntent.STANDARD);
            case GENERAL_KNOWLEDGE -> LearningActionConstraints.unconstrained();
        };
    }

    public static String frozenFingerprint(StudyMission mission) {
        var fields=new ArrayList<String>();
        fields.add(mission.userId().toString()); fields.add(mission.id().toString());
        fields.add(mission.objectives().getFirst().id().toString());
        fields.add(mission.groundingMode().name()); fields.add(Integer.toString(mission.materials().size()));
        mission.materials().stream().sorted(Comparator.comparing((MissionMaterial s)->s.materialId().toString())
                .thenComparing(s->s.materialVersionId().toString()).thenComparing(s->text(s.documentNodeId())))
                .forEach(s->{fields.add(s.materialId().toString());fields.add(s.materialVersionId().toString());
                    fields.add(text(s.documentNodeId()));});
        return StudyMissionStartReceiptRepository.fingerprint("HIPPOCAMPUS/P714/FROZEN/1",fields);
    }

    public String blockingFingerprint(StudyMission mission,List<ScopeFacts> facts,Instant now) {
        var state=assemble(mission,com.hippocampus.learning.port.InitialStudyMissionSourceAuthority.capability(facts),now);
        var fields=new ArrayList<>(List.of(mission.userId().toString(),mission.id().toString(),
                mission.objectives().getFirst().id().toString(),frozenFingerprint(mission),mission.status().name(),
                mission.learningState()==null ? "NONE" : mission.learningState().name(),mission.updatedAt().toString(),
                text(mission.currentActivityId()),Integer.toString(mission.activities().size()),"0",
                String.valueOf(state.timeContext().remainingMinutes()),Integer.toString(facts.size())));
        facts.stream().sorted(Comparator.comparing((ScopeFacts s)->s.materialId().toString())
                .thenComparing(s->s.frozenVersionId().toString()).thenComparing(s->text(s.documentNodeId())))
                .forEach(s->fields.addAll(List.of(s.materialId().toString(),s.frozenVersionId().toString(),
                        text(s.documentNodeId()),s.materialStatus(),s.versionStatus(),s.readiness().name(),
                        Boolean.toString(s.groundedTextAvailable()),Boolean.toString(s.visualAvailable()),
                        Boolean.toString(s.visualReliable()))));
        return StudyMissionStartReceiptRepository.fingerprint("HIPPOCAMPUS/P714/INITIAL-BLOCK/1",fields);
    }
    private static String text(Object value) { return value==null ? "EMPTY" : value.toString(); }
}
