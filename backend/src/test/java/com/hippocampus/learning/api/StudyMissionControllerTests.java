package com.hippocampus.learning.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hippocampus.learning.application.ChangeStudyMissionStatusUseCase;

class StudyMissionControllerTests {

    @Test
    void lifecycleEndpointsDelegateOnlyToApplicationUseCaseAndReturnContinuity() {
        ChangeStudyMissionStatusUseCase useCase = mock(ChangeStudyMissionStatusUseCase.class);
        UUID missionId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        ChangeStudyMissionStatusUseCase.Result active = result(missionId, activityId, versionId, "ACTIVE");
        ChangeStudyMissionStatusUseCase.Result paused = result(missionId, activityId, versionId, "PAUSED");
        ChangeStudyMissionStatusUseCase.Result stopped = result(missionId, activityId, versionId, "STOPPED");
        when(useCase.execute(missionId, ChangeStudyMissionStatusUseCase.Command.PAUSE)).thenReturn(paused);
        when(useCase.execute(missionId, ChangeStudyMissionStatusUseCase.Command.RESUME)).thenReturn(active);
        when(useCase.execute(missionId, ChangeStudyMissionStatusUseCase.Command.STOP)).thenReturn(stopped);
        StudyMissionController controller = new StudyMissionController(useCase);

        StudyMissionLifecycleResponse pause = controller.pause(missionId);
        StudyMissionLifecycleResponse resume = controller.resume(missionId);
        StudyMissionLifecycleResponse stop = controller.stop(missionId);

        assertThat(pause.status()).isEqualTo("PAUSED");
        assertThat(resume.status()).isEqualTo("ACTIVE");
        assertThat(stop.status()).isEqualTo("STOPPED");
        assertThat(resume.currentActivityId()).isEqualTo(activityId);
        assertThat(resume.sourceScopes()).singleElement()
                .extracting(StudyMissionLifecycleResponse.SourceScope::materialVersionId)
                .isEqualTo(versionId);
        verify(useCase).execute(missionId, ChangeStudyMissionStatusUseCase.Command.PAUSE);
        verify(useCase).execute(missionId, ChangeStudyMissionStatusUseCase.Command.RESUME);
        verify(useCase).execute(missionId, ChangeStudyMissionStatusUseCase.Command.STOP);
    }

    private static ChangeStudyMissionStatusUseCase.Result result(
            UUID missionId, UUID activityId, UUID versionId, String status) {
        Instant now = Instant.parse("2026-09-29T13:00:00Z");
        return new ChangeStudyMissionStatusUseCase.Result(
                missionId, status, activityId,
                List.of(new ChangeStudyMissionStatusUseCase.SourceScope(
                        UUID.randomUUID(), versionId, null)),
                now, null, "STOPPED".equals(status) ? now : null, now);
    }
}
