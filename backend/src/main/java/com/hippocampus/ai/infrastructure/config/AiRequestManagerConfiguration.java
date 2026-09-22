package com.hippocampus.ai.infrastructure.config;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hippocampus.ai.application.provider.AiProviderAdapter;
import com.hippocampus.ai.application.request.AiRequestManager;
import com.hippocampus.ai.application.request.AiRequestManagerPolicy;
import com.hippocampus.ai.application.request.AiRequestTelemetry;
import com.hippocampus.ai.application.routing.ProviderId;
import com.hippocampus.ai.infrastructure.observability.MicrometerAiRequestTelemetry;

import io.micrometer.core.instrument.MeterRegistry;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiRequestManagerProperties.class)
public class AiRequestManagerConfiguration {

    @Bean
    @ConditionalOnMissingBean(AiRequestTelemetry.class)
    AiRequestTelemetry aiRequestTelemetry(MeterRegistry meterRegistry) {
        return new MicrometerAiRequestTelemetry(meterRegistry);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(AiRequestManager.class)
    AiRequestManager aiRequestManager(
            List<AiProviderAdapter> adapters,
            AiRequestManagerProperties properties,
            AiRequestTelemetry telemetry) {
        Map<ProviderId, AiRequestManagerPolicy> policies = new EnumMap<>(ProviderId.class);
        policies.put(ProviderId.GEMINI, properties.gemini().toPolicy());
        policies.put(ProviderId.OLLAMA_CLOUD, properties.ollamaCloud().toPolicy());
        return new AiRequestManager(adapters, policies, telemetry);
    }
}
