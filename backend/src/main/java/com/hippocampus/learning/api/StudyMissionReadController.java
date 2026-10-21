package com.hippocampus.learning.api;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.util.MultiValueMap;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;

import com.hippocampus.learning.application.GetStudyMissionUseCase;

@RestController
@ConditionalOnBean(GetStudyMissionUseCase.class)
public class StudyMissionReadController {

    private final GetStudyMissionUseCase query;

    public StudyMissionReadController(GetStudyMissionUseCase query) {
        this.query = query;
    }

    @GetMapping("/api/study-missions/{missionId}")
    ResponseEntity<?> get(@PathVariable UUID missionId, @RequestParam MultiValueMap<String, String> parameters) {
        var initialVersion=InitialActivityContractVersion.parse(parameters);
        if(initialVersion==InitialActivityContractVersion.INVALID) throw new com.hippocampus.learning.application.StudyMissionStartException(
            com.hippocampus.learning.application.StudyMissionStartException.Kind.INVALID_INITIAL_CONTRACT,null);
        var version = ContinuationContractVersion.parse(parameters);
        if (version == ContinuationContractVersion.INVALID)
            return ResponseEntity.badRequest().body(ContinuationContractVersion.invalid());
        if(initialVersion==InitialActivityContractVersion.ONE) {
            var initial=query.execute(missionId,version==ContinuationContractVersion.ONE,true);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                StudyMissionWithInitialActivityResponse.from(initial,version==ContinuationContractVersion.ONE));
        }
        var result = version == ContinuationContractVersion.ONE ? query.execute(missionId, true) : query.execute(missionId);
        Object body = version == ContinuationContractVersion.ONE
                ? StudyMissionWithContinuationResponse.from(result) : StudyMissionResponse.from(result);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
