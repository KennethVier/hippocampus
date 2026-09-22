package com.hippocampus.ai.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.ArrayList;
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
import com.hippocampus.ai.application.provider.ProviderStreamCompleted;
import com.hippocampus.ai.application.provider.ProviderStreamEvent;
import com.hippocampus.ai.application.provider.ProviderTextDelta;
import com.hippocampus.ai.application.routing.ProviderId;
import com.hippocampus.ai.infrastructure.provider.gemini.GeminiProviderAdapter;
import com.hippocampus.ai.infrastructure.provider.ollama.OllamaCloudProviderAdapter;
import reactor.core.publisher.Flux;

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

    @ParameterizedTest
    @MethodSource("streamAdapters")
    void adaptersShareProviderNeutralStreamingEventsWithoutLeakingProviderTypes(AdapterCase adapterCase)
            throws NoSuchMethodException {
        List<ProviderStreamEvent> events = new ArrayList<>();

        adapterCase.adapter().stream(adapterCase.request()).consume(events::add);

        assertThat(events).hasSize(3);
        assertThat(events.get(0)).isEqualTo(new ProviderTextDelta("{\"answer\":"));
        assertThat(events.get(1)).isEqualTo(new ProviderTextDelta("\"raw\"}"));
        assertThat(events.get(2)).isInstanceOf(ProviderStreamCompleted.class);
        assertThat(Stream.of(
                        AiProviderAdapter.class.getMethod("stream", ProviderExecutionRequest.class).getReturnType(),
                        ProviderStreamEvent.class,
                        ProviderTextDelta.class,
                        ProviderStreamCompleted.class)
                .map(Class::getName))
                .noneMatch(name -> name.contains("reactor")
                        || name.contains("gemini")
                        || name.contains("ollama")
                        || name.contains("springframework.ai"));
        assertThat(ProviderStreamCompleted.class.getRecordComponents())
                .extracting(component -> component.getType().getName())
                .noneMatch(name -> name.contains("reactor")
                        || name.contains("gemini")
                        || name.contains("ollama")
                        || name.contains("springframework.ai"));
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

    static Stream<AdapterCase> streamAdapters() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(Flux.just(
                        new ChatResponse(List.of(new Generation(new AssistantMessage("{\"answer\":")))),
                        new ChatResponse(List.of(new Generation(new AssistantMessage("\"raw\"}"))))));

        RestClient.Builder builder = RestClient.builder().baseUrl("https://ollama.com/api");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://ollama.com/api/chat"))
                .andRespond(withSuccess("""
                        {"model":"cloud-model","message":{"role":"assistant","content":"{\\\"answer\\\":"},"done":false}
                        {"model":"cloud-model","message":{"role":"assistant","content":"\\\"raw\\\"}"},"done":false}
                        {"model":"cloud-model","message":{"role":"assistant","content":""},"done":true,"done_reason":"stop","prompt_eval_count":4,"eval_count":2}
                        """, MediaType.APPLICATION_NDJSON));

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
