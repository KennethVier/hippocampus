package com.hippocampus.ai.infrastructure.provider.gemini;

import com.google.genai.Client;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    ChatModel geminiGenerationChatModel(Client client, GeminiProviderProperties properties) {
        GoogleGenAiChatOptions defaultOptions = GoogleGenAiChatOptions.builder()
                .model(properties.getDefaultModel())
                .build();
        return GoogleGenAiChatModel.builder()
                .genAiClient(client)
                .options(defaultOptions)
                .build();
    }

    @Bean
    AiProviderAdapter geminiProviderAdapter(ChatModel geminiGenerationChatModel) {
        return new GeminiProviderAdapter(geminiGenerationChatModel);
    }
}
