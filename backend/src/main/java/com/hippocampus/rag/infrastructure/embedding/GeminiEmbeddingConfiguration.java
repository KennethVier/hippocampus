package com.hippocampus.rag.infrastructure.embedding;

import org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hippocampus.rag.port.EmbeddingPort;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "hippocampus.rag.embedding.gemini",
        name = "enabled",
        havingValue = "true")
@EnableConfigurationProperties(GeminiEmbeddingProperties.class)
public class GeminiEmbeddingConfiguration {

    @Bean
    GoogleGenAiEmbeddingConnectionDetails googleGenAiEmbeddingConnectionDetails(
            GeminiEmbeddingProperties properties) {
        properties.validateEnabledConfiguration();
        return GoogleGenAiEmbeddingConnectionDetails.builder()
                .apiKey(properties.getApiKey())
                .build();
    }

    @Bean
    GoogleGenAiTextEmbeddingOptions googleGenAiTextEmbeddingOptions(GeminiEmbeddingProperties properties) {
        properties.validateEnabledConfiguration();
        return GoogleGenAiTextEmbeddingOptions.builder()
                .model(properties.getModel())
                .dimensions(properties.getDimension())
                .build();
    }

    @Bean
    GoogleGenAiTextEmbeddingModel googleGenAiTextEmbeddingModel(
            GoogleGenAiEmbeddingConnectionDetails connectionDetails,
            GoogleGenAiTextEmbeddingOptions options) {
        return new GoogleGenAiTextEmbeddingModel(connectionDetails, options);
    }

    @Bean
    EmbeddingPort geminiEmbeddingPort(
            GoogleGenAiTextEmbeddingModel embeddingModel,
            GeminiEmbeddingProperties properties) {
        return new GeminiEmbeddingAdapter(embeddingModel, properties.getModel(), properties.getDimension());
    }
}
