package com.hippocampus.ai.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import com.hippocampus.ai.application.AiExecutionOrchestrator;
import com.hippocampus.ai.infrastructure.learning.AiResponseEvaluationAdapter;
import com.hippocampus.ai.infrastructure.learning.AiTaskExecutionPolicy;
import com.hippocampus.learning.port.ResponseEvaluationPort;

@AutoConfiguration(
        after = AiExecutionOrchestratorConfiguration.class,
        beforeName = "com.hippocampus.learning.infrastructure.config.StudyMissionApplicationConfiguration")
@ConditionalOnBean({AiExecutionOrchestrator.class, AiTaskExecutionPolicy.class})
public class AiLearningTaskConfiguration {

    @Bean
    @ConditionalOnMissingBean(ResponseEvaluationPort.class)
    ResponseEvaluationPort responseEvaluationPort(
            AiExecutionOrchestrator orchestrator,
            AiTaskExecutionPolicy executionPolicy) {
        return new AiResponseEvaluationAdapter(orchestrator, executionPolicy);
    }
}
