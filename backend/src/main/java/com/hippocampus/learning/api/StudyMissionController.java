package com.hippocampus.learning.api;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hippocampus.learning.application.ChangeStudyMissionStatusUseCase;

@RestController
@ConditionalOnBean(ChangeStudyMissionStatusUseCase.class)
public class StudyMissionController {

    private final ChangeStudyMissionStatusUseCase lifecycle;

    public StudyMissionController(ChangeStudyMissionStatusUseCase lifecycle) {
        this.lifecycle = lifecycle;
    }

    @PostMapping("/api/study-missions/{missionId}/pause")
    StudyMissionLifecycleResponse pause(@PathVariable UUID missionId) {
        return StudyMissionLifecycleResponse.from(
                lifecycle.execute(missionId, ChangeStudyMissionStatusUseCase.Command.PAUSE));
    }

    @PostMapping("/api/study-missions/{missionId}/resume")
    StudyMissionLifecycleResponse resume(@PathVariable UUID missionId) {
        return StudyMissionLifecycleResponse.from(
                lifecycle.execute(missionId, ChangeStudyMissionStatusUseCase.Command.RESUME));
    }

    @PostMapping("/api/study-missions/{missionId}/stop")
    StudyMissionLifecycleResponse stop(@PathVariable UUID missionId) {
        return StudyMissionLifecycleResponse.from(
                lifecycle.execute(missionId, ChangeStudyMissionStatusUseCase.Command.STOP));
    }
}
