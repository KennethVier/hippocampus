package com.hippocampus.bootstrap;

import com.hippocampus.identity.domain.AuthenticatedUser;
import com.hippocampus.learning.application.InitialStudyMissionStateAssembler;
import com.hippocampus.learning.application.StartStudyMissionExperienceUseCase;
import com.hippocampus.learning.domain.*;
import com.hippocampus.learning.port.*;
import com.hippocampus.learning.port.StudyMissionStartReceiptRepository.*;
import com.hippocampus.rag.application.*;
import com.hippocampus.rag.domain.*;
import com.hippocampus.rag.port.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class LearningActivityEvidenceAdapterTests {
    private final Instant now=Instant.parse("2026-10-20T00:00:00Z");
    private final UUID owner=UUID.randomUUID(),topic=UUID.randomUUID(),id=UUID.randomUUID();
    private final LearningObjective objective=new LearningObjective(UUID.randomUUID(),"Explain synthetic concept","topic:"+topic,
            "Synthetic",1,LearningObjectiveStatus.ACTIVE,now);
    private final StudyMission mission=new StudyMission(id,owner,topic,null,StudyMissionStatus.ACTIVE,LearningStage.UNDERSTANDING,
            StudyMissionGroundingMode.STRICT_SOURCE,20,now,null,null,null,
            List.of(new MissionMaterial(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),null)),List.of(objective),List.of(),now,now);
    private final NextLearningAction action=new NextLearningAction(LearningActionType.UNDERSTAND,objective.id(),objective.conceptKey(),
            LearningDifficulty.FOUNDATIONAL,"INITIAL",true,LearningActionConstraints.unconstrained());
    private final BuildRetrievalScope catalog=mock(BuildRetrievalScope.class);
    private final LexicalSearchRepository lexical=mock(LexicalSearchRepository.class);
    private final VectorSearchRepository vector=mock(VectorSearchRepository.class);
    private final ActiveIndexGenerationRepository generations=mock(ActiveIndexGenerationRepository.class);
    private final BuildEvidencePackage packages=mock(BuildEvidencePackage.class);
    private final MaterializeEvidenceSourceReferences references=mock(MaterializeEvidenceSourceReferences.class);
    private final StudyMissionRepository missions=mock(StudyMissionRepository.class);
    private final StudyMissionStartReceiptRepository receipts=mock(StudyMissionStartReceiptRepository.class);
    private final InitialStudyMissionSourceAuthority sources=mock(InitialStudyMissionSourceAuthority.class);
    private final EmbeddingPort embedding=mock(EmbeddingPort.class);
    private final Receipt claimed;
    private final LearningActivityEvidenceAdapter adapter;
    LearningActivityEvidenceAdapterTests() {
        UUID key=UUID.randomUUID();String frozen=InitialStudyMissionStateAssembler.frozenFingerprint(mission);
        var command=new StartStudyMissionExperienceUseCase.Command(key,topic,objective.objectiveText(),20,
                mission.materials().stream().map(MissionMaterial::materialId).toList());
        claimed=Receipt.ready(owner,key,command.fingerprint(owner),id,objective.id(),frozen,"a".repeat(64),now)
                .claim(UUID.randomUUID(),new Snapshot(now,null,0,0,objective.id(),frozen),action,now);
        when(missions.findOwnedById(id,owner)).thenReturn(Optional.of(mission));
        when(receipts.findByMission(owner,id)).thenReturn(Optional.of(claimed));
        when(receipts.databaseTime()).thenReturn(now.plusSeconds(1));
        var material=mission.materials().getFirst();
        when(sources.inspect(owner,mission.materials())).thenReturn(List.of(new InitialStudyMissionSourceAuthority.ScopeFacts(
                material.materialId(),material.materialVersionId(),null,"READY","READY",SourceReadiness.READY,true,false,false,
                Set.of(UUID.randomUUID()),Set.of())));
        when(generations.findActiveGeneration()).thenReturn(Optional.empty());
        var evidence=mock(EvidencePackage.class);
        when(packages.execute(any())).thenReturn(evidence);
        when(references.execute(eq(evidence),any(FrozenMissionSourceBinding.class))).thenReturn(List.of());
        adapter=new LearningActivityEvidenceAdapter(catalog,lexical,vector,generations,Optional.of(embedding),packages,references,
                new ActivityEvidenceRetrievalOptions(4,4,4,400,3,2),()->new AuthenticatedUser(owner),missions,receipts,sources);
    }
    private ActivityEvidencePort.Request request(UUID user) { return new ActivityEvidencePort.Request(user,id,topic,
            mission.groundingMode(),mission.materials(),objective,action); }

    @Test void initialRetrievalUsesPersistedFrozenVersionAndClaimWithoutCatalogSubstitution() {
        adapter.retrieve(request(owner),claimed.context());
        var capture=org.mockito.ArgumentCaptor.forClass(LexicalSearchRequest.class);
        verify(lexical).search(capture.capture());
        assertThat(capture.getValue().scope().targets()).extracting(RetrievalScopeTarget::materialVersionId)
                .containsExactly(mission.materials().getFirst().materialVersionId());
        assertThat(capture.getValue().frozenMission()).isEqualTo(new FrozenMissionSourceBinding(id,claimed.generation(),claimed.claimToken()));
        verifyNoInteractions(catalog,vector,embedding);
        verify(references).execute(any(EvidencePackage.class),eq(capture.getValue().frozenMission()));
    }
    @Test void forgedOwnerCannotReachRetrievalOrProvider() {
        assertThatThrownBy(()->adapter.retrieve(request(UUID.randomUUID()),claimed.context())).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(catalog,lexical,vector,embedding,packages,references);
    }
    @Test void supersededClaimCannotReachRetrievalOrProvider() {
        var next=claimed.close(now.plusSeconds(180),Outcome.LEASE_EXPIRED,Reason.PREPARATION_TIMEOUT,null)
                .claim(UUID.randomUUID(),claimed.claimSnapshot(),action,now.plusSeconds(180));
        when(receipts.findByMission(owner,id)).thenReturn(Optional.of(next));
        assertThatThrownBy(()->adapter.retrieve(request(owner),claimed.context())).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(catalog,lexical,vector,embedding,packages,references);
    }
    @Test void expiredWorkDeadlineFailsWhileLeaseIsLive() {
        when(receipts.databaseTime()).thenReturn(now.plusSeconds(150));
        assertThatThrownBy(()->adapter.retrieve(request(owner),claimed.context())).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(catalog,lexical,vector,embedding,packages,references);
    }
    @Test void sourceLossAfterSearchPreventsProvenanceAndEvidenceExposure() {
        when(sources.inspect(owner,mission.materials())).thenReturn(List.of(),List.of()).thenThrow(new IllegalStateException("revoked"));
        assertThatThrownBy(()->adapter.retrieve(request(owner),claimed.context())).isInstanceOf(IllegalStateException.class);
        verify(lexical).search(any());
        verifyNoInteractions(embedding,packages,references);
    }
    @Test void legacyConstructorFailsClosedForInitialRetrieval() {
        var legacy=new LearningActivityEvidenceAdapter(catalog,lexical,vector,generations,Optional.of(embedding),packages,references,
                new ActivityEvidenceRetrievalOptions(4,4,4,400,3,2));
        assertThatThrownBy(()->legacy.retrieve(request(owner),claimed.context())).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(catalog,lexical,vector,embedding,packages,references);
    }
}
