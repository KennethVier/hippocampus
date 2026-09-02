package com.hippocampus.materials.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hippocampus.materials.infrastructure.observability.MicrometerMaterialLifecycleTelemetry;
import com.hippocampus.materials.port.MaterialLifecycleTelemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class MaterialLifecycleTelemetryConfigurationTests {

    @Test
    void createsMaterialsTelemetryFromTheExistingMeterRegistry() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MaterialLifecycleTelemetryConfiguration.class))
                .withUserConfiguration(MeterConfiguration.class)
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(MeterRegistry.class)
                        .hasSingleBean(MaterialLifecycleTelemetry.class)
                        .getBean(MaterialLifecycleTelemetry.class)
                        .isInstanceOf(MicrometerMaterialLifecycleTelemetry.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class MeterConfiguration {
        @Bean MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
