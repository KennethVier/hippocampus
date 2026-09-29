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
import org.springframework.transaction.PlatformTransactionManager;

import com.hippocampus.identity.domain.AuthenticatedUser;
import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.learning.application.StartStudyMissionUseCase;
import com.hippocampus.learning.port.StudyMissionRepository;
import com.hippocampus.learning.port.StudyMissionSourceCatalog;
import com.hippocampus.learning.port.SubtopicRepository;
import com.hippocampus.learning.port.TopicRepository;

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

    @Configuration(proxyBeanMethods = false)
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
    }
}
