package com.hippocampus.learning.api;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hippocampus.learning.application.ContinueStudyMissionUseCase;

@RestController
@ConditionalOnBean(ContinueStudyMissionUseCase.class)
public class StudyMissionContinuationController {

    private final ContinueStudyMissionUseCase continueMission;

    public StudyMissionContinuationController(ContinueStudyMissionUseCase continueMission) {
        this.continueMission = continueMission;
    }

    @PostMapping("/api/study-missions/{missionId}/activities/{activityId}/continue")
    ResponseEntity<Void> continueAfterActivity(
            @PathVariable UUID missionId,
            @PathVariable UUID activityId) {
        continueMission.execute(new ContinueStudyMissionUseCase.Command(missionId, activityId));
        return ResponseEntity.noContent().build();
    }
}
