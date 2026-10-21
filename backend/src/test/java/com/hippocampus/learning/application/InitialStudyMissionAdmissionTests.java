package com.hippocampus.learning.application;

import com.hippocampus.learning.domain.LearningActionConstraints;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningDifficulty;
import com.hippocampus.learning.domain.NextLearningAction;
import com.hippocampus.learning.port.StudyMissionStartReceiptRepository.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InitialStudyMissionAdmissionTests {
    private static final UUID OWNER=UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID KEY=UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID MISSION=UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID OBJECTIVE=UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final Instant NOW=Instant.parse("2026-10-20T00:00:00Z");
    private static final String HASH="a".repeat(64);

    @Test void deadlineRejectsAWorkerWhileItsLeaseIsStillLive() {
        Receipt claim=ready().claim(UUID.randomUUID(),snapshot(),action(),NOW);
        assertTrue(claim.matches(claim.context(),NOW.plusSeconds(149)));
        assertFalse(claim.matches(claim.context(),NOW.plusSeconds(150)));
        assertTrue(NOW.plusSeconds(150).isBefore(claim.leaseExpiresAt()));
    }

    @Test void takeoverRejectsTheOldWorkerAndPreservesItsClosedHistory() {
        Receipt first=ready().claim(UUID.randomUUID(),snapshot(),action(),NOW);
        Receipt failed=first.close(NOW.plusSeconds(180),Outcome.LEASE_EXPIRED,Reason.PREPARATION_TIMEOUT,null);
        Receipt next=failed.claim(UUID.randomUUID(),snapshot(),action(),NOW.plusSeconds(180));
        assertFalse(next.matches(first.context(),NOW.plusSeconds(181)));
        assertTrue(next.matches(next.context(),NOW.plusSeconds(196)));
        assertEquals(failed.attemptHistory().getFirst(),next.attemptHistory().getFirst());
        assertEquals(2,next.attemptsUsed());
    }

    @Test void threeFailuresCannotConsumeAFourthClaimOrRestartAnAcceptedReceipt() {
        Receipt receipt=ready();
        for(int i=0;i<3;i++) {
            Instant now=NOW.plusSeconds(20L*i);
            receipt=receipt.claim(UUID.randomUUID(),snapshot(),action(),now)
                    .close(now.plusSeconds(1),Outcome.FAILED,Reason.DEPENDENCY_UNAVAILABLE,null);
        }
        Receipt exhausted=receipt;
        assertEquals(State.EXHAUSTED,exhausted.state());
        assertEquals(Reason.RETRY_LIMIT_REACHED,exhausted.reasonCode());
        assertThrows(IllegalStateException.class,()->exhausted.claim(UUID.randomUUID(),snapshot(),action(),NOW.plusSeconds(90)));
        Receipt accepted=ready().claim(UUID.randomUUID(),snapshot(),action(),NOW)
                .close(NOW.plusSeconds(1),Outcome.ACCEPTED,null,UUID.randomUUID());
        assertThrows(IllegalStateException.class,()->accepted.claim(UUID.randomUUID(),snapshot(),action(),NOW.plusSeconds(90)));
    }

    @Test void inadmissibleInitialCompletionCreatesNeitherClaimNorActivityAndRepeatsWithoutChurn() {
        NextLearningAction complete=new NextLearningAction(LearningActionType.COMPLETE,OBJECTIVE,"topic:test",null,
                "TIME_LIMIT",false,LearningActionConstraints.unconstrained());
        Receipt blocked=ready().block(complete,HASH,Reason.INITIAL_COMPLETION_NOT_ADMISSIBLE,NOW);
        assertEquals(State.NO_ACTIVITY,blocked.state());
        assertEquals(0,blocked.attemptsUsed());
        assertNull(blocked.acceptedActivityId());
        assertFalse(InitialStudyMissionAdmission.materializable(complete));
        assertSame(blocked,blocked.block(complete,HASH,Reason.INITIAL_COMPLETION_NOT_ADMISSIBLE,NOW.plusSeconds(5)));
    }

    @Test void idempotencyNormalizesOnlyApprovedFieldsAndIsOwnerScoped() {
        UUID topic=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID();
        var first=new StartStudyMissionExperienceUseCase.Command(KEY,topic,"  Objective\r\nnext  ",20,List.of(a,b));
        var same=new StartStudyMissionExperienceUseCase.Command(UUID.randomUUID(),topic,"Objective\nnext",20,List.of(b,a));
        assertEquals(first.fingerprint(OWNER),same.fingerprint(OWNER));
        assertNotEquals(first.fingerprint(OWNER),first.fingerprint(UUID.randomUUID()));
        var changed=new StartStudyMissionExperienceUseCase.Command(KEY,topic,"Objective\nnext",21,List.of(a,b));
        assertNotEquals(first.fingerprint(OWNER),changed.fingerprint(OWNER));
    }

    @Test void invalidUnicodeAndDuplicateSourcesCannotAcquireRequestIdentity() {
        UUID topic=UUID.randomUUID(),source=UUID.randomUUID();
        assertThrows(StudyMissionStartException.class,()->new StartStudyMissionExperienceUseCase.Command(
                KEY,topic,"bad\uD800",20,List.of(source)));
        assertThrows(StudyMissionStartException.class,()->new StartStudyMissionExperienceUseCase.Command(
                KEY,topic,"Objective",20,List.of(source,source)));
    }

    @Test void missingClaimFailsClosedBeforeSourceOrReceiptAccess() {
        var receipts=org.mockito.Mockito.mock(com.hippocampus.learning.port.StudyMissionStartReceiptRepository.class);
        var sources=org.mockito.Mockito.mock(com.hippocampus.learning.port.InitialStudyMissionSourceAuthority.class);
        var admission=new InitialStudyMissionAdmission(receipts,sources,new InitialStudyMissionStateAssembler(),
                InitialStudyMissionStateAssemblerTests.engine());
        assertThrows(StudyMissionStartException.class,()->admission.admit(
                InitialStudyMissionStateAssemblerTests.mission(20),null,action(),java.util.Set.of(),false));
        org.mockito.Mockito.verifyNoInteractions(receipts,sources);
    }

    @Test void finalCompletionClosesOnlyOperationalClaimAndDiscardsTheInitialDraft() {
        var mission=InitialStudyMissionStateAssemblerTests.mission(2);
        var assembler=new InitialStudyMissionStateAssembler();
        var engine=InitialStudyMissionStateAssemblerTests.engine();
        var selected=engine.decide(assembler.assemble(mission,InitialStudyMissionStateAssemblerTests.CAPABILITY,NOW));
        String frozen=InitialStudyMissionStateAssembler.frozenFingerprint(mission);
        var ready=Receipt.ready(mission.userId(),UUID.randomUUID(),HASH,mission.id(),
                mission.objectives().getFirst().id(),frozen,HASH,NOW);
        var claimed=ready.claim(UUID.randomUUID(),new Snapshot(mission.updatedAt(),null,0,0,
                mission.objectives().getFirst().id(),frozen),selected,NOW);
        var receipts=org.mockito.Mockito.mock(com.hippocampus.learning.port.StudyMissionStartReceiptRepository.class);
        var sources=org.mockito.Mockito.mock(com.hippocampus.learning.port.InitialStudyMissionSourceAuthority.class);
        org.mockito.Mockito.when(receipts.lockByMission(mission.userId(),mission.id())).thenReturn(java.util.Optional.of(claimed));
        org.mockito.Mockito.when(receipts.databaseTime()).thenReturn(NOW.plusSeconds(121));
        var scope=mission.materials().getFirst();
        var facts=java.util.List.of(new com.hippocampus.learning.port.InitialStudyMissionSourceAuthority.ScopeFacts(
                scope.materialId(),scope.materialVersionId(),null,"READY","READY",
                com.hippocampus.learning.domain.SourceReadiness.READY,true,false,false,
                java.util.Set.of(UUID.randomUUID()),java.util.Set.of()));
        org.mockito.Mockito.when(sources.lockAndInspect(mission.userId(),mission.materials(),java.util.Set.of(),false)).thenReturn(facts);
        var admission=new InitialStudyMissionAdmission(receipts,sources,assembler,engine);
        assertNull(admission.admit(mission,claimed.context(),selected,java.util.Set.of(),false));
        var captured=org.mockito.ArgumentCaptor.forClass(Receipt.class);
        org.mockito.Mockito.verify(receipts).replace(org.mockito.ArgumentMatchers.eq(claimed),captured.capture());
        assertEquals(State.NO_ACTIVITY,captured.getValue().state());
        assertEquals(Reason.INITIAL_COMPLETION_NOT_ADMISSIBLE,captured.getValue().reasonCode());
        assertEquals(LearningActionType.COMPLETE,captured.getValue().selectedAction().actionType());
        assertEquals(1,captured.getValue().attemptsUsed());
        assertNull(captured.getValue().acceptedActivityId());
        assertTrue(mission.activities().isEmpty());
        assertEquals(com.hippocampus.learning.domain.StudyMissionStatus.ACTIVE,mission.status());
    }

    @Test void providerExposureChecksTheExactSelectedReferencesWithoutWritingOrLocking() {
        var mission=InitialStudyMissionStateAssemblerTests.mission(20);
        UUID key=UUID.randomUUID(),reference=UUID.randomUUID();
        String frozen=InitialStudyMissionStateAssembler.frozenFingerprint(mission);
        var command=new StartStudyMissionExperienceUseCase.Command(key,mission.topicId(),mission.objectives().getFirst().objectiveText(),20,
            mission.materials().stream().map(com.hippocampus.learning.domain.MissionMaterial::materialId).toList());
        var selected=new NextLearningAction(LearningActionType.UNDERSTAND,mission.objectives().getFirst().id(),"topic:"+mission.topicId(),
            LearningDifficulty.FOUNDATIONAL,"INITIAL",true,LearningActionConstraints.unconstrained());
        var claim=Receipt.ready(mission.userId(),key,command.fingerprint(mission.userId()),mission.id(),mission.objectives().getFirst().id(),frozen,HASH,NOW)
            .claim(UUID.randomUUID(),new Snapshot(mission.updatedAt(),null,0,0,mission.objectives().getFirst().id(),frozen),selected,NOW);
        var missions=org.mockito.Mockito.mock(com.hippocampus.learning.port.StudyMissionRepository.class);
        var receipts=org.mockito.Mockito.mock(com.hippocampus.learning.port.StudyMissionStartReceiptRepository.class);
        var sources=org.mockito.Mockito.mock(com.hippocampus.learning.port.InitialStudyMissionSourceAuthority.class);
        org.mockito.Mockito.when(missions.findOwnedById(mission.id(),mission.userId())).thenReturn(java.util.Optional.of(mission));
        org.mockito.Mockito.when(receipts.findByMission(mission.userId(),mission.id())).thenReturn(java.util.Optional.of(claim));
        org.mockito.Mockito.when(receipts.databaseTime()).thenReturn(NOW.plusSeconds(1));
        var tx=new StudyMissionStartTransactions(()->new com.hippocampus.identity.domain.AuthenticatedUser(mission.userId()),
            org.mockito.Mockito.mock(StartStudyMissionUseCase.class),missions,receipts,sources,new InitialStudyMissionStateAssembler(),
            InitialStudyMissionStateAssemblerTests.engine());
        var ids=java.util.Set.of(reference);
        var actual=claim.context();
        var forged=new InitialContext(actual.ownerId(),actual.startRequestId(),actual.missionId(),actual.generation()+1,
            actual.claimToken(),actual.workDeadline(),actual.leaseExpiresAt(),actual.snapshot(),actual.selectedAction());
        assertThrows(StudyMissionStartException.class,()->tx.authorizeExposure(forged,ids));
        org.mockito.Mockito.verifyNoInteractions(sources);
        org.mockito.Mockito.when(sources.inspectForExposure(mission.userId(),mission.materials(),ids))
            .thenThrow(new com.hippocampus.learning.port.InitialStudyMissionSourceAuthority.SourceUnavailableException())
            .thenReturn(java.util.List.of());
        var unavailable=assertThrows(StudyMissionStartException.class,()->tx.authorizeExposure(claim.context(),ids));
        assertEquals(StudyMissionStartException.Kind.UNAVAILABLE,unavailable.kind());
        assertEquals(Reason.SOURCE_UNAVAILABLE,unavailable.reason());
        assertDoesNotThrow(()->tx.authorizeExposure(claim.context(),ids));
        org.mockito.Mockito.verify(sources,org.mockito.Mockito.times(2)).inspectForExposure(mission.userId(),mission.materials(),ids);
        org.mockito.Mockito.verifyNoMoreInteractions(sources);
        org.mockito.Mockito.verify(receipts,org.mockito.Mockito.never()).configureTransactionLimits();
        org.mockito.Mockito.verify(receipts,org.mockito.Mockito.never()).replace(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(missions,org.mockito.Mockito.never()).findFreshOwnedByIdForUpdate(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
    }

    @Test void finalSourcePortDenialDoesNotFinalizeReceiptOrAuthorizeActivity() {
        var mission=InitialStudyMissionStateAssemblerTests.mission(20);
        var assembler=new InitialStudyMissionStateAssembler();var engine=InitialStudyMissionStateAssemblerTests.engine();
        var selected=engine.decide(assembler.assemble(mission,InitialStudyMissionStateAssemblerTests.CAPABILITY,NOW));
        String frozen=InitialStudyMissionStateAssembler.frozenFingerprint(mission);
        var claim=Receipt.ready(mission.userId(),UUID.randomUUID(),HASH,mission.id(),mission.objectives().getFirst().id(),frozen,HASH,NOW)
                .claim(UUID.randomUUID(),new Snapshot(mission.updatedAt(),null,0,0,mission.objectives().getFirst().id(),frozen),selected,NOW);
        var receipts=org.mockito.Mockito.mock(com.hippocampus.learning.port.StudyMissionStartReceiptRepository.class);
        var sources=org.mockito.Mockito.mock(com.hippocampus.learning.port.InitialStudyMissionSourceAuthority.class);
        org.mockito.Mockito.when(receipts.lockByMission(mission.userId(),mission.id())).thenReturn(java.util.Optional.of(claim));
        org.mockito.Mockito.when(receipts.databaseTime()).thenReturn(NOW);
        org.mockito.Mockito.when(sources.lockAndInspect(mission.userId(),mission.materials(),java.util.Set.of(),false))
                .thenThrow(new com.hippocampus.learning.port.InitialStudyMissionSourceAuthority.SourceUnavailableException());
        var admission=new InitialStudyMissionAdmission(receipts,sources,assembler,engine);
        var failure=assertThrows(StudyMissionStartException.class,()->admission.admit(mission,claim.context(),selected,java.util.Set.of(),false));
        assertEquals(StudyMissionStartException.Kind.UNAVAILABLE,failure.kind());assertEquals(Reason.SOURCE_UNAVAILABLE,failure.reason());
        org.mockito.Mockito.verify(receipts,org.mockito.Mockito.never()).replace(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
        assertTrue(mission.activities().isEmpty());assertNull(claim.acceptedActivityId());assertEquals(State.CLAIMED,claim.state());
    }

    @Test void applicationFailureKindsKeepEveryExistingPublicCodeAndSafeMessage() {
        var codes=java.util.Map.of(StudyMissionStartException.Kind.CONFLICT,"STUDY_MISSION_START_REQUEST_CONFLICT",
                StudyMissionStartException.Kind.STATE_CHANGED,"STUDY_MISSION_INITIAL_STATE_CHANGED",
                StudyMissionStartException.Kind.SOURCE_BUSY,"STUDY_MISSION_SOURCE_BUSY",
                StudyMissionStartException.Kind.UNAVAILABLE,"STUDY_MISSION_START_UNAVAILABLE",
                StudyMissionStartException.Kind.MALFORMED,"MALFORMED_REQUEST",
                StudyMissionStartException.Kind.INVALID_INITIAL_CONTRACT,"INVALID_INITIAL_ACTIVITY_CONTRACT",
                StudyMissionStartException.Kind.TOO_LARGE,"REQUEST_TOO_LARGE");
        for(var kind:StudyMissionStartException.Kind.values()) {
            var failure=new StudyMissionStartException(kind,Reason.SOURCE_UNAVAILABLE);
            assertInstanceOf(com.hippocampus.shared.application.error.ApplicationException.class,failure);
            assertEquals(codes.get(kind),failure.errorCode().value());assertEquals(failure.getMessage(),failure.clientMessage());
            assertEquals(kind,failure.kind());assertEquals(Reason.SOURCE_UNAVAILABLE,failure.reason());
        }
    }

    private static Receipt ready() { return Receipt.ready(OWNER,KEY,HASH,MISSION,OBJECTIVE,HASH,HASH,NOW); }
    private static Snapshot snapshot() { return new Snapshot(NOW,null,0,0,OBJECTIVE,HASH); }
    private static NextLearningAction action() {
        return new NextLearningAction(LearningActionType.UNDERSTAND,OBJECTIVE,"topic:test",
                LearningDifficulty.FOUNDATIONAL,"INITIAL",true,LearningActionConstraints.unconstrained());
    }
}
