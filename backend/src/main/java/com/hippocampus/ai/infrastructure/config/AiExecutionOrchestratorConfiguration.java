package com.hippocampus.ai.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import com.hippocampus.ai.application.AiExecutionOrchestrator;
import com.hippocampus.ai.application.prompt.PromptContextBuilder;
import com.hippocampus.ai.application.request.AiRequestManager;
import com.hippocampus.ai.application.request.AiRequestTelemetry;
import com.hippocampus.ai.application.routing.ProviderRouter;
import com.hippocampus.ai.application.validation.AiOutputValidator;
import com.hippocampus.ai.application.validation.AiSourceReferenceValidator;
import com.hippocampus.ai.infrastructure.learning.AiTaskExecutionPolicy;
import com.hippocampus.ai.port.AiDiagnosticsPersistence;
import com.hippocampus.identity.port.CurrentUser;

@AutoConfiguration(after = AiRuntimeConfiguration.class)
@ConditionalOnBean({
        AiTaskExecutionPolicy.class,
        PromptContextBuilder.class,
        CurrentUser.class,
        ProviderRouter.class,
        AiRequestManager.class,
        AiOutputValidator.class,
        AiSourceReferenceValidator.class,
        AiDiagnosticsPersistence.class,
        AiRequestTelemetry.class
})
public class AiExecutionOrchestratorConfiguration {

    @Bean
    @ConditionalOnMissingBean
    AiExecutionOrchestrator aiExecutionOrchestrator(
            PromptContextBuilder promptContextBuilder,
            CurrentUser currentUser,
            ProviderRouter providerRouter,
            AiRequestManager requestManager,
            AiOutputValidator outputValidator,
            AiSourceReferenceValidator sourceReferenceValidator,
            AiDiagnosticsPersistence diagnosticsPersistence,
            AiRequestTelemetry telemetry) {
        return new AiExecutionOrchestrator(
                promptContextBuilder,
                currentUser,
                providerRouter,
                requestManager,
                outputValidator,
                sourceReferenceValidator,
                diagnosticsPersistence,
                telemetry);
    }
}
