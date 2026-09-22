package com.hippocampus.ai.infrastructure.provider.gemini;

import com.google.genai.Client;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

import com.hippocampus.ai.application.provider.AiProviderAdapter;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "hippocampus.ai.providers.gemini",
        name = "enabled",
        havingValue = "true")
@EnableConfigurationProperties(GeminiProviderProperties.class)
public class GeminiProviderConfiguration {

    @Bean
    Client geminiGenerationClient(GeminiProviderProperties properties) {
        properties.validateEnabledConfiguration();
        return Client.builder().apiKey(properties.getApiKey()).build();
    }

    @Bean
    RetryTemplate geminiGenerationRetryTemplate() {
        return new RetryTemplate(RetryPolicy.withMaxRetries(0));
    }

    @Bean
    ChatModel geminiGenerationChatModel(
            Client client,
            GeminiProviderProperties properties,
            @Qualifier("geminiGenerationRetryTemplate") RetryTemplate retryTemplate) {
        return buildChatModel(client, properties, retryTemplate);
    }

    ChatModel geminiGenerationChatModel(Client client, GeminiProviderProperties properties) {
        return buildChatModel(client, properties, new RetryTemplate(RetryPolicy.withMaxRetries(0)));
    }

    private ChatModel buildChatModel(
            Client client,
            GeminiProviderProperties properties,
            RetryTemplate retryTemplate) {
        GoogleGenAiChatOptions defaultOptions = GoogleGenAiChatOptions.builder()
                .model(properties.getDefaultModel())
                .build();
        return GoogleGenAiChatModel.builder()
                .genAiClient(client)
                .options(defaultOptions)
                .retryTemplate(retryTemplate)
                .build();
    }

    @Bean
    AiProviderAdapter geminiProviderAdapter(ChatModel geminiGenerationChatModel) {
        return new GeminiProviderAdapter(geminiGenerationChatModel);
    }
}
