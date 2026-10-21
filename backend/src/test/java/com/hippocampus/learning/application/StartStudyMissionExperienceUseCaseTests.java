package com.hippocampus.learning.application;

import com.hippocampus.identity.domain.AuthenticatedUser;
import com.hippocampus.learning.domain.*;
import com.hippocampus.learning.port.StudyMissionStartReceiptRepository.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class StartStudyMissionExperienceUseCaseTests {
    private final StudyMission mission=InitialStudyMissionStateAssemblerTests.mission(20);
    private final Instant now=InitialStudyMissionStateAssemblerTests.NOW;
    private final UUID key=UUID.randomUUID();
    private final StudyMissionStartTransactions tx=mock(StudyMissionStartTransactions.class);
    private final MaterializeLearningActivityUseCase materialize=mock(MaterializeLearningActivityUseCase.class);
    private final StartStudyMissionExperienceUseCase.Command command=new StartStudyMissionExperienceUseCase.Command(key,mission.topicId(),
        mission.objectives().getFirst().objectiveText(),20,mission.materials().stream().map(MissionMaterial::materialId).toList());
    private final Receipt ready=Receipt.ready(mission.userId(),key,command.fingerprint(mission.userId()),mission.id(),mission.objectives().getFirst().id(),
        InitialStudyMissionStateAssembler.frozenFingerprint(mission),"a".repeat(64),now);
    private final NextLearningAction action=new NextLearningAction(LearningActionType.UNDERSTAND,mission.objectives().getFirst().id(),
        "topic:"+mission.topicId(),LearningDifficulty.FOUNDATIONAL,"INITIAL",true,LearningActionConstraints.unconstrained());
    private final Receipt claimed=ready.claim(UUID.randomUUID(),new Snapshot(mission.updatedAt(),null,0,0,
        mission.objectives().getFirst().id(),ready.frozenBindingFingerprint()),action,now);
    private final StudyMissionStartTransactions.View initial=new StudyMissionStartTransactions.View(mission,ready,false,now,null);
    private final StudyMissionStartTransactions.View work=new StudyMissionStartTransactions.View(mission,claimed,false,now,claimed.context());
    private StartStudyMissionExperienceUseCase useCase(boolean allowed) {
        when(tx.create(command)).thenReturn(initial);
        when(tx.claim(mission.id(),key,false)).thenReturn(work);
        when(tx.recover(mission.id(),key,false)).thenReturn(initial);
        return new StartStudyMissionExperienceUseCase(()->new AuthenticatedUser(mission.userId()),tx,materialize,()->allowed);
    }
    @Test void environmentOutsideApprovedEnvelopeDoesNotClaimOrCallProvider() {
        assertSame(initial,useCase(false).start(command));
        verify(tx).create(command);verifyNoMoreInteractions(tx);verifyNoInteractions(materialize);
    }
    @Test void workerReceivesAuthenticationAndFullFenceAndOnlyRecoversAfterExecution() {
        var useCase=useCase(true);
        var authentication=UsernamePasswordAuthenticationToken.authenticated("synthetic-owner",null,List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            when(materialize.execute(any())).thenAnswer(call->{
                assertSame(authentication,SecurityContextHolder.getContext().getAuthentication());
                var received=call.getArgument(0,MaterializeLearningActivityUseCase.Command.class);
                assertEquals(claimed.context(),received.initialContext());assertNotNull(received.deadline());return null;
            });
            assertSame(initial,useCase.start(command));
            var order=inOrder(tx,materialize);order.verify(tx).create(command);order.verify(tx).claim(mission.id(),key,false);
            order.verify(materialize).execute(any());order.verify(tx).recover(mission.id(),key,false);
        } finally { SecurityContextHolder.clearContext(); }
    }
    @Test void uncertainCreateRecoversOriginalIdentityAndNeverCreatesAgain() {
        var useCase=useCase(false);
        when(tx.create(command)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("uncertain commit"));
        when(tx.replay(key,command.fingerprint(mission.userId()))).thenReturn(initial);
        assertSame(initial,useCase.start(command));
        verify(tx).create(command);verify(tx).replay(key,command.fingerprint(mission.userId()));verifyNoMoreInteractions(tx);
        verifyNoInteractions(materialize);
    }
    @Test void canceledLogicalFutureRetainsOwnerPermitUntilPhysicalWorkerExit() throws Exception {
        var useCase=useCase(true);var entered=new CountDownLatch(4);var release=new CountDownLatch(1);
        var failure=new AtomicReference<Throwable>();
        when(materialize.execute(any())).thenAnswer(call->{
            entered.countDown();
            boolean done=false;while(!done)try { done=release.await(5,TimeUnit.SECONDS);if(!done)throw new AssertionError("fixture release timed out"); }
            catch(InterruptedException ignored) { /* simulate best-effort cancellation unable to stop a transport */ }
            return null;
        });
        var callers=new ArrayList<Thread>();
        try {
            for(int i=0;i<4;i++) callers.add(Thread.ofVirtual().start(()->{try{useCase.start(command);}catch(Throwable t){failure.set(t);}}));
            assertTrue(entered.await(3,TimeUnit.SECONDS));
            for(var caller:callers)caller.interrupt();
            for(var caller:callers){caller.join(2000);assertFalse(caller.isAlive());}
            assertNull(failure.get());
            assertSame(initial,useCase.start(command));
            verify(tx,times(4)).claim(mission.id(),key,false);
            verify(materialize,times(4)).execute(any());
        } finally { release.countDown();for(var caller:callers)caller.join(2000); }
    }
    @Test void clientProjectionPreservesAcceptedPreparingRecoveryAndEmptyCompletion() {
        UUID acceptedId=UUID.randomUUID();
        var accepted=claimed.close(now.plusSeconds(1),Outcome.ACCEPTED,null,acceptedId);
        var acceptedResult=StartStudyMissionExperienceUseCase.ClientResult.from(new StudyMissionStartTransactions.View(mission,accepted,true,now.plusSeconds(2),null));
        assertEquals("ACTIVITY_CREATED",acceptedResult.outcome());assertEquals(acceptedId,acceptedResult.activityId());
        assertEquals(mission.id(),acceptedResult.missionId());assertEquals(key,acceptedResult.startRequestId());assertTrue(acceptedResult.replayed());
        var preparing=StartStudyMissionExperienceUseCase.ClientResult.from(new StudyMissionStartTransactions.View(mission,claimed,false,now.plusSeconds(1),null));
        assertEquals("PREPARING",preparing.outcome());assertEquals(5,preparing.retryAfterSeconds());
        var recovery=StartStudyMissionExperienceUseCase.ClientResult.from(new StudyMissionStartTransactions.View(mission,accepted,true,now,null,Reason.SOURCE_UNAVAILABLE));
        assertEquals("RECOVERY_REQUIRED",recovery.outcome());assertEquals("SOURCE_UNAVAILABLE",recovery.reasonCode());assertNull(recovery.activityId());
        var complete=new NextLearningAction(LearningActionType.COMPLETE,mission.objectives().getFirst().id(),"topic:test",null,"TIME_LIMIT",false,LearningActionConstraints.unconstrained());
        var blocked=ready.block(complete,"a".repeat(64),Reason.INITIAL_COMPLETION_NOT_ADMISSIBLE,now);
        var empty=StartStudyMissionExperienceUseCase.ClientResult.from(new StudyMissionStartTransactions.View(mission,blocked,false,now,null));
        assertEquals("NO_ACTIVITY",empty.outcome());assertEquals("COMPLETE",empty.actionType());
        assertEquals("INITIAL_COMPLETION_NOT_ADMISSIBLE",empty.reasonCode());assertFalse(empty.canRetry());
        assertEquals("ACTIVE",empty.missionStatus());assertNull(empty.activityId());assertTrue(mission.activities().isEmpty());
    }
    @Test void sourcePortFailureRetainsSourceUnavailableRecoveryReason() {
        var useCase=useCase(true);
        when(materialize.execute(any())).thenThrow(new com.hippocampus.learning.port.InitialStudyMissionSourceAuthority.SourceUnavailableException());
        assertSame(initial,useCase.start(command));
        verify(tx).failed(claimed.context(),Reason.SOURCE_UNAVAILABLE);
    }
}
