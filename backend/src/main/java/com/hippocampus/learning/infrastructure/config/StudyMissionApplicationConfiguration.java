package com.hippocampus.learning.infrastructure.config;

import java.time.Clock;
import java.util.EnumMap;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;

import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.learning.application.StartStudyMissionUseCase;
import com.hippocampus.learning.application.ChangeStudyMissionStatusUseCase;
import com.hippocampus.learning.application.ContinueStudyMissionUseCase;
import com.hippocampus.learning.application.GetStudyMissionUseCase;
import com.hippocampus.learning.application.MaterializeLearningActivityUseCase;
import com.hippocampus.learning.application.PersistActivityResponse;
import com.hippocampus.learning.application.PersistMaterializedActivity;
import com.hippocampus.learning.application.PersistPresentationCompletion;
import com.hippocampus.learning.application.SubmitActivityResponseUseCase;
import com.hippocampus.learning.application.StudyMissionLearningStateAssembler;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningEngine;
import com.hippocampus.learning.domain.LearningPolicyConfiguration;
import com.hippocampus.learning.domain.policy.MissionStateMachinePolicy;
import com.hippocampus.learning.port.StudyMissionRepository;
import com.hippocampus.learning.port.ActivityAiTaskPort;
import com.hippocampus.learning.port.ActivityEvidencePort;
import com.hippocampus.learning.port.ActivitySourceReferenceAuthorization;
import com.hippocampus.learning.port.ActivityResponseContractRepository;
import com.hippocampus.learning.port.GeneratedArtifactRepository;
import com.hippocampus.learning.port.GeneratedActivityContentDecoder;
import com.hippocampus.learning.port.ResponseEvaluationPort;
import com.hippocampus.learning.port.StudyMissionSourceCatalog;
import com.hippocampus.learning.port.StudyMissionSourcePresentationRepository;
import com.hippocampus.learning.port.SubtopicRepository;
import com.hippocampus.learning.port.TopicRepository;
import com.hippocampus.progress.port.StudentAttemptRepository;
import com.hippocampus.progress.domain.EvidenceProjector;
import com.hippocampus.progress.port.EvidenceEventRepository;
import com.hippocampus.progress.port.LearningEvidenceRepository;

@AutoConfiguration(afterName =
        "com.hippocampus.materials.infrastructure.config.SourceReferenceConfiguration")
@ConditionalOnBean({
        CurrentUser.class,
        TopicRepository.class,
        SubtopicRepository.class,
        StudyMissionSourceCatalog.class,
        StudyMissionRepository.class
})
public class StudyMissionApplicationConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock studyMissionClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    LearningEngine learningEngine() {
        EnumMap<LearningActionType, Integer> durations = new EnumMap<>(LearningActionType.class);
        for (LearningActionType actionType : LearningActionType.values()) {
            durations.put(actionType, 2);
        }
        durations.put(LearningActionType.UNDERSTAND, 8);
        durations.put(LearningActionType.RETRIEVE, 4);
        durations.put(LearningActionType.CONNECT, 8);
        durations.put(LearningActionType.APPLY, 16);
        durations.put(LearningActionType.REFLECT, 2);
        return new LearningEngine(new LearningPolicyConfiguration(
                2, 3, 4, 5, 2,
                LearningPolicyConfiguration.V1_DUPLICATE_HISTORY_WINDOW,
                durations));
    }

    @Bean
    @ConditionalOnMissingBean
    MissionStateMachinePolicy missionStateMachinePolicy() {
        return new MissionStateMachinePolicy();
    }

    @Bean
    @ConditionalOnMissingBean
    StudyMissionLearningStateAssembler studyMissionLearningStateAssembler() {
        return new StudyMissionLearningStateAssembler();
    }

    @Bean
    @ConditionalOnMissingBean
    EvidenceProjector evidenceProjector() {
        return new EvidenceProjector();
    }

    @Bean
    @Lazy
    StartStudyMissionUseCase startStudyMissionUseCase(
            CurrentUser currentUser,
            TopicRepository topics,
            SubtopicRepository subtopics,
            StudyMissionSourceCatalog sourceCatalog,
            StudyMissionRepository missions,
            LearningEngine learningEngine,
            Clock studyMissionClock) {
        return new StartStudyMissionUseCase(
                currentUser,
                topics,
                subtopics,
                sourceCatalog,
                missions,
                learningEngine,
                studyMissionClock);
    }

    @Bean
    @Lazy
    @ConditionalOnMissingBean
    @ConditionalOnBean({GeneratedArtifactRepository.class, ActivitySourceReferenceAuthorization.class})
    PersistMaterializedActivity persistMaterializedActivity(
            StudyMissionRepository missions,
            GeneratedArtifactRepository artifacts,
            ActivitySourceReferenceAuthorization sourceAuthorization) {
        return new PersistMaterializedActivity(missions, artifacts, sourceAuthorization);
    }

    @Bean
    @Lazy
    @ConditionalOnMissingBean
    @ConditionalOnBean({ActivityEvidencePort.class, ActivityAiTaskPort.class,
            PersistMaterializedActivity.class})
    MaterializeLearningActivityUseCase materializeLearningActivityUseCase(
            CurrentUser currentUser,
            StudyMissionRepository missions,
            ActivityEvidencePort evidencePort,
            ActivityAiTaskPort aiTaskPort,
            PersistMaterializedActivity persistence,
            Clock studyMissionClock) {
        return new MaterializeLearningActivityUseCase(
                currentUser, missions, evidencePort, aiTaskPort, persistence, studyMissionClock);
    }

    @Bean
    @Lazy
    @ConditionalOnMissingBean
    @ConditionalOnBean({StudentAttemptRepository.class, EvidenceEventRepository.class,
            LearningEvidenceRepository.class})
    PersistActivityResponse persistActivityResponse(
            StudyMissionRepository missions,
            StudentAttemptRepository attempts,
            EvidenceEventRepository evidenceEvents,
            LearningEvidenceRepository learningEvidence,
            EvidenceProjector evidenceProjector) {
        return new PersistActivityResponse(
                missions, attempts, evidenceEvents, learningEvidence, evidenceProjector);
    }

    @Bean
    @Lazy
    @ConditionalOnMissingBean
    PersistPresentationCompletion persistPresentationCompletion(StudyMissionRepository missions) {
        return new PersistPresentationCompletion(missions);
    }

    @Bean
    @Lazy
    @ConditionalOnMissingBean
    @ConditionalOnBean({StudentAttemptRepository.class, ActivityResponseContractRepository.class,
            ResponseEvaluationPort.class, PersistActivityResponse.class})
    SubmitActivityResponseUseCase submitActivityResponseUseCase(
            CurrentUser currentUser,
            StudyMissionRepository missions,
            StudentAttemptRepository attempts,
            ActivityResponseContractRepository contracts,
            ResponseEvaluationPort responseEvaluation,
            LearningEngine learningEngine,
            StudyMissionLearningStateAssembler learningStateAssembler,
            PersistActivityResponse persistence,
            Clock studyMissionClock) {
        return new SubmitActivityResponseUseCase(
                currentUser, missions, attempts, contracts, responseEvaluation,
                learningEngine, learningStateAssembler, persistence, studyMissionClock);
    }

    @Bean
    @Lazy
    @ConditionalOnMissingBean
    @ConditionalOnBean({StudentAttemptRepository.class, ActivityResponseContractRepository.class,
            MaterializeLearningActivityUseCase.class, PersistPresentationCompletion.class})
    ContinueStudyMissionUseCase continueStudyMissionUseCase(
            CurrentUser currentUser,
            StudyMissionRepository missions,
            StudentAttemptRepository attempts,
            ActivityResponseContractRepository contracts,
            LearningEngine learningEngine,
            StudyMissionLearningStateAssembler learningStateAssembler,
            MaterializeLearningActivityUseCase materializeLearningActivity,
            PersistPresentationCompletion persistPresentationCompletion,
            Clock studyMissionClock) {
        return new ContinueStudyMissionUseCase(
                currentUser, missions, attempts, contracts, learningEngine, learningStateAssembler,
                materializeLearningActivity, persistPresentationCompletion, studyMissionClock);
    }

    @Bean
    @Lazy
    @ConditionalOnMissingBean
    ChangeStudyMissionStatusUseCase changeStudyMissionStatusUseCase(
            CurrentUser currentUser,
            StudyMissionRepository missions,
            MissionStateMachinePolicy stateMachine,
            Clock studyMissionClock) {
        return new ChangeStudyMissionStatusUseCase(
                currentUser, missions, stateMachine, studyMissionClock);
    }

    @Bean
    @Lazy
    @ConditionalOnMissingBean
    @ConditionalOnBean({
            GeneratedArtifactRepository.class,
            GeneratedActivityContentDecoder.class,
            StudyMissionSourcePresentationRepository.class
    })
    GetStudyMissionUseCase getStudyMissionUseCase(
            CurrentUser currentUser,
            StudyMissionRepository missions,
            GeneratedArtifactRepository artifacts,
            GeneratedActivityContentDecoder contentDecoder,
            StudyMissionSourcePresentationRepository sourcePresentations) {
        return new GetStudyMissionUseCase(
                currentUser, missions, artifacts, contentDecoder, sourcePresentations);
    }
}
