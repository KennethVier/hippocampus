package com.hippocampus.learning.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;

import com.hippocampus.bootstrap.JacksonGeneratedActivityContentDecoder;
import com.hippocampus.identity.domain.AuthenticatedUser;
import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.learning.application.GetStudyMissionUseCase;
import com.hippocampus.learning.application.ContinueStudyMissionUseCase;
import com.hippocampus.learning.application.StartStudyMissionUseCase;
import com.hippocampus.learning.application.StudyMissionLearningStateAssembler;
import com.hippocampus.learning.application.SubmitActivityResponseUseCase;
import com.hippocampus.learning.port.ActivityAiTaskPort;
import com.hippocampus.learning.port.ActivityEvidencePort;
import com.hippocampus.learning.port.ActivityResponseContractRepository;
import com.hippocampus.learning.port.ActivitySourceReferenceAuthorization;
import com.hippocampus.learning.port.GeneratedArtifactRepository;
import com.hippocampus.learning.port.ResponseEvaluationPort;
import com.hippocampus.learning.port.StudyMissionRepository;
import com.hippocampus.learning.port.StudyMissionSourceCatalog;
import com.hippocampus.learning.port.StudyMissionSourcePresentationRepository;
import com.hippocampus.learning.port.SubtopicRepository;
import com.hippocampus.learning.port.TopicRepository;
import com.hippocampus.progress.port.StudentAttemptRepository;

import tools.jackson.databind.ObjectMapper;

class StudyMissionApplicationConfigurationTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AopAutoConfiguration.class,
                    TransactionAutoConfiguration.class,
                    StudyMissionApplicationConfiguration.class))
            .withUserConfiguration(RequiredBeansConfiguration.class);

    @Test
    void resolvesTransactionProxiedStartStudyMissionUseCase() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(StartStudyMissionUseCase.class);
            StartStudyMissionUseCase useCase = context.getBean(StartStudyMissionUseCase.class);
            assertThat(AopUtils.isAopProxy(useCase)).isTrue();
            assertThat(AopUtils.isCglibProxy(useCase)).isTrue();
        });
    }

    @Test
    void wiresMissionReadWithBootJacksonWithoutJacksonTwoOrAiComposition() {
        runner.run(context -> {
            assertThat(context).hasNotFailed()
                    .hasSingleBean(GetStudyMissionUseCase.class)
                    .hasSingleBean(JacksonGeneratedActivityContentDecoder.class)
                    .hasSingleBean(ObjectMapper.class)
                    .doesNotHaveBean(com.fasterxml.jackson.databind.ObjectMapper.class);

            assertThat(context.getBean(GetStudyMissionUseCase.class)).isNotNull();
        });
    }

    @Test
    void wiresSubmissionAndBackendOwnedContinuationWhenRequiredPortsExist() {
        runner.run(context -> assertThat(context).hasNotFailed()
                .hasSingleBean(StudyMissionLearningStateAssembler.class)
                .hasSingleBean(SubmitActivityResponseUseCase.class)
                .hasSingleBean(ContinueStudyMissionUseCase.class));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(JacksonGeneratedActivityContentDecoder.class)
    static class RequiredBeansConfiguration {
        @Bean
        PlatformTransactionManager transactionManager() {
            return mock(PlatformTransactionManager.class);
        }

        @Bean
        CurrentUser currentUser() {
            return () -> new AuthenticatedUser(UUID.randomUUID());
        }

        @Bean
        TopicRepository topicRepository() {
            return mock(TopicRepository.class);
        }

        @Bean
        SubtopicRepository subtopicRepository() {
            return mock(SubtopicRepository.class);
        }

        @Bean
        StudyMissionSourceCatalog studyMissionSourceCatalog() {
            return mock(StudyMissionSourceCatalog.class);
        }

        @Bean
        StudyMissionRepository studyMissionRepository() {
            return mock(StudyMissionRepository.class);
        }

        @Bean
        GeneratedArtifactRepository generatedArtifactRepository() {
            return mock(GeneratedArtifactRepository.class);
        }

        @Bean
        ActivitySourceReferenceAuthorization activitySourceReferenceAuthorization() {
            return mock(ActivitySourceReferenceAuthorization.class);
        }

        @Bean
        ActivityEvidencePort activityEvidencePort() {
            return mock(ActivityEvidencePort.class);
        }

        @Bean
        ActivityAiTaskPort activityAiTaskPort() {
            return mock(ActivityAiTaskPort.class);
        }

        @Bean
        StudentAttemptRepository studentAttemptRepository() {
            return mock(StudentAttemptRepository.class);
        }

        @Bean
        ActivityResponseContractRepository activityResponseContractRepository() {
            return mock(ActivityResponseContractRepository.class);
        }

        @Bean
        ResponseEvaluationPort responseEvaluationPort() {
            return mock(ResponseEvaluationPort.class);
        }

        @Bean
        StudyMissionSourcePresentationRepository studyMissionSourcePresentationRepository() {
            return mock(StudyMissionSourcePresentationRepository.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
