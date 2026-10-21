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
import com.hippocampus.learning.application.PersistPresentationCompletion;
import com.hippocampus.learning.application.StartStudyMissionUseCase;
import com.hippocampus.learning.application.StudyMissionLearningStateAssembler;
import com.hippocampus.learning.application.SubmitActivityResponseUseCase;
import com.hippocampus.learning.port.ActivityAiTaskPort;
import com.hippocampus.learning.port.ActivityEvidencePort;
import com.hippocampus.learning.port.ActivityResponseContractRepository;
import com.hippocampus.learning.port.ActivitySourceReferenceAuthorization;
import com.hippocampus.learning.port.GeneratedArtifactRepository;
import com.hippocampus.learning.port.ResponseEvaluationPort;
import com.hippocampus.learning.port.ResponseEvidenceResolver;
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
            .withUserConfiguration(RequiredBeansConfiguration.class)
            .withBean(ResponseEvidenceResolver.class, () -> mock(ResponseEvidenceResolver.class));

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
    void bootJacksonWiresRealResponseContractsAndAvailabilityWithoutLegacyMapper() {
        UUID owner = UUID.randomUUID(), artifactId = UUID.randomUUID();
        var artifacts = mock(GeneratedArtifactRepository.class);
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration.class,
                        StudyMissionApplicationConfiguration.class))
                .withUserConfiguration(
                        com.hippocampus.learning.infrastructure.persistence.JsonActivityResponseContractRepository.class,
                        JacksonGeneratedActivityContentDecoder.class)
                .withBean(com.hippocampus.learning.port.StudyMissionStartReceiptRepository.class,
                        () -> mock(com.hippocampus.learning.port.StudyMissionStartReceiptRepository.class))
                .withBean(com.hippocampus.learning.port.InitialStudyMissionSourceAuthority.class,
                        () -> mock(com.hippocampus.learning.port.InitialStudyMissionSourceAuthority.class))
                .withBean(CurrentUser.class, () -> () -> new AuthenticatedUser(owner))
                .withBean(TopicRepository.class, () -> mock(TopicRepository.class))
                .withBean(SubtopicRepository.class, () -> mock(SubtopicRepository.class))
                .withBean(StudyMissionSourceCatalog.class, () -> mock(StudyMissionSourceCatalog.class))
                .withBean(StudyMissionRepository.class, () -> mock(StudyMissionRepository.class))
                .withBean(com.hippocampus.learning.port.FollowUpIntentRepository.class,
                        () -> mock(com.hippocampus.learning.port.FollowUpIntentRepository.class))
                .withBean(ActivitySourceReferenceAuthorization.class, () -> mock(ActivitySourceReferenceAuthorization.class))
                .withBean(GeneratedArtifactRepository.class, () -> artifacts)
                .withBean(StudentAttemptRepository.class, () -> mock(StudentAttemptRepository.class))
                .withBean(StudyMissionSourcePresentationRepository.class,
                        () -> mock(StudyMissionSourcePresentationRepository.class))
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(ObjectMapper.class)
                            .doesNotHaveBean(com.fasterxml.jackson.databind.ObjectMapper.class)
                            .hasSingleBean(com.hippocampus.learning.application.AssessmentAvailabilityResolver.class)
                            .hasSingleBean(com.hippocampus.learning.application.StudyMissionContinuationDecision.class)
                            .hasSingleBean(GetStudyMissionUseCase.class);
                    var mapper = context.getBean(ObjectMapper.class);
                    String payload = mapper.writeValueAsString(java.util.Map.of(
                            "question", "Synthetic question", "expectedAnswer", "Synthetic answer",
                            "expectedConcepts", java.util.List.of("Synthetic concept"), "correctOption", "A"));
                    org.mockito.Mockito.when(artifacts.findById(artifactId)).thenReturn(java.util.Optional.of(
                            new GeneratedArtifactRepository.GeneratedArtifact(artifactId, owner,
                                    "QUESTION", "QUESTION_GENERATION", "Synthetic question", payload,
                                    "STRICT_SOURCE", "SOURCE_GROUNDED_GENERATED", "QUESTION_GENERATION_V1", "1",
                                    "SYNTHETIC_NO_PROVIDER", "NONE", null, "VALIDATED", true, java.time.Instant.EPOCH)));
                    var repository = context.getBean(ActivityResponseContractRepository.class);
                    assertThat(repository).isInstanceOf(
                            com.hippocampus.learning.infrastructure.persistence.JsonActivityResponseContractRepository.class);
                    var parsed = repository.findValidatedForActivity(UUID.randomUUID(), artifactId, owner).orElseThrow();
                    assertThat(parsed.question()).isEqualTo("Synthetic question");
                    assertThat(parsed.expectedAnswer()).isEqualTo("Synthetic answer");
                    assertThat(parsed.expectedConcepts()).containsExactly("Synthetic concept");
                    assertThat(parsed.correctOption()).isEqualTo("A");
                    assertThat(parsed.supportsDeterministicEvaluation()).isTrue();
                });
    }

    @Test
    void wiresSubmissionAndBackendOwnedContinuationWhenRequiredPortsExist() {
        runner.run(context -> {
            assertThat(context).hasNotFailed()
                .hasSingleBean(StudyMissionLearningStateAssembler.class)
                .hasSingleBean(PersistPresentationCompletion.class)
                .hasSingleBean(SubmitActivityResponseUseCase.class)
                .hasSingleBean(ContinueStudyMissionUseCase.class)
                .hasSingleBean(com.hippocampus.learning.application.StudyMissionContinuationDecision.class);
            assertThat(AopUtils.isAopProxy(context.getBean(
                    com.hippocampus.learning.application.ChangeStudyMissionStatusUseCase.class))).isTrue();
        });
    }

    @Test
    void omitsSubmissionWhenAuthorizedEvidenceResolverIsMissing() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        AopAutoConfiguration.class,
                        TransactionAutoConfiguration.class,
                        StudyMissionApplicationConfiguration.class))
                .withUserConfiguration(RequiredBeansConfiguration.class)
                .run(context -> assertThat(context).hasNotFailed()
                        .doesNotHaveBean(SubmitActivityResponseUseCase.class)
                        .hasSingleBean(ContinueStudyMissionUseCase.class));
    }

    @Test
    void resolvesTransactionalFollowUpLifecycleAndMaterializationWithRequiredPorts() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(AopUtils.isAopProxy(context.getBean(com.hippocampus.learning.application.FollowUpIntentLifecycle.class))).isTrue();
            assertThat(context.getBean(com.hippocampus.learning.application.PersistMaterializedActivity.class)).isNotNull();
            assertThat(context.getBean(com.hippocampus.learning.application.PersistActivityResponse.class)).isNotNull();
            assertThat(context.getBean(ContinueStudyMissionUseCase.class)).isNotNull();
            assertThat(context.getBean(com.hippocampus.learning.application.ChangeStudyMissionStatusUseCase.class)).isNotNull();
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import(JacksonGeneratedActivityContentDecoder.class)
    static class RequiredBeansConfiguration {
        @Bean com.hippocampus.learning.port.StudyMissionStartReceiptRepository initialReceipts() {
            return mock(com.hippocampus.learning.port.StudyMissionStartReceiptRepository.class);
        }
        @Bean com.hippocampus.learning.port.InitialStudyMissionSourceAuthority initialSources() {
            return mock(com.hippocampus.learning.port.InitialStudyMissionSourceAuthority.class);
        }
        @Bean
        com.hippocampus.learning.port.FollowUpIntentRepository followUpIntentRepository() {
            return mock(com.hippocampus.learning.port.FollowUpIntentRepository.class);
        }
        @Bean
        com.hippocampus.learning.port.FollowUpActivityContentValidation followUpActivityContentValidation() {
            return mock(com.hippocampus.learning.port.FollowUpActivityContentValidation.class);
        }

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
