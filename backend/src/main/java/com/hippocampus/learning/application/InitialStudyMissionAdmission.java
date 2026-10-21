package com.hippocampus.learning.application;

import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningEngine;
import com.hippocampus.learning.domain.NextLearningAction;
import com.hippocampus.learning.domain.StudyMission;
import com.hippocampus.learning.domain.StudyMissionStatus;
import com.hippocampus.learning.port.InitialStudyMissionSourceAuthority;
import com.hippocampus.learning.port.StudyMissionStartReceiptRepository;
import com.hippocampus.learning.port.StudyMissionStartReceiptRepository.InitialContext;
import com.hippocampus.learning.port.StudyMissionStartReceiptRepository.Outcome;
import com.hippocampus.learning.port.StudyMissionStartReceiptRepository.Reason;
import com.hippocampus.learning.port.StudyMissionStartReceiptRepository.Receipt;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Called only inside existing final persistence, after the fresh owned mission lock. */
public final class InitialStudyMissionAdmission {
    private final StudyMissionStartReceiptRepository receipts;
    private final InitialStudyMissionSourceAuthority sources;
    private final InitialStudyMissionStateAssembler assembler;
    private final LearningEngine engine;

    public InitialStudyMissionAdmission(StudyMissionStartReceiptRepository receipts,
            InitialStudyMissionSourceAuthority sources,InitialStudyMissionStateAssembler assembler,LearningEngine engine) {
        this.receipts=Objects.requireNonNull(receipts); this.sources=Objects.requireNonNull(sources);
        this.assembler=Objects.requireNonNull(assembler); this.engine=Objects.requireNonNull(engine);
    }

    public void configureTransactionLimits() { receipts.configureTransactionLimits(); }

    public Receipt admit(StudyMission mission,InitialContext context,NextLearningAction proposed,
                          Set<UUID> references,boolean sourceRequired) {
        if(context==null || !mission.activities().isEmpty() || mission.currentActivityId()!=null
                || mission.status()!=StudyMissionStatus.ACTIVE || !mission.userId().equals(context.ownerId())
                || !mission.id().equals(context.missionId()) || !Objects.equals(proposed,context.selectedAction())
                || !mission.updatedAt().equals(context.snapshot().updatedAt())
                || mission.objectives().size()!=1
                || !mission.objectives().getFirst().id().equals(context.snapshot().objectiveId())
                || !InitialStudyMissionStateAssembler.frozenFingerprint(mission).equals(context.snapshot().frozenBindingFingerprint()))
            throw stale();
        Receipt receipt=receipts.lockByMission(mission.userId(),mission.id()).orElseThrow(InitialStudyMissionAdmission::stale);
        if(!receipt.matches(context,receipts.databaseTime())) throw stale();
        java.util.List<com.hippocampus.learning.port.InitialStudyMissionSourceAuthority.ScopeFacts> facts;
        try { facts=sources.lockAndInspect(mission.userId(),mission.materials(),references,sourceRequired); }
        catch(com.hippocampus.learning.port.InitialStudyMissionSourceAuthority.SourceUnavailableException unavailable) {
            throw new StudyMissionStartException(StudyMissionStartException.Kind.UNAVAILABLE,Reason.SOURCE_UNAVAILABLE);
        }
        Instant now=receipts.databaseTime();
        if(!receipt.matches(context,now)) throw stale();
        NextLearningAction actual=engine.decide(assembler.assemble(mission,InitialStudyMissionSourceAuthority.capability(facts),now));
        if(actual.actionType()==LearningActionType.COMPLETE) {
            Receipt blocked=receipt.close(now,Outcome.ADMISSION_DENIED,Reason.INITIAL_COMPLETION_NOT_ADMISSIBLE,null)
                    .block(actual,assembler.blockingFingerprint(mission,facts,now),Reason.INITIAL_COMPLETION_NOT_ADMISSIBLE,now);
            receipts.replace(receipt,blocked);
            return null;
        }
        if(!actual.equals(proposed) || !materializable(actual)) throw stale();
        return receipt;
    }

    public void accepted(Receipt receipt,UUID activityId) {
        Instant now=receipts.databaseTime();
        if(!receipt.matches(receipt.context(),now)) throw stale();
        receipts.replace(receipt,receipt.close(now,Outcome.ACCEPTED,null,activityId));
    }

    public static boolean materializable(NextLearningAction action) {
        return switch(action.actionType()) {
            case UNDERSTAND,HINT,PREREQUISITE_SUPPORT,RETRIEVE,UNDERSTANDING_CHECK,CONNECT,APPLY -> action.aiTaskRequired();
            case REFLECT -> !action.aiTaskRequired();
            default -> false;
        };
    }

    private static StudyMissionStartException stale() {
        return new StudyMissionStartException(StudyMissionStartException.Kind.STATE_CHANGED,Reason.STATE_CHANGED);
    }
}
