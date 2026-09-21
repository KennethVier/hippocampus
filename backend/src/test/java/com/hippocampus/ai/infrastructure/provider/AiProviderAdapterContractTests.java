package com.hippocampus.ai.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.hippocampus.ai.application.provider.AiProviderAdapter;
import com.hippocampus.ai.application.provider.ProviderExecutionRequest;
import com.hippocampus.ai.application.provider.ProviderExecutionResult;
import com.hippocampus.ai.application.routing.ProviderId;
import com.hippocampus.ai.infrastructure.provider.gemini.GeminiProviderAdapter;
import com.hippocampus.ai.infrastructure.provider.ollama.OllamaCloudProviderAdapter;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiProviderAdapterContractTests {

    @ParameterizedTest
    @MethodSource("adapters")
    void adaptersShareTheProviderNeutralExecutionContract(AdapterCase adapterCase) {
        ProviderExecutionResult result = adapterCase.adapter().execute(adapterCase.request());

        assertThat(result.providerId()).isEqualTo(adapterCase.request().target().providerId());
        assertThat(result.rawContent()).isEqualTo("{\"answer\":\"raw\"}");
        assertThat(result.getClass().getPackageName()).isEqualTo("com.hippocampus.ai.application.provider");
        assertThat(result.getClass().getRecordComponents())
                .extracting(component -> component.getType().getPackageName())
                .noneMatch(name -> name.contains("gemini") || name.contains("ollama") || name.contains("springframework.ai"));
        adapterCase.verify();
    }

    static Stream<AdapterCase> adapters() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("{\"answer\":\"raw\"}")))));

        RestClient.Builder builder = RestClient.builder().baseUrl("https://ollama.com/api");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://ollama.com/api/chat"))
                .andRespond(withSuccess(
                        "{\"model\":\"cloud-model\",\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"answer\\\":\\\"raw\\\"}\"}}",
                        MediaType.APPLICATION_JSON));

        return Stream.of(
                new AdapterCase(
                        new GeminiProviderAdapter(chatModel),
                        ProviderTestFixtures.request(ProviderId.GEMINI, "gemini-model"),
                        () -> {}),
                new AdapterCase(
                        new OllamaCloudProviderAdapter(builder.build()),
                        ProviderTestFixtures.request(ProviderId.OLLAMA_CLOUD, "cloud-model"),
                        server::verify));
    }

    record AdapterCase(AiProviderAdapter adapter, ProviderExecutionRequest request, Runnable verifier) {
        void verify() {
            verifier.run();
        }
    }
}
