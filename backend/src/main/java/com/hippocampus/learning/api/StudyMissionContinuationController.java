package com.hippocampus.learning.api;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.util.MultiValueMap;

import com.hippocampus.learning.application.ContinueStudyMissionUseCase;

@RestController
@ConditionalOnBean(ContinueStudyMissionUseCase.class)
public class StudyMissionContinuationController {

    private final ContinueStudyMissionUseCase continueMission;

    public StudyMissionContinuationController(ContinueStudyMissionUseCase continueMission) {
        this.continueMission = continueMission;
    }

    @PostMapping("/api/study-missions/{missionId}/activities/{activityId}/continue")
    ResponseEntity<?> continueAfterActivity(
            @PathVariable UUID missionId,
            @PathVariable UUID activityId, @RequestParam MultiValueMap<String, String> parameters) {
        var version = ContinuationContractVersion.parse(parameters);
        if (version == ContinuationContractVersion.INVALID)
            return ResponseEntity.badRequest().body(ContinuationContractVersion.invalid());
        var result = continueMission.execute(new ContinueStudyMissionUseCase.Command(
                missionId, activityId, version == ContinuationContractVersion.ONE));
        if (version == ContinuationContractVersion.LEGACY) {
            if (result == null || !"ACTIVITY_CREATED".equals(result.outcome()))
                throw new IllegalStateException("Legacy continuation did not create an activity");
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok("ACTIVITY_CREATED".equals(result.outcome())
                ? new ActivityCreated(result.outcome(), result.materializedActivityId())
                : new NoActivity(result.outcome(), result.actionType(), result.rationaleCode()));
    }

    record ActivityCreated(String outcome, UUID activityId) {}
    record NoActivity(String outcome, String actionType, String rationaleCode) {}
}
