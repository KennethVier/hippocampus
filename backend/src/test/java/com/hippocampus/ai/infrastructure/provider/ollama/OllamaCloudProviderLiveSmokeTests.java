package com.hippocampus.ai.infrastructure.provider.ollama;

import static com.hippocampus.ai.infrastructure.provider.ProviderTestFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import com.hippocampus.ai.application.provider.ProviderExecutionResult;
import com.hippocampus.ai.application.routing.ProviderId;

class OllamaCloudProviderLiveSmokeTests {

    @Test
    void callsOllamaCloudWhenExplicitlyEnabled() {
        String apiKey = System.getenv("OLLAMA_API_KEY");
        String model = System.getenv("HIPPOCAMPUS_OLLAMA_CLOUD_SMOKE_MODEL");
        Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("HIPPOCAMPUS_LIVE_AI_SMOKE")));
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank());
        Assumptions.assumeTrue(model != null && !model.isBlank());

        RestClient client = RestClient.builder()
                .baseUrl("https://ollama.com/api")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        ProviderExecutionResult result = new OllamaCloudProviderAdapter(client)
                .execute(request(ProviderId.OLLAMA_CLOUD, model));

        assertThat(result.providerId()).isEqualTo(ProviderId.OLLAMA_CLOUD);
        assertThat(result.rawContent()).isNotBlank();
    }
}
