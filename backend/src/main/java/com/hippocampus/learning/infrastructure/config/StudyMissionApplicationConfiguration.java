package com.hippocampus.learning.infrastructure.config;

import java.time.Clock;
import java.util.EnumMap;
import org.springframework.beans.factory.ObjectProvider;
import com.hippocampus.learning.application.AssessmentAvailabilityResolver;
import com.hippocampus.learning.application.StudyMissionContinuationDecision;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;

import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.learning.application.StartStudyMissionUseCase;
import com.hippocampus.learning.application.FollowUpIntentLifecycle;
import com.hippocampus.learning.port.FollowUpIntentRepository;
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
import com.hippocampus.learning.port.ResponseEvidenceResolver;
import com.hippocampus.learning.port.StudyMissionSourceCatalog;
import com.hippocampus.learning.port.StudyMissionSourcePresentationRepository;
import com.hippocampus.learning.port.SubtopicRepository;
import com.hippocampus.learning.port.TopicRepository;
import com.hippocampus.progress.port.StudentAttemptRepository;

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
    @Lazy
    FollowUpIntentLifecycle followUpIntentLifecycle(FollowUpIntentRepository intents,
            StudyMissionRepository missions, StudentAttemptRepository attempts,
            ActivitySourceReferenceAuthorization sources, LearningEngine learningEngine,
            StudyMissionLearningStateAssembler assembler, AssessmentAvailabilityResolver availability) {
        return new FollowUpIntentLifecycle(intents,missions,attempts,sources,learningEngine,assembler,availability);
    }

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
    @Lazy
    com.hippocampus.learning.application.AttemptEvidenceEligibility attemptEvidenceEligibility(
            ActivityResponseContractRepository contracts, ActivitySourceReferenceAuthorization sources,
            GeneratedArtifactRepository artifacts) {
        return new com.hippocampus.learning.application.AttemptEvidenceEligibility(contracts,sources,artifacts);
    }

    @Bean
    @ConditionalOnMissingBean
    StudyMissionLearningStateAssembler studyMissionLearningStateAssembler(
            com.hippocampus.learning.application.AttemptEvidenceEligibility eligibility) {
        return new StudyMissionLearningStateAssembler(eligibility);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({ActivityResponseContractRepository.class, StudentAttemptRepository.class,
            StudyMissionSourcePresentationRepository.class, GeneratedArtifactRepository.class})
    AssessmentAvailabilityResolver assessmentAvailabilityResolver(ActivityResponseContractRepository contracts,
            StudentAttemptRepository attempts, StudyMissionSourcePresentationRepository sources,
            GeneratedArtifactRepository artifacts, com.hippocampus.learning.application.AttemptEvidenceEligibility eligibility) {
        return new AssessmentAvailabilityResolver(contracts, attempts, sources, artifacts, eligibility);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({AssessmentAvailabilityResolver.class, StudyMissionSourcePresentationRepository.class})
    StudyMissionContinuationDecision studyMissionContinuationDecision(AssessmentAvailabilityResolver availability,
            LearningEngine engine, StudyMissionLearningStateAssembler assembler,
            StudyMissionSourcePresentationRepository sources, FollowUpIntentLifecycle followUps) {
        return new StudyMissionContinuationDecision(availability, engine, assembler, sources, followUps);
    }

    @Bean
    com.hippocampus.learning.application.InitialStudyMissionStateAssembler initialStudyMissionStateAssembler() {
        return new com.hippocampus.learning.application.InitialStudyMissionStateAssembler();
    }

    @Bean
    @Lazy
    com.hippocampus.learning.application.InitialStudyMissionAdmission initialStudyMissionAdmission(
            com.hippocampus.learning.port.StudyMissionStartReceiptRepository receipts,
            com.hippocampus.learning.port.InitialStudyMissionSourceAuthority sources,
            com.hippocampus.learning.application.InitialStudyMissionStateAssembler assembler,LearningEngine engine) {
        return new com.hippocampus.learning.application.InitialStudyMissionAdmission(receipts,sources,assembler,engine);
    }

    @Bean
    @Lazy
    com.hippocampus.learning.application.StudyMissionStartTransactions studyMissionStartTransactions(
            CurrentUser currentUser,StartStudyMissionUseCase start,StudyMissionRepository missions,
            com.hippocampus.learning.port.StudyMissionStartReceiptRepository receipts,
            com.hippocampus.learning.port.InitialStudyMissionSourceAuthority sources,
            com.hippocampus.learning.application.InitialStudyMissionStateAssembler assembler,LearningEngine engine) {
        return new com.hippocampus.learning.application.StudyMissionStartTransactions(currentUser,start,missions,receipts,sources,assembler,engine);
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
    @ConditionalOnBean({GeneratedArtifactRepository.class, ActivitySourceReferenceAuthorization.class,
            ActivityResponseContractRepository.class})
    PersistMaterializedActivity persistMaterializedActivity(
            StudyMissionRepository missions,
            GeneratedArtifactRepository artifacts,
            ActivitySourceReferenceAuthorization sourceAuthorization, FollowUpIntentLifecycle followUps,
            com.hippocampus.learning.port.FollowUpActivityContentValidation followUpValidation, ActivityResponseContractRepository contracts,
            StudyMissionContinuationDecision continuation,
            com.hippocampus.learning.application.InitialStudyMissionAdmission initialAdmission) {
        return new PersistMaterializedActivity(missions, artifacts, sourceAuthorization, followUps, followUpValidation, contracts,continuation,initialAdmission);
    }

    @Bean
    @Lazy
    @ConditionalOnMissingBean
    @ConditionalOnBean({ActivityEvidencePort.class, ActivityAiTaskPort.class,
            PersistMaterializedActivity.class, AssessmentAvailabilityResolver.class})
    MaterializeLearningActivityUseCase materializeLearningActivityUseCase(
            CurrentUser currentUser,
            StudyMissionRepository missions,
            ActivityEvidencePort evidencePort,
            ActivityAiTaskPort aiTaskPort,
            PersistMaterializedActivity persistence,
            Clock studyMissionClock, AssessmentAvailabilityResolver availability,
            com.hippocampus.learning.application.StudyMissionStartTransactions initialTransactions) {
        return new MaterializeLearningActivityUseCase(
                currentUser, missions, evidencePort, aiTaskPort, persistence, studyMissionClock, availability,initialTransactions);
    }

    @Bean
    @Lazy
    @ConditionalOnMissingBean
    @ConditionalOnBean(StudentAttemptRepository.class)
    PersistActivityResponse persistActivityResponse(
            StudyMissionRepository missions,
            StudentAttemptRepository attempts, FollowUpIntentLifecycle followUps,
            StudyMissionLearningStateAssembler assembler, LearningEngine engine) {
        return new PersistActivityResponse(missions, attempts, followUps, assembler, engine);
    }

    @Bean
    @Lazy
    @ConditionalOnMissingBean
    PersistPresentationCompletion persistPresentationCompletion(StudyMissionRepository missions,
            ActivityResponseContractRepository contracts, ActivitySourceReferenceAuthorization sources,
            GeneratedArtifactRepository artifacts) {
        return new PersistPresentationCompletion(missions, contracts, sources, artifacts);
    }

    @Bean
    @Lazy
    @ConditionalOnMissingBean
    @ConditionalOnBean({StudentAttemptRepository.class, ActivityResponseContractRepository.class,
            ResponseEvaluationPort.class, ResponseEvidenceResolver.class,
            PersistActivityResponse.class})
    SubmitActivityResponseUseCase submitActivityResponseUseCase(
            CurrentUser currentUser,
            StudyMissionRepository missions,
            StudentAttemptRepository attempts,
            ActivityResponseContractRepository contracts,
            ResponseEvaluationPort responseEvaluation,
            ResponseEvidenceResolver evidenceResolver,
            LearningEngine learningEngine,
            StudyMissionLearningStateAssembler learningStateAssembler,
            PersistActivityResponse persistence,
            Clock studyMissionClock) {
        return new SubmitActivityResponseUseCase(
                currentUser, missions, attempts, contracts, responseEvaluation, evidenceResolver,
                learningEngine, learningStateAssembler, persistence, studyMissionClock);
    }

    @Bean
    @Lazy
    @ConditionalOnMissingBean
    @ConditionalOnBean({StudentAttemptRepository.class, ActivityResponseContractRepository.class,
            MaterializeLearningActivityUseCase.class, PersistPresentationCompletion.class,
            StudyMissionContinuationDecision.class})
    ContinueStudyMissionUseCase continueStudyMissionUseCase(
            CurrentUser currentUser,
            StudyMissionRepository missions,
            ActivityResponseContractRepository contracts,
            MaterializeLearningActivityUseCase materializeLearningActivity,
            PersistPresentationCompletion persistPresentationCompletion,
            Clock studyMissionClock, StudyMissionContinuationDecision continuation,
            ChangeStudyMissionStatusUseCase statusChanges, FollowUpIntentLifecycle followUps) {
        return new ContinueStudyMissionUseCase(
                currentUser, missions, contracts, continuation, materializeLearningActivity,
                persistPresentationCompletion, statusChanges, studyMissionClock, followUps);
    }

    @Bean
    @Lazy
    @ConditionalOnMissingBean
    ChangeStudyMissionStatusUseCase changeStudyMissionStatusUseCase(
            CurrentUser currentUser,
            StudyMissionRepository missions,
            MissionStateMachinePolicy stateMachine,
            Clock studyMissionClock, ObjectProvider<StudyMissionContinuationDecision> continuation,
            ObjectProvider<ActivitySourceReferenceAuthorization> sourceAuthorization, FollowUpIntentLifecycle followUps,
            com.hippocampus.learning.application.StudyMissionStartTransactions initialTransactions) {
        return new ChangeStudyMissionStatusUseCase(
                currentUser, missions, stateMachine, studyMissionClock,
                continuation.getIfAvailable(), sourceAuthorization.getIfAvailable(), followUps, initialTransactions);
    }

    @Bean
    @Lazy
    @ConditionalOnMissingBean
    @ConditionalOnBean({
            GeneratedArtifactRepository.class,
            GeneratedActivityContentDecoder.class,
            StudyMissionSourcePresentationRepository.class,
            StudentAttemptRepository.class
    })
    GetStudyMissionUseCase getStudyMissionUseCase(
            CurrentUser currentUser,
            StudyMissionRepository missions,
            GeneratedArtifactRepository artifacts,
            GeneratedActivityContentDecoder contentDecoder,
            StudyMissionSourcePresentationRepository sourcePresentations,
            StudentAttemptRepository attempts, ObjectProvider<StudyMissionContinuationDecision> continuation,
            Clock studyMissionClock,
            com.hippocampus.learning.port.StudyMissionStartReceiptRepository receipts,
            com.hippocampus.learning.port.InitialStudyMissionSourceAuthority initialSources) {
        return new GetStudyMissionUseCase(
                currentUser, missions, artifacts, contentDecoder, sourcePresentations, attempts,
                continuation.getIfAvailable(), studyMissionClock,receipts,initialSources);
    }
}
