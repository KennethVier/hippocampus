package com.hippocampus.materials.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import com.hippocampus.materials.application.CompleteProcessingStage;
import com.hippocampus.materials.application.ExecuteClaimedProcessingJob;
import com.hippocampus.materials.application.MissingProcessingStageHandlerException;
import com.hippocampus.materials.application.ProcessingDispatcher;
import com.hippocampus.materials.application.ProcessingStageHandler;
import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.ProcessingJobType;
import com.hippocampus.materials.port.ProcessingJobStageCompletionRepository;

class ProcessingJobDispatchConfigurationTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AopAutoConfiguration.class,
                    JdbcTemplateAutoConfiguration.class,
                    JdbcClientAutoConfiguration.class,
                    TransactionAutoConfiguration.class,
                    ProcessingJobDispatchConfiguration.class))
            .withUserConfiguration(RequiredBeansConfiguration.class);

    @Test
    void wiresDispatcherAndTransactionProxiedCompletionWithoutHandlers() {
        runner.run(context -> {
            assertThat(context).hasNotFailed()
                    .hasSingleBean(ProcessingDispatcher.class)
                    .hasSingleBean(ProcessingJobStageCompletionRepository.class)
                    .hasSingleBean(CompleteProcessingStage.class)
                    .hasSingleBean(ExecuteClaimedProcessingJob.class)
                    .doesNotHaveBean(TaskScheduler.class)
                    .doesNotHaveBean(ThreadPoolTaskExecutor.class);
            assertThat(AopUtils.isAopProxy(context.getBean(CompleteProcessingStage.class))).isTrue();
            assertThatThrownByMissingHandler(context.getBean(ProcessingDispatcher.class));
        });
    }

    @Test
    void wiresRegisteredHandlerList() {
        runner.withUserConfiguration(HandlerConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(ProcessingStageHandler.class);
            ProcessingDispatcher dispatcher = context.getBean(ProcessingDispatcher.class);
            ClaimedProcessingJob job = new ClaimedProcessingJob(
                    java.util.UUID.randomUUID(), ProcessingJobType.MATERIAL_VALIDATE,
                    java.util.UUID.randomUUID(), "processor-v1");
            assertThat(dispatcher.dispatch(job).nextStage()).isEqualTo(ProcessingJobType.MATERIAL_EXTRACT);
        });
    }

    @Test
    void duplicateHandlerRegistrationFailsContextStartup() {
        runner.withUserConfiguration(DuplicateHandlerConfiguration.class).run(context ->
                assertThat(context).hasFailed());
    }

    @Test
    void remainsAbsentInDatasourceFreeContext() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ProcessingJobDispatchConfiguration.class))
                .run(context -> assertThat(context).hasNotFailed()
                        .doesNotHaveBean(ProcessingDispatcher.class)
                        .doesNotHaveBean(ExecuteClaimedProcessingJob.class));
    }

    private static void assertThatThrownByMissingHandler(ProcessingDispatcher dispatcher) {
        ClaimedProcessingJob job = new ClaimedProcessingJob(
                java.util.UUID.randomUUID(), ProcessingJobType.MATERIAL_VALIDATE,
                java.util.UUID.randomUUID(), "processor-v1");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> dispatcher.dispatch(job))
                .isInstanceOf(MissingProcessingStageHandlerException.class);
    }

    @Configuration(proxyBeanMethods = false)
    static class RequiredBeansConfiguration {
        @Bean
        DataSource dataSource() {
            return mock(DataSource.class);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return mock(PlatformTransactionManager.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class HandlerConfiguration {
        @Bean
        ProcessingStageHandler validationHandler() {
            ProcessingStageHandler handler = mock(ProcessingStageHandler.class);
            when(handler.jobType()).thenReturn(ProcessingJobType.MATERIAL_VALIDATE);
            return handler;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DuplicateHandlerConfiguration {
        @Bean
        ProcessingStageHandler firstValidationHandler() {
            return handler();
        }

        @Bean
        ProcessingStageHandler secondValidationHandler() {
            return handler();
        }

        private static ProcessingStageHandler handler() {
            ProcessingStageHandler handler = mock(ProcessingStageHandler.class);
            when(handler.jobType()).thenReturn(ProcessingJobType.MATERIAL_VALIDATE);
            return handler;
        }
    }
}
