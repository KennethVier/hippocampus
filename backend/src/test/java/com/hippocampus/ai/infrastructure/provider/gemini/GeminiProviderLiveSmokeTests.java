package com.hippocampus.ai.infrastructure.provider.gemini;

import static com.hippocampus.ai.infrastructure.provider.ProviderTestFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import com.google.genai.Client;
import com.hippocampus.ai.application.provider.ProviderExecutionResult;
import com.hippocampus.ai.application.provider.ProviderStreamCompleted;
import com.hippocampus.ai.application.routing.ProviderId;

import java.util.ArrayList;

class GeminiProviderLiveSmokeTests {

    @Test
    void callsGeminiWhenExplicitlyEnabled() {
        String apiKey = System.getenv("GEMINI_API_KEY");
        String model = System.getenv("HIPPOCAMPUS_GEMINI_SMOKE_MODEL");
        Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("HIPPOCAMPUS_LIVE_AI_SMOKE")));
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank());
        Assumptions.assumeTrue(model != null && !model.isBlank());

        GeminiProviderProperties properties = new GeminiProviderProperties();
        properties.setEnabled(true);
        properties.setApiKey(apiKey);
        properties.setDefaultModel(model);
        GeminiProviderConfiguration configuration = new GeminiProviderConfiguration();
        Client client = configuration.geminiGenerationClient(properties);
        ChatModel chatModel = configuration.geminiGenerationChatModel(client, properties);

        ProviderExecutionResult result = new GeminiProviderAdapter(chatModel)
                .execute(request(ProviderId.GEMINI, model));

        assertThat(result.providerId()).isEqualTo(ProviderId.GEMINI);
        assertThat(result.modelId()).isNotBlank();
        assertThat(result.rawContent()).isNotBlank();
    }

    @Test
    void streamsGeminiWhenExplicitlyEnabled() {
        String apiKey = System.getenv("GEMINI_API_KEY");
        String model = System.getenv("HIPPOCAMPUS_GEMINI_SMOKE_MODEL");
        Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("HIPPOCAMPUS_LIVE_AI_SMOKE")));
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank());
        Assumptions.assumeTrue(model != null && !model.isBlank());

        GeminiProviderProperties properties = new GeminiProviderProperties();
        properties.setEnabled(true);
        properties.setApiKey(apiKey);
        properties.setDefaultModel(model);
        GeminiProviderConfiguration configuration = new GeminiProviderConfiguration();
        Client client = configuration.geminiGenerationClient(properties);
        ChatModel chatModel = configuration.geminiGenerationChatModel(client, properties);
        var events = new ArrayList<>();

        new GeminiProviderAdapter(chatModel)
                .stream(request(ProviderId.GEMINI, model))
                .consume(events::add);

        assertThat(events).isNotEmpty();
        assertThat(events.getLast()).isInstanceOf(ProviderStreamCompleted.class);
    }
}
