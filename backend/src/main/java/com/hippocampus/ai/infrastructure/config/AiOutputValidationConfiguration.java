package com.hippocampus.ai.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

import com.hippocampus.ai.application.validation.AiOutputValidator;
import com.hippocampus.ai.application.validation.AiSourceReferenceValidator;
import com.hippocampus.ai.infrastructure.validation.JacksonAiStructuredOutputDecoder;
import com.hippocampus.ai.port.AiStructuredOutputDecoder;
import com.hippocampus.materials.port.SourceReferenceRepository;

@AutoConfiguration(
        afterName = "com.hippocampus.materials.infrastructure.config.SourceReferenceConfiguration")
public class AiOutputValidationConfiguration {

    @Bean
    AiStructuredOutputDecoder aiStructuredOutputDecoder() {
        return new JacksonAiStructuredOutputDecoder();
    }

    @Bean
    AiOutputValidator aiOutputValidator(AiStructuredOutputDecoder decoder) {
        return new AiOutputValidator(decoder);
    }

    @Bean
    @ConditionalOnBean(SourceReferenceRepository.class)
    AiSourceReferenceValidator aiSourceReferenceValidator(SourceReferenceRepository sourceReferences) {
        return new AiSourceReferenceValidator(sourceReferences);
    }
}
