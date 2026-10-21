package com.hippocampus.learning.application;

import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningEngine;
import com.hippocampus.learning.domain.NextLearningAction;
import com.hippocampus.learning.domain.StudyMission;
import com.hippocampus.learning.domain.StudyMissionGroundingMode;
import com.hippocampus.learning.domain.StudyMissionStatus;
import com.hippocampus.learning.port.InitialStudyMissionSourceAuthority;
import com.hippocampus.learning.port.StudyMissionRepository;
import com.hippocampus.learning.port.StudyMissionStartReceiptRepository;
import com.hippocampus.learning.port.StudyMissionStartReceiptRepository.*;
import com.hippocampus.shared.application.error.ApplicationNotFoundException;
import com.hippocampus.shared.domain.error.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Separately proxied short transactions; the composition never encloses provider calls in a transaction. */
public class StudyMissionStartTransactions {
    private final CurrentUser currentUser;
    private final StartStudyMissionUseCase start;
    private final StudyMissionRepository missions;
    private final StudyMissionStartReceiptRepository receipts;
    private final InitialStudyMissionSourceAuthority sources;
    private final InitialStudyMissionStateAssembler assembler;
    private final LearningEngine engine;

    public StudyMissionStartTransactions(CurrentUser currentUser,StartStudyMissionUseCase start,
            StudyMissionRepository missions,StudyMissionStartReceiptRepository receipts,
            InitialStudyMissionSourceAuthority sources,InitialStudyMissionStateAssembler assembler,LearningEngine engine) {
        this.currentUser=Objects.requireNonNull(currentUser);this.start=Objects.requireNonNull(start);
        this.missions=Objects.requireNonNull(missions);this.receipts=Objects.requireNonNull(receipts);
        this.sources=Objects.requireNonNull(sources);this.assembler=Objects.requireNonNull(assembler);
        this.engine=Objects.requireNonNull(engine);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED,timeout=5)
    public View create(StartStudyMissionExperienceUseCase.Command command) {
        receipts.configureTransactionLimits();
        UUID owner=owner();
        String fingerprint=command.fingerprint(owner);
        var previous=receipts.findByRequest(owner,command.startRequestId());
        if(previous.isPresent()) {
            if(!previous.get().requestFingerprint().equals(fingerprint)) throw conflict();
            return readOwned(previous.get(),true);
        }
        var result=start.execute(new StartStudyMissionUseCase.Command(command.topicId(),null,
                command.learningObjectiveText(),command.availableTimeMinutes(),StudyMissionGroundingMode.STRICT_SOURCE,
                command.materialIds().stream().map(id->new StartStudyMissionUseCase.SelectedSourceScope(id,null)).toList()));
        StudyMission mission=result.mission();
        Receipt receipt=Receipt.ready(owner,command.startRequestId(),fingerprint,mission.id(),
                mission.objectives().getFirst().id(),InitialStudyMissionStateAssembler.frozenFingerprint(mission),
                actionFingerprint(result.firstAction()),receipts.databaseTime());
        receipts.insert(receipt);
        return new View(mission,receipt,false,receipts.databaseTime(),null);
    }

    @Transactional(readOnly=true,isolation=Isolation.READ_COMMITTED,timeout=5)
    public View replay(UUID key,String expectedFingerprint) {
        Receipt receipt=receipts.findByRequest(owner(),key).orElseThrow(StudyMissionStartTransactions::notFound);
        if(expectedFingerprint!=null && !expectedFingerprint.equals(receipt.requestFingerprint())) throw conflict();
        return readOwned(receipt,true);
    }

    @Transactional(readOnly=true,isolation=Isolation.READ_COMMITTED,timeout=5)
    public View recover(UUID missionId,UUID key,boolean replayed) {
        Receipt receipt=receipts.findByMission(owner(),missionId).filter(r->r.startRequestId().equals(key))
                .orElseThrow(StudyMissionStartTransactions::notFound);
        return readOwned(receipt,replayed);
    }

    private View readOwned(Receipt receipt,boolean replayed) {
        StudyMission mission=missions.findOwnedById(receipt.missionId(),owner()).orElseThrow(StudyMissionStartTransactions::notFound);
        // Never expose saved content. Revalidate source authority even for accepted-result replay.
        Reason recovery=null;
        try { sources.inspect(mission.userId(),mission.materials()); }
        catch(com.hippocampus.learning.port.InitialStudyMissionSourceAuthority.SourceUnavailableException unavailable) { recovery=Reason.SOURCE_UNAVAILABLE; }
        catch(StudyMissionStartException unavailable) { recovery=unavailable.reason(); }
        return new View(mission,receipt,replayed,receipts.databaseTime(),null,recovery);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED,timeout=5)
    public View claim(UUID missionId,UUID key,boolean replayed) {
        receipts.configureTransactionLimits();
        StudyMission mission=missions.findFreshOwnedByIdForUpdate(missionId,owner()).orElseThrow(StudyMissionStartTransactions::notFound);
        Receipt original=receipts.lockByMission(owner(),missionId).filter(r->r.startRequestId().equals(key))
                .orElseThrow(StudyMissionStartTransactions::notFound);
        Instant now=receipts.databaseTime();
        Receipt receipt=original;
        if(receipt.state()==State.ACTIVITY_CREATED) return new View(mission,receipt,replayed,now,null);
        if(!mission.activities().isEmpty() || mission.currentActivityId()!=null) throw stale();
        if(receipt.state()==State.CLAIMED) {
            if(now.isBefore(receipt.leaseExpiresAt())) return new View(mission,receipt,replayed,now,null);
            receipt=receipt.close(now,Outcome.LEASE_EXPIRED,Reason.PREPARATION_TIMEOUT,null);
        }
        java.util.List<com.hippocampus.learning.port.InitialStudyMissionSourceAuthority.ScopeFacts> facts;
        try { facts=sources.lockAndInspect(owner(),mission.materials(),Set.of(),false); }
        catch(com.hippocampus.learning.port.InitialStudyMissionSourceAuthority.SourceUnavailableException unavailable) {
            throw new StudyMissionStartException(StudyMissionStartException.Kind.UNAVAILABLE,Reason.SOURCE_UNAVAILABLE);
        }
        now=receipts.databaseTime();
        if(mission.status()!=StudyMissionStatus.ACTIVE || receipt.attemptsUsed()>=3
                || (receipt.nextRetryAt()!=null && now.isBefore(receipt.nextRetryAt()))) {
            receipts.replace(original,receipt);
            return new View(mission,receipt,replayed,now,null);
        }
        String blocking=assembler.blockingFingerprint(mission,facts,now);
        if(blocking.equals(receipt.blockingContextFingerprint())) return new View(mission,receipt,replayed,now,null);
        NextLearningAction action=engine.decide(assembler.assemble(mission,InitialStudyMissionSourceAuthority.capability(facts),now));
        if(!InitialStudyMissionAdmission.materializable(action)) {
            Reason reason=action.actionType()==LearningActionType.COMPLETE
                    ? Reason.INITIAL_COMPLETION_NOT_ADMISSIBLE : unexpected(action) ? Reason.POLICY_DECISION_REQUIRED : null;
            Receipt block=receipt.block(action,blocking,reason,now);
            receipts.replace(original,block);
            return new View(mission,block,replayed,now,null);
        }
        Snapshot snapshot=new Snapshot(mission.updatedAt(),null,0,0,mission.objectives().getFirst().id(),
                InitialStudyMissionStateAssembler.frozenFingerprint(mission));
        Receipt claimed=receipt.claim(UUID.randomUUID(),snapshot,action,now);
        receipts.replace(original,claimed);
        return new View(mission,claimed,replayed,now,claimed.context());
    }

    @Transactional(isolation=Isolation.READ_COMMITTED,timeout=5)
    public void failed(InitialContext context,Reason reason) {
        receipts.configureTransactionLimits();
        if(!owner().equals(context.ownerId())) throw notFound();
        missions.findFreshOwnedByIdForUpdate(context.missionId(),owner()).orElseThrow(StudyMissionStartTransactions::notFound);
        var optional=receipts.lockByMission(owner(),context.missionId());
        if(optional.isEmpty()) return;
        Receipt receipt=optional.get();
        // A deadline-expired current claim may be closed, but a replaced fence may never be touched.
        if(receipt.state()!=State.CLAIMED || receipt.generation()!=context.generation()
                || !receipt.claimToken().equals(context.claimToken()) || !receipt.startRequestId().equals(context.startRequestId())) return;
        receipts.replace(receipt,receipt.close(receipts.databaseTime(),Outcome.FAILED,reason,null));
    }

    @Transactional(readOnly=true,isolation=Isolation.READ_COMMITTED,timeout=5)
    public void authorizeExposure(InitialContext context,Set<UUID> references) {
        Objects.requireNonNull(references);
        if(!owner().equals(context.ownerId())) throw notFound();
        StudyMission mission=missions.findOwnedById(context.missionId(),owner()).orElseThrow(StudyMissionStartTransactions::notFound);
        Receipt receipt=receipts.findByMission(owner(),mission.id()).orElseThrow(StudyMissionStartTransactions::notFound);
        if(!receipt.matches(context,receipts.databaseTime()) || mission.status()!=StudyMissionStatus.ACTIVE
                || mission.currentActivityId()!=null || !mission.activities().isEmpty() || mission.objectives().size()!=1
                || !mission.updatedAt().equals(context.snapshot().updatedAt())
                || !InitialStudyMissionStateAssembler.frozenFingerprint(mission).equals(context.snapshot().frozenBindingFingerprint())) throw stale();
        var identity=new StartStudyMissionExperienceUseCase.Command(receipt.startRequestId(),mission.topicId(),
                mission.objectives().getFirst().objectiveText(),mission.availableTimeMinutes(),
                mission.materials().stream().map(com.hippocampus.learning.domain.MissionMaterial::materialId).toList());
        if(!identity.fingerprint(owner()).equals(receipt.requestFingerprint())) throw stale();
        try { sources.inspectForExposure(owner(),mission.materials(),references); }
        catch(com.hippocampus.learning.port.InitialStudyMissionSourceAuthority.SourceUnavailableException unavailable) {
            throw new StudyMissionStartException(StudyMissionStartException.Kind.UNAVAILABLE,Reason.SOURCE_UNAVAILABLE);
        }
    }

    /** Existing lifecycle use case already holds the mission row; do not reacquire it out of order. */
    public void fenceForLifecycle(StudyMission mission,boolean terminal) {
        receipts.lockByMission(mission.userId(),mission.id()).ifPresent(receipt->{
            if(receipt.state()==State.CLAIMED) receipts.replace(receipt,receipt.close(receipts.databaseTime(),
                    Outcome.INTERRUPTED,terminal?Reason.MISSION_TERMINAL:Reason.MISSION_PAUSED,null));
        });
    }

    public record View(StudyMission mission,Receipt receipt,boolean replayed,Instant observedAt,InitialContext work,Reason recoveryReason) {
        public View(StudyMission mission,Receipt receipt,boolean replayed,Instant observedAt,InitialContext work) {
            this(mission,receipt,replayed,observedAt,work,null);
        }
        public int retrySeconds() {
            Instant until=receipt.state()==State.CLAIMED ? receipt.leaseExpiresAt() : receipt.nextRetryAt();
            if(until==null || !observedAt.isBefore(until)) return 0;
            return (int)Math.min(180,(Duration.between(observedAt,until).toMillis()+999)/1000);
        }
        public boolean canRetry() {
            return recoveryReason!=Reason.SOURCE_UNAVAILABLE && mission.status()==StudyMissionStatus.ACTIVE && receipt.attemptsUsed()<3
                    && (receipt.state()!=State.CLAIMED || !observedAt.isBefore(receipt.leaseExpiresAt()))
                    && receipt.state()!=State.ACTIVITY_CREATED
                    && receipt.reasonCode()!=Reason.INITIAL_COMPLETION_NOT_ADMISSIBLE
                    && receipt.reasonCode()!=Reason.POLICY_DECISION_REQUIRED;
        }
    }
    private UUID owner() { return currentUser.authenticatedUser().userId(); }
    private static boolean unexpected(NextLearningAction action) {
        return switch(action.actionType()) {
            case FEEDBACK,RETRY,REDUCE_DIFFICULTY,REUSE_VALIDATED_CONTENT,START -> true;
            default -> false;
        };
    }
    private static String actionFingerprint(NextLearningAction action) {
        return StudyMissionStartReceiptRepository.fingerprint("HIPPOCAMPUS/P714/ACTION/1",List.of(action.toString()));
    }
    private static StudyMissionStartException conflict() { return new StudyMissionStartException(StudyMissionStartException.Kind.CONFLICT,Reason.STATE_CHANGED); }
    private static StudyMissionStartException stale() { return new StudyMissionStartException(StudyMissionStartException.Kind.STATE_CHANGED,Reason.STATE_CHANGED); }
    private static ApplicationNotFoundException notFound() { return new ApplicationNotFoundException(new ErrorCode("STUDY_MISSION_NOT_FOUND"),"Study mission was not found."); }
}
