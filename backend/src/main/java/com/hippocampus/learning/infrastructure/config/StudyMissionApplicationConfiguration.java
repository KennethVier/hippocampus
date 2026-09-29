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
import com.hippocampus.learning.application.MaterializeLearningActivityUseCase;
import com.hippocampus.learning.application.PersistMaterializedActivity;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningEngine;
import com.hippocampus.learning.domain.LearningPolicyConfiguration;
import com.hippocampus.learning.port.StudyMissionRepository;
import com.hippocampus.learning.port.ActivityAiTaskPort;
import com.hippocampus.learning.port.ActivityEvidencePort;
import com.hippocampus.learning.port.ActivitySourceReferenceAuthorization;
import com.hippocampus.learning.port.GeneratedArtifactRepository;
import com.hippocampus.learning.port.StudyMissionSourceCatalog;
import com.hippocampus.learning.port.SubtopicRepository;
import com.hippocampus.learning.port.TopicRepository;

@AutoConfiguration
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
        return new LearningEngine(new LearningPolicyConfiguration(2, 3, 4, 5, 2, 3, durations));
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
}
