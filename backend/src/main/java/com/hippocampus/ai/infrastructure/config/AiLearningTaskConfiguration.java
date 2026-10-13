package com.hippocampus.ai.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import com.hippocampus.ai.application.validation.ResponseAttributionValidator;

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
            AiTaskExecutionPolicy executionPolicy,
            @Value("${hippocampus.ai.response-evaluation.max-response-characters}") int maxResponseCharacters,
            @Value("${hippocampus.ai.response-evaluation.max-sources}") int maxSources,
            @Value("${hippocampus.ai.response-evaluation.max-source-characters}") int maxSourceCharacters,
            @Value("${hippocampus.ai.response-evaluation.max-assertions}") int maxAssertions,
            @Value("${hippocampus.ai.response-evaluation.max-candidates}") int maxCandidates,
            @Value("${hippocampus.ai.response-evaluation.max-source-spans}") int maxSourceSpans,
            @Value("${hippocampus.ai.response-evaluation.max-end-to-end-millis}") long maxEndToEndMillis) {
        return new AiResponseEvaluationAdapter(orchestrator, executionPolicy,
                new ResponseAttributionValidator.Limits(
                        maxResponseCharacters, maxSources, maxSourceCharacters,
                        maxAssertions, maxCandidates, maxSourceSpans, maxEndToEndMillis));
    }
}
