package com.hippocampus.materials.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import com.hippocampus.materials.infrastructure.observability.MicrometerMaterialLifecycleTelemetry;
import com.hippocampus.materials.port.MaterialLifecycleTelemetry;

import io.micrometer.core.instrument.MeterRegistry;

@AutoConfiguration
public class MaterialLifecycleTelemetryConfiguration {

    @Bean
    @ConditionalOnMissingBean(MaterialLifecycleTelemetry.class)
    MaterialLifecycleTelemetry materialLifecycleTelemetry(MeterRegistry meterRegistry) {
        return new MicrometerMaterialLifecycleTelemetry(meterRegistry);
    }
}
