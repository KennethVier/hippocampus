package com.hippocampus.learning.api;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.hippocampus.learning.application.SubmitActivityResponseUseCase;

import jakarta.validation.Valid;

@RestController
@ConditionalOnBean(SubmitActivityResponseUseCase.class)
public class ActivityResponseController {

    private final SubmitActivityResponseUseCase submitResponse;

    public ActivityResponseController(SubmitActivityResponseUseCase submitResponse) {
        this.submitResponse = submitResponse;
    }

    @PostMapping("/api/study-missions/{missionId}/activities/{activityId}/responses")
    ActivitySubmissionResponse submit(
            @PathVariable UUID missionId,
            @PathVariable UUID activityId,
            @Valid @RequestBody ActivityResponseRequest request) {
        return ActivitySubmissionResponse.from(submitResponse.execute(
                new SubmitActivityResponseUseCase.Command(
                        missionId, activityId, request.responseText(), null, request.selectedOption())));
    }
}
