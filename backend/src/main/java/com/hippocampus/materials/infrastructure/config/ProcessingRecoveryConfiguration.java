package com.hippocampus.materials.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.PlatformTransactionManager;
import com.hippocampus.materials.application.*;
import com.hippocampus.materials.infrastructure.persistence.JdbcProcessingJobExecutionRepository;
import com.hippocampus.materials.infrastructure.scheduling.ProcessingJobPoller;
import com.hippocampus.materials.infrastructure.scheduling.SpringProcessingHeartbeatMonitor;
import com.hippocampus.materials.port.*;

@AutoConfiguration(after = {ProcessingJobClaimConfiguration.class, ProcessingJobDispatchConfiguration.class})
@ConditionalOnBean({JdbcClient.class, PlatformTransactionManager.class})
@EnableConfigurationProperties(ProcessingRecoveryProperties.class)
@EnableScheduling
public class ProcessingRecoveryConfiguration {
    @Bean ProcessingJobExecutionRepository processingJobExecutionRepository(JdbcClient jdbc) {
        return new JdbcProcessingJobExecutionRepository(jdbc);
    }
    @Bean UpdateProcessingJobExecution updateProcessingJobExecution(ProcessingJobExecutionRepository jobs) {
        return new UpdateProcessingJobExecution(jobs);
    }
    @Bean ReportProcessingJobProgress reportProcessingJobProgress(UpdateProcessingJobExecution updates) {
        return new ReportProcessingJobProgress(updates);
    }
    @Bean ProcessingRetryPolicy processingRetryPolicy(ProcessingRecoveryProperties properties) {
        return new ProcessingRetryPolicy(properties.initialRetryDelay(), properties.maximumRetryDelay());
    }
    @Bean ProcessingFailureClassifier processingFailureClassifier() { return new ProcessingFailureClassifier(); }
    @Bean FinalizeProcessingFailure finalizeProcessingFailure(ProcessingJobExecutionRepository jobs,
            ProcessingRetryPolicy retries) { return new FinalizeProcessingFailure(jobs, retries); }
    @Bean(name = "taskScheduler") TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("processing-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }
    @Bean ProcessingHeartbeatMonitor processingHeartbeatMonitor(TaskScheduler taskScheduler,
            UpdateProcessingJobExecution updates, ProcessingRecoveryProperties properties) {
        return new SpringProcessingHeartbeatMonitor(taskScheduler, updates, properties.heartbeatInterval());
    }
    @Bean RunNextProcessingJob runNextProcessingJob(ClaimNextProcessingJob claims,
            ExecuteClaimedProcessingJob execution, ProcessingFailureClassifier failures) {
        return new RunNextProcessingJob(claims, execution, failures);
    }
    @Bean @ConditionalOnProperty(prefix = "hippocampus.materials.processing.recovery", name = "enabled", havingValue = "true")
    ProcessingJobPoller processingJobPoller(RunNextProcessingJob runner, ProcessingRecoveryProperties properties) {
        return new ProcessingJobPoller(runner, properties.workerId());
    }
}
