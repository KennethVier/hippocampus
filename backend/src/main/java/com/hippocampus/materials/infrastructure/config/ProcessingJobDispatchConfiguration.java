package com.hippocampus.materials.infrastructure.config;

import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

import com.hippocampus.materials.application.CompleteProcessingStage;
import com.hippocampus.materials.application.ExecuteClaimedProcessingJob;
import com.hippocampus.materials.application.ProcessingDispatcher;
import com.hippocampus.materials.application.ProcessingStageHandler;
import com.hippocampus.materials.infrastructure.persistence.JdbcProcessingJobStageCompletionRepository;
import com.hippocampus.materials.port.ProcessingJobStageCompletionRepository;

@AutoConfiguration(afterName = {
        "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration",
        "org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration"
})
@ConditionalOnBean({JdbcClient.class, PlatformTransactionManager.class})
public class ProcessingJobDispatchConfiguration {

    @Bean
    ProcessingDispatcher processingDispatcher(List<ProcessingStageHandler> handlers) {
        return new ProcessingDispatcher(handlers);
    }

    @Bean
    ProcessingJobStageCompletionRepository processingJobStageCompletionRepository(JdbcClient jdbcClient) {
        return new JdbcProcessingJobStageCompletionRepository(jdbcClient);
    }

    @Bean
    CompleteProcessingStage completeProcessingStage(ProcessingJobStageCompletionRepository jobs) {
        return new CompleteProcessingStage(jobs);
    }

    @Bean
    ExecuteClaimedProcessingJob executeClaimedProcessingJob(
            ProcessingDispatcher dispatcher,
            CompleteProcessingStage completion) {
        return new ExecuteClaimedProcessingJob(dispatcher, completion);
    }
}
