package com.hippocampus.learning.api;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.hippocampus.learning.application.GetStudyMissionUseCase;

@RestController
@ConditionalOnBean(GetStudyMissionUseCase.class)
public class StudyMissionReadController {

    private final GetStudyMissionUseCase query;

    public StudyMissionReadController(GetStudyMissionUseCase query) {
        this.query = query;
    }

    @GetMapping("/api/study-missions/{missionId}")
    StudyMissionResponse get(@PathVariable UUID missionId) {
        return StudyMissionResponse.from(query.execute(missionId));
    }
}
