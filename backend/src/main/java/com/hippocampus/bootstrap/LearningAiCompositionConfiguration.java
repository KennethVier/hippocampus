package com.hippocampus.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import com.hippocampus.ai.application.AiExecutionOrchestrator;
import com.hippocampus.ai.infrastructure.learning.AiTaskExecutionOptions;
import com.hippocampus.learning.port.ActivityAiTaskPort;

@AutoConfiguration(beforeName = "com.hippocampus.learning.infrastructure.config.StudyMissionApplicationConfiguration")
@ConditionalOnBean({AiExecutionOrchestrator.class, AiTaskExecutionOptions.class})
public class LearningAiCompositionConfiguration {

    @Bean
    @ConditionalOnMissingBean(ActivityAiTaskPort.class)
    ActivityAiTaskPort activityAiTaskPort(
            AiExecutionOrchestrator orchestrator,
            AiTaskExecutionOptions executionOptions,
            ObjectMapper objectMapper) {
        return new LearningActivityAiTaskAdapter(orchestrator, executionOptions, objectMapper);
    }
}
