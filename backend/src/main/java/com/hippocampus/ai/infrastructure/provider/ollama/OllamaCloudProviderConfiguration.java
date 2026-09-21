package com.hippocampus.ai.infrastructure.provider.ollama;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import com.hippocampus.ai.application.provider.AiProviderAdapter;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "hippocampus.ai.providers.ollama-cloud",
        name = "enabled",
        havingValue = "true")
@EnableConfigurationProperties(OllamaCloudProviderProperties.class)
public class OllamaCloudProviderConfiguration {

    @Bean
    RestClient ollamaCloudRestClient(RestClient.Builder builder, OllamaCloudProviderProperties properties) {
        properties.validateEnabledConfiguration();
        return builder
                .baseUrl(properties.normalizedBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .build();
    }

    @Bean
    AiProviderAdapter ollamaCloudProviderAdapter(RestClient ollamaCloudRestClient) {
        return new OllamaCloudProviderAdapter(ollamaCloudRestClient);
    }
}
