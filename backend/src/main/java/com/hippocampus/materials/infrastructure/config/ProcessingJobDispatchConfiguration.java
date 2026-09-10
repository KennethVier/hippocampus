package com.hippocampus.materials.infrastructure.config;

import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

import com.hippocampus.materials.application.CompleteProcessingStage;
import com.hippocampus.materials.application.ExecuteClaimedProcessingJob;
import com.hippocampus.materials.application.FinalizeProcessingFailure;
import com.hippocampus.materials.application.ProcessingFailureClassifier;
import com.hippocampus.materials.application.ProcessingDispatcher;
import com.hippocampus.materials.application.ProcessingStageHandler;
import com.hippocampus.materials.infrastructure.persistence.JdbcProcessingJobStageCompletionRepository;
import com.hippocampus.materials.port.ProcessingJobStageCompletionRepository;
import com.hippocampus.materials.port.ProcessingHeartbeatMonitor;
import org.springframework.beans.factory.ObjectProvider;

@AutoConfiguration(afterName = {
        "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration",
        "org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration"
})
@ConditionalOnBean({JdbcClient.class, PlatformTransactionManager.class})
@org.springframework.context.annotation.Import(MaterialReadinessConfiguration.class)
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
    CompleteProcessingStage completeProcessingStage(ProcessingJobStageCompletionRepository jobs,
            com.hippocampus.materials.application.DeriveMaterialReadiness readiness) {
        return new CompleteProcessingStage(jobs, readiness);
    }

    @Bean
    ExecuteClaimedProcessingJob executeClaimedProcessingJob(
            ProcessingDispatcher dispatcher,
            CompleteProcessingStage completion,
            ObjectProvider<ProcessingFailureClassifier> classifiers,
            ObjectProvider<FinalizeProcessingFailure> failureFinalizers,
            ObjectProvider<ProcessingHeartbeatMonitor> heartbeatMonitors) {
        ProcessingFailureClassifier classifier = classifiers.getIfAvailable();
        FinalizeProcessingFailure finalizer = failureFinalizers.getIfAvailable();
        ProcessingHeartbeatMonitor heartbeat = heartbeatMonitors.getIfAvailable();
        return classifier == null || finalizer == null || heartbeat == null
                ? new ExecuteClaimedProcessingJob(dispatcher, completion)
                : new ExecuteClaimedProcessingJob(dispatcher, completion, classifier, finalizer, heartbeat);
    }
}
