package com.hippocampus.ai.infrastructure.config;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.hippocampus.ai.application.prompt.PromptContextBuilder;
import com.hippocampus.ai.application.prompt.PromptTemplateRegistry;
import com.hippocampus.ai.application.prompt.PromptTokenCounter;
import com.hippocampus.ai.application.provider.AiProviderAdapter;
import com.hippocampus.ai.application.routing.ProviderId;
import com.hippocampus.ai.application.routing.ProviderRouter;
import com.hippocampus.ai.infrastructure.learning.AiTaskExecutionPolicy;
import com.hippocampus.ai.infrastructure.prompt.Utf8ByteLengthPromptTokenCounter;

@AutoConfiguration(after = {
        AiOutputValidationConfiguration.class,
        AiDiagnosticsPersistenceConfiguration.class
})
@EnableConfigurationProperties(AiTaskExecutionProperties.class)
public class AiRuntimeConfiguration {

    @Bean
    @ConditionalOnMissingBean
    PromptTokenCounter promptTokenCounter() {
        return new Utf8ByteLengthPromptTokenCounter();
    }

    @Bean
    @ConditionalOnMissingBean
    PromptTemplateRegistry promptTemplateRegistry() {
        return new PromptTemplateRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    PromptContextBuilder promptContextBuilder(
            PromptTemplateRegistry registry,
            PromptTokenCounter tokenCounter) {
        return new PromptContextBuilder(registry, tokenCounter);
    }

    @Bean
    @ConditionalOnMissingBean
    ProviderRouter providerRouter() {
        return new ProviderRouter();
    }

    @Bean
    @ConditionalOnBean(AiProviderAdapter.class)
    @ConditionalOnMissingBean
    AiTaskExecutionPolicy aiTaskExecutionPolicy(
            AiTaskExecutionProperties properties,
            List<AiProviderAdapter> providerAdapters) {
        Set<ProviderId> configuredProviders = providerAdapters.stream()
                .map(AiProviderAdapter::providerId)
                .collect(Collectors.toUnmodifiableSet());
        return properties.toPolicy(configuredProviders);
    }
}
