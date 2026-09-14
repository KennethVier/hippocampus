package com.hippocampus.rag.infrastructure.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.hippocampus.rag.port.EmbeddingPort;

class GeminiEmbeddingConfigurationTests {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(GeminiEmbeddingConfiguration.class);

    @Test
    void disabledConfigurationNeedsNoApiKeyAndCreatesNoProviderBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(EmbeddingPort.class);
        });
    }

    @Test
    void enabledConfigurationUsesConfiguredModelAndDimension() {
        String credential = testCredential();
        contextRunner.withPropertyValues(
                "hippocampus.rag.embedding.gemini.enabled=true",
                "hippocampus.rag.embedding.gemini.api-key=" + credential,
                "hippocampus.rag.embedding.gemini.model=gemini-embedding-2",
                "hippocampus.rag.embedding.gemini.dimension=768")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(EmbeddingPort.class);
                    GoogleGenAiTextEmbeddingOptions options = context.getBean(GoogleGenAiTextEmbeddingOptions.class);
                    assertThat(options.getModel()).isEqualTo("gemini-embedding-2");
                    assertThat(options.getDimensions()).isEqualTo(768);
                    assertThat(options.getTitle()).isNull();
                });
    }

    @Test
    void enabledConfigurationFailsClosedForMissingCredential() {
        assertInvalidEnabledConfiguration(null, "hippocampus.rag.embedding.gemini.enabled=true");
    }

    @Test
    void enabledConfigurationFailsClosedForBlankModelOrNonPositiveDimension() {
        String credential = testCredential();
        assertInvalidEnabledConfiguration(
                credential,
                "hippocampus.rag.embedding.gemini.enabled=true",
                "hippocampus.rag.embedding.gemini.api-key=" + credential,
                "hippocampus.rag.embedding.gemini.model=");
        assertInvalidEnabledConfiguration(
                credential,
                "hippocampus.rag.embedding.gemini.enabled=true",
                "hippocampus.rag.embedding.gemini.api-key=" + credential,
                "hippocampus.rag.embedding.gemini.dimension=0");
    }

    @Test
    void propertiesStringRepresentationDoesNotExposeCredential() {
        String credential = testCredential();
        GeminiEmbeddingProperties properties = new GeminiEmbeddingProperties();
        properties.setApiKey(credential);

        assertThat(properties.toString()).doesNotContain(credential);
    }

    private void assertInvalidEnabledConfiguration(String credential, String... properties) {
        contextRunner.withPropertyValues(properties).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class);
            if (credential != null) {
                assertThat(context.getStartupFailure().toString()).doesNotContain(credential);
            }
        });
    }

    private static String testCredential() {
        return "test-" + UUID.randomUUID();
    }
}
