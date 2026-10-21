package com.hippocampus.learning.application;

import java.time.Instant;
import java.util.*;
import com.hippocampus.learning.domain.*;
import com.hippocampus.learning.port.*;
import com.hippocampus.progress.domain.StudentAttempt;
import com.hippocampus.progress.port.StudentAttemptRepository;

/** Builds an internally consistent fixture command; it supplies no grading authority. */
public final class ResponsePersistenceTestCommand {
    private ResponsePersistenceTestCommand() {}
    public static PersistActivityResponse.Command create(StudyMission mission, LearningActivity current,
            StudentAttemptRepository attempts, StudyMissionLearningStateAssembler assembler, LearningEngine engine,
            StudentAttempt proposed, Instant now) {
        Map<UUID,List<StudentAttempt>> history=new LinkedHashMap<>();
        for (LearningActivity a:mission.activities()) history.put(a.id(),attempts.findOwnedByActivity(a.id(),mission.userId()));
        var action=engine.decide(assembler.assembleForSubmission(mission,current,history,proposed,now));
        var stage=SubmitActivityResponseUseCase.stageFor(action,mission.learningState());
        boolean complete=action.actionType()==LearningActionType.COMPLETE;
        boolean ungraded=proposed.evaluationStatus().equals("UNGRADED");
        var contract=ungraded?null:assembler.eligibility().deterministic(mission,current).orElseThrow().response();
        boolean correct=proposed.evaluationStatus().equals("CORRECT");
        var receipt=new SubmissionReceipt(mission.id(),current.id(),proposed.id(),proposed.evaluationStatus(),
                ungraded||!correct?List.of():contract.expectedConcepts(),ungraded||correct?List.of():contract.expectedConcepts(),
                List.of(),ungraded?"Your response was saved, but it could not be graded reliably. You can continue safely.":contract.feedback(),
                ungraded?"EVALUATION_UNRESOLVED":null,complete?"COMPLETED":mission.status().name(),stage.name(),now.toString(),
                !complete&&SubmitActivityResponseUseCase.isMaterializable(action.actionType()));
        var saved=new StudentAttempt(proposed.id(),proposed.userId(),proposed.learningActivityId(),proposed.attemptNumber(),
                proposed.responseText(),proposed.responsePayload(),now,proposed.evaluationStatus(),proposed.evaluationArtifactId(),
                proposed.deterministicResult(),now,proposed.requestId()==null?UUID.randomUUID():proposed.requestId(),
                proposed.limitationCode(),receipt.toJson());
        var existing=history.getOrDefault(current.id(),List.of());
        return new PersistActivityResponse.Command(mission.id(),mission.userId(),new PersistActivityResponse.Snapshot(
                mission.updatedAt(),current.id(),current.status(),current.completedAt(),existing.size(),
                existing.stream().mapToInt(StudentAttempt::attemptNumber).max().orElse(0)),saved,stage,complete,now,action,
                ungraded?null:new FollowUpIntent.Basis("DETERMINISTIC_MCQ",List.of()));
    }
}
