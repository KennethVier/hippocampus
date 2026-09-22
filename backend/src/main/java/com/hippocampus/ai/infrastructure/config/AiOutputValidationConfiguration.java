package com.hippocampus.ai.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hippocampus.ai.application.validation.AiOutputValidator;
import com.hippocampus.ai.infrastructure.validation.JacksonAiStructuredOutputDecoder;
import com.hippocampus.ai.port.AiStructuredOutputDecoder;

@Configuration(proxyBeanMethods = false)
public class AiOutputValidationConfiguration {

    @Bean
    AiStructuredOutputDecoder aiStructuredOutputDecoder() {
        return new JacksonAiStructuredOutputDecoder();
    }

    @Bean
    AiOutputValidator aiOutputValidator(AiStructuredOutputDecoder decoder) {
        return new AiOutputValidator(decoder);
    }
}
