package com.hippocampus.bootstrap;

import com.hippocampus.ai.infrastructure.config.AiRequestManagerProperties;
import com.hippocampus.identity.domain.AuthenticatedUser;
import com.hippocampus.learning.application.MaterializeLearningActivityUseCase;
import com.hippocampus.learning.application.StartStudyMissionExperienceUseCase;
import com.hippocampus.learning.application.StudyMissionStartTransactions;
import com.hippocampus.learning.domain.StudyMission;
import com.hippocampus.learning.port.StudyMissionStartReceiptRepository.Receipt;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

class LearningAiCompositionConfigurationTests {
    @Test void approvedEffectiveProfileReachesExplicitClaimWithoutCallingAProvider() {
        check(new AiRequestManagerProperties(4,provider(2,32,45000,3,2000),provider(2,32,45000,3,2000)),true);
    }
    @ParameterizedTest @MethodSource("oversizedProfiles")
    void unsupportedEffectiveProfileRetainsKnownMissionWithoutClaimOrProvider(AiRequestManagerProperties profile) {
        check(profile,false);
    }
    static Stream<AiRequestManagerProperties> oversizedProfiles() {
        var valid=provider(2,32,45000,3,2000);
        return Stream.of(new AiRequestManagerProperties(5,valid,valid),
                new AiRequestManagerProperties(4,provider(3,32,45000,3,2000),valid),
                new AiRequestManagerProperties(4,provider(2,33,45000,3,2000),valid),
                new AiRequestManagerProperties(4,provider(2,32,45001,3,2000),valid),
                new AiRequestManagerProperties(4,provider(2,32,45000,4,2000),valid),
                new AiRequestManagerProperties(4,provider(2,32,45000,3,2001),valid));
    }
    private static void check(AiRequestManagerProperties properties,boolean allowed) {
        UUID owner=UUID.randomUUID(),key=UUID.randomUUID(),missionId=UUID.randomUUID();
        var transactions=mock(StudyMissionStartTransactions.class);
        var materializer=mock(MaterializeLearningActivityUseCase.class);
        var mission=mock(StudyMission.class);when(mission.id()).thenReturn(missionId);
        var receipt=Receipt.ready(owner,key,"a".repeat(64),missionId,UUID.randomUUID(),"b".repeat(64),"c".repeat(64),Instant.EPOCH);
        var view=new StudyMissionStartTransactions.View(mission,receipt,false,Instant.EPOCH,null);
        when(transactions.create(any())).thenReturn(view);
        when(transactions.claim(any(),any(),anyBoolean())).thenReturn(view);
        var useCase=new LearningAiCompositionConfiguration().startStudyMissionExperienceUseCase(
                ()->new AuthenticatedUser(owner),transactions,materializer,properties);
        var command=new StartStudyMissionExperienceUseCase.Command(key,UUID.randomUUID(),"Objective",20,List.of(UUID.randomUUID()));
        assertSame(view,useCase.start(command));
        verify(transactions,allowed?times(1):never()).claim(missionId,key,false);
        verifyNoInteractions(materializer);
    }
    private static AiRequestManagerProperties.ProviderPolicy provider(int concurrency,int queue,long millis,int attempts,long retry) {
        return new AiRequestManagerProperties.ProviderPolicy(concurrency,queue,Duration.ofMillis(millis),attempts,
                Duration.ofMillis(250),Duration.ofMillis(retry),3,Duration.ofSeconds(15));
    }
}
