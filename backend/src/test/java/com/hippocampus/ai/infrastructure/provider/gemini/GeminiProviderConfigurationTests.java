package com.hippocampus.ai.infrastructure.provider.gemini;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.hippocampus.ai.application.provider.AiProviderAdapter;
import org.springframework.core.retry.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiProviderConfigurationTests {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(GeminiProviderConfiguration.class);

    @Test
    void disabledByDefaultNeedsNoApiKeyAndCreatesNoAdapter() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(AiProviderAdapter.class);
        });
    }

    @Test
    void enabledConfigurationRequiresApiKey() {
        contextRunner.withPropertyValues("hippocampus.ai.providers.gemini.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void enabledConfigurationRequiresExplicitModelSelection() {
        contextRunner.withPropertyValues(
                        "hippocampus.ai.providers.gemini.enabled=true",
                        "hippocampus.ai.providers.gemini.api-key=test-secret")
                .run(context -> assertThat(context).hasFailed());
        assertThat(new GeminiProviderProperties().getDefaultModel()).isNull();
    }

    @Test
    void enabledConfigurationCreatesAdapterWithoutExposingApiKey() {
        String secret = "test-" + UUID.randomUUID();
        contextRunner.withPropertyValues(
                        "hippocampus.ai.providers.gemini.enabled=true",
                        "hippocampus.ai.providers.gemini.api-key=" + secret,
                        "hippocampus.ai.providers.gemini.default-model=gemini-test")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AiProviderAdapter.class);
                    assertThat(context.getBean(GeminiProviderProperties.class).toString()).doesNotContain(secret);
                });
    }

    @Test
    void geminiRetryTemplateAllowsOnlyOneProviderAttempt() {
        contextRunner.withPropertyValues(
                        "hippocampus.ai.providers.gemini.enabled=true",
                        "hippocampus.ai.providers.gemini.api-key=test-secret",
                        "hippocampus.ai.providers.gemini.default-model=gemini-test")
                .run(context -> {
                    RetryTemplate retryTemplate = context.getBean("geminiGenerationRetryTemplate", RetryTemplate.class);
                    AtomicInteger attempts = new AtomicInteger();

                    assertThatThrownBy(() -> retryTemplate.invoke(() -> {
                        attempts.incrementAndGet();
                        throw new IllegalStateException("provider failure");
                    })).isInstanceOf(IllegalStateException.class);
                    assertThat(attempts).hasValue(1);
                });
    }
}
