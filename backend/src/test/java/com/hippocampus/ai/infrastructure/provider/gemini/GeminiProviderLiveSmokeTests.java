package com.hippocampus.ai.infrastructure.provider.gemini;

import static com.hippocampus.ai.infrastructure.provider.ProviderTestFixtures.liveExplanationRequest;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import com.google.genai.Client;
import com.hippocampus.ai.application.validation.AiOutputValidator;
import com.hippocampus.ai.application.provider.ProviderExecutionResult;
import com.hippocampus.ai.application.routing.ProviderId;
import com.hippocampus.ai.domain.AiOutputContract;
import com.hippocampus.ai.domain.ExplanationResult;
import com.hippocampus.ai.domain.ValidatedAiResult;
import com.hippocampus.ai.infrastructure.validation.JacksonAiStructuredOutputDecoder;

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
                .execute(liveExplanationRequest(ProviderId.GEMINI, model));
        ValidatedAiResult<?> validated = new AiOutputValidator(new JacksonAiStructuredOutputDecoder())
                .validate(result, AiOutputContract.EXPLANATION);

        assertThat(result.providerId()).isEqualTo(ProviderId.GEMINI);
        assertThat(result.modelId()).isNotBlank();
        assertThat(validated.result()).isInstanceOf(ExplanationResult.class);
    }
}
