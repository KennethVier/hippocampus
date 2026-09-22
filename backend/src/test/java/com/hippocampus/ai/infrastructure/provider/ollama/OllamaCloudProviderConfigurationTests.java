package com.hippocampus.ai.infrastructure.provider.ollama;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import com.hippocampus.ai.application.provider.AiProviderAdapter;

class OllamaCloudProviderConfigurationTests {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withUserConfiguration(OllamaCloudProviderConfiguration.class);

    @Test
    void disabledByDefaultNeedsNoApiKeyAndCreatesNoAdapter() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(AiProviderAdapter.class);
        });
    }

    @Test
    void enabledConfigurationRequiresApiKeyAndHttpsCloudUrl() {
        assertInvalid("https://ollama.com/api", "");
        assertInvalid("http://ollama.com/api", "secret");
        assertInvalid("https://localhost:11434/api", "secret");
        assertInvalid("https://ollama.com/api?token=secret", "secret");
    }

    @Test
    void enabledConfigurationCreatesAdapterWithoutExposingApiKey() {
        String secret = "test-" + UUID.randomUUID();
        contextRunner.withPropertyValues(
                        "hippocampus.ai.providers.ollama-cloud.enabled=true",
                        "hippocampus.ai.providers.ollama-cloud.base-url=https://ollama.com/api",
                        "hippocampus.ai.providers.ollama-cloud.api-key=" + secret)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AiProviderAdapter.class);
                    assertThat(context.getBean(OllamaCloudProviderProperties.class).toString()).doesNotContain(secret);
                });
    }

    private void assertInvalid(String baseUrl, String apiKey) {
        contextRunner.withPropertyValues(
                        "hippocampus.ai.providers.ollama-cloud.enabled=true",
                        "hippocampus.ai.providers.ollama-cloud.base-url=" + baseUrl,
                        "hippocampus.ai.providers.ollama-cloud.api-key=" + apiKey)
                .run(context -> assertThat(context).hasFailed());
    }
}
