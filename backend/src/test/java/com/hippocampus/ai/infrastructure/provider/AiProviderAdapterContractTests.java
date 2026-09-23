package com.hippocampus.ai.infrastructure.provider;

import static com.hippocampus.ai.infrastructure.provider.ProviderTestFixtures.validateLiveExplanation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withRawStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.google.genai.errors.ClientException;
import com.google.genai.errors.ServerException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.hippocampus.ai.application.provider.AiProviderAdapter;
import com.hippocampus.ai.application.provider.ProviderExecutionException;
import com.hippocampus.ai.application.provider.ProviderExecutionRequest;
import com.hippocampus.ai.application.provider.ProviderExecutionResult;
import com.hippocampus.ai.application.provider.ProviderFailureType;
import com.hippocampus.ai.application.provider.ProviderStreamCompleted;
import com.hippocampus.ai.application.provider.ProviderStreamEvent;
import com.hippocampus.ai.application.provider.ProviderTextDelta;
import com.hippocampus.ai.application.provider.ProviderUsage;
import com.hippocampus.ai.application.routing.ProviderId;
import com.hippocampus.ai.domain.AiTaskType;
import com.hippocampus.ai.infrastructure.provider.gemini.GeminiProviderAdapter;
import com.hippocampus.ai.infrastructure.provider.ollama.OllamaCloudProviderAdapter;
import reactor.core.publisher.Flux;

class AiProviderAdapterContractTests {

    private static final String RAW_PROVIDER_DETAIL = "raw-provider-body api-key-secret";

    @ParameterizedTest(name = "{0}")
    @MethodSource("adapters")
    void adaptersShareTheProviderNeutralExecutionContract(AdapterCase adapterCase) {
        ProviderExecutionResult result = adapterCase.adapter().execute(adapterCase.request());

        assertThat(adapterCase.adapter().providerId()).isEqualTo(adapterCase.request().target().providerId());
        assertThat(result.providerId()).isEqualTo(adapterCase.request().target().providerId());
        assertThat(result.modelId()).isEqualTo(adapterCase.expectedModelId());
        assertThat(result.rawContent()).isEqualTo("{\"answer\":\"raw\"}");
        assertThat(result.usage().inputTokens()).contains(adapterCase.expectedInputTokens());
        assertThat(result.usage().outputTokens()).contains(adapterCase.expectedOutputTokens());
        assertThat(result.latency()).isGreaterThanOrEqualTo(Duration.ZERO);
        assertThat(result.retryCount()).isZero();
        assertThat(result.getClass().getPackageName()).isEqualTo("com.hippocampus.ai.application.provider");
        assertThat(result.getClass().getRecordComponents())
                .extracting(component -> component.getType().getPackageName())
                .noneMatch(name -> name.contains("gemini")
                        || name.contains("ollama")
                        || name.contains("springframework.ai"));
        adapterCase.verify();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("adapters")
    void adaptersExposeTheSameTaskCapabilityContract(AdapterCase adapterCase) {
        assertThat(Stream.of(AiTaskType.values()).allMatch(adapterCase.adapter()::supports)).isTrue();
        assertThat(adapterCase.adapter().supports(null)).isFalse();
    }

    @ParameterizedTest(name = "safe diagnostic {index}")
    @MethodSource("liveSmokeValidationFailures")
    void liveSmokeValidationFailureReportsOnlySafeSchemaMetadata(ProviderExecutionResult result) {
        assertThatThrownBy(() -> validateLiveExplanation(result))
                .isInstanceOf(AssertionError.class)
                .hasMessage("""
                        provider: %s
                        model: %s
                        output contract: EXPLANATION
                        schema failure reason: CONTRACT_MISMATCH
                        """.formatted(result.providerId(), result.modelId()).strip())
                .hasNoCause()
                .hasMessageNotContaining(RAW_PROVIDER_DETAIL);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("normalizedFailures")
    void adaptersNormalizeCommonFailuresWithoutLeakingProviderDetails(FailureCase failureCase) {
        assertThatThrownBy(() -> failureCase.adapter().execute(failureCase.request()))
                .isInstanceOfSatisfying(ProviderExecutionException.class, failure -> {
                    assertThat(failure.providerId()).isEqualTo(failureCase.providerId());
                    assertThat(failure.failureType()).isEqualTo(failureCase.expectedFailure());
                    assertThat(failure.retryCount()).isZero();
                    assertThat(failure).hasNoCause();
                    assertThat(failure.getMessage()).doesNotContain(
                            "raw-provider-body", "api-key-secret", "system-policy-secret-marker");
                });
        failureCase.verify();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unsupportedRoutes")
    void adaptersRejectRoutesForAnotherProvider(FailureCase failureCase) {
        assertThatThrownBy(() -> failureCase.adapter().execute(failureCase.request()))
                .isInstanceOfSatisfying(ProviderExecutionException.class, failure -> {
                    assertThat(failure.providerId()).isEqualTo(failureCase.providerId());
                    assertThat(failure.failureType()).isEqualTo(ProviderFailureType.UNSUPPORTED_TASK);
                    assertThat(failure).hasNoCause();
                });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("streamAdapters")
    void adaptersShareProviderNeutralStreamingEventsWithoutLeakingProviderTypes(AdapterCase adapterCase)
            throws NoSuchMethodException {
        List<ProviderStreamEvent> events = new ArrayList<>();

        adapterCase.adapter().stream(adapterCase.request()).consume(events::add);

        assertThat(events).hasSize(3);
        assertThat(events.get(0)).isEqualTo(new ProviderTextDelta("{\"answer\":"));
        assertThat(events.get(1)).isEqualTo(new ProviderTextDelta("\"raw\"}"));
        assertThat(events.get(2)).isInstanceOfSatisfying(ProviderStreamCompleted.class, completed -> {
            assertThat(completed.providerId()).isEqualTo(adapterCase.request().target().providerId());
            assertThat(completed.modelId()).isEqualTo(adapterCase.expectedModelId());
            assertThat(completed.usage().inputTokens()).contains(adapterCase.expectedInputTokens());
            assertThat(completed.usage().outputTokens()).contains(adapterCase.expectedOutputTokens());
            assertThat(completed.latency()).isGreaterThanOrEqualTo(Duration.ZERO);
        });
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
        adapterCase.verify();
    }

    static Stream<AdapterCase> adapters() {
        ChatModel chatModel = mock(ChatModel.class);
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .model("gemini-actual")
                .usage(new DefaultUsage(4, 2, 6))
                .build();
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(new ChatResponse(
                        List.of(new Generation(new AssistantMessage("{\"answer\":\"raw\"}"))), metadata));

        RestClient.Builder builder = RestClient.builder().baseUrl("https://ollama.com/api");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://ollama.com/api/chat"))
                .andRespond(withSuccess(
                        "{\"model\":\"ollama-actual\",\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"answer\\\":\\\"raw\\\"}\"},\"prompt_eval_count\":4,\"eval_count\":2}",
                        MediaType.APPLICATION_JSON));

        return Stream.of(
                new AdapterCase(
                        "Gemini execution contract",
                        new GeminiProviderAdapter(chatModel),
                        ProviderTestFixtures.request(ProviderId.GEMINI, "gemini-selected"),
                        "gemini-actual", 4, 2, () -> {}),
                new AdapterCase(
                        "Ollama execution contract",
                        new OllamaCloudProviderAdapter(builder.build()),
                        ProviderTestFixtures.request(ProviderId.OLLAMA_CLOUD, "ollama-selected"),
                        "ollama-actual", 4, 2, server::verify));
    }

    static Stream<AdapterCase> streamAdapters() {
        ChatModel chatModel = mock(ChatModel.class);
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .model("gemini-actual")
                .usage(new DefaultUsage(4, 2, 6))
                .build();
        when(chatModel.stream(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(Flux.just(
                        new ChatResponse(List.of(new Generation(new AssistantMessage("{\"answer\":")))),
                        new ChatResponse(
                                List.of(new Generation(new AssistantMessage("\"raw\"}"))), metadata)));

        RestClient.Builder builder = RestClient.builder().baseUrl("https://ollama.com/api");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://ollama.com/api/chat"))
                .andRespond(withSuccess("""
                        {"model":"ollama-actual","message":{"role":"assistant","content":"{\\"answer\\":"},"done":false}
                        {"model":"ollama-actual","message":{"role":"assistant","content":"\\"raw\\"}"},"done":false}
                        {"model":"ollama-actual","message":{"role":"assistant","content":""},"done":true,"done_reason":"stop","prompt_eval_count":4,"eval_count":2}
                        """, MediaType.APPLICATION_NDJSON));

        return Stream.of(
                new AdapterCase(
                        "Gemini streaming contract",
                        new GeminiProviderAdapter(chatModel),
                        ProviderTestFixtures.request(ProviderId.GEMINI, "gemini-selected"),
                        "gemini-actual", 4, 2, () -> {}),
                new AdapterCase(
                        "Ollama streaming contract",
                        new OllamaCloudProviderAdapter(builder.build()),
                        ProviderTestFixtures.request(ProviderId.OLLAMA_CLOUD, "ollama-selected"),
                        "ollama-actual", 4, 2, server::verify));
    }

    static Stream<FailureCase> normalizedFailures() {
        return Stream.of(
                geminiFailure("Gemini authentication", new ClientException(401, "UNAUTHENTICATED", RAW_PROVIDER_DETAIL),
                        ProviderFailureType.AUTHENTICATION_FAILURE),
                geminiFailure("Gemini quota", new ClientException(402, "PAYMENT_REQUIRED", RAW_PROVIDER_DETAIL),
                        ProviderFailureType.QUOTA_EXHAUSTED),
                geminiFailure("Gemini rate limit", new ClientException(429, "RESOURCE_EXHAUSTED", RAW_PROVIDER_DETAIL),
                        ProviderFailureType.RATE_LIMITED),
                geminiFailure("Gemini timeout", new IllegalStateException(
                                RAW_PROVIDER_DETAIL, new SocketTimeoutException(RAW_PROVIDER_DETAIL)),
                        ProviderFailureType.TIMEOUT),
                geminiFailure("Gemini unavailable", new ServerException(503, "UNAVAILABLE", RAW_PROVIDER_DETAIL),
                        ProviderFailureType.PROVIDER_UNAVAILABLE),
                ollamaHttpFailure("Ollama authentication", 401, ProviderFailureType.AUTHENTICATION_FAILURE),
                ollamaHttpFailure("Ollama quota", 402, ProviderFailureType.QUOTA_EXHAUSTED),
                ollamaHttpFailure("Ollama rate limit", 429, ProviderFailureType.RATE_LIMITED),
                ollamaTimeoutFailure(),
                ollamaHttpFailure("Ollama unavailable", 503, ProviderFailureType.PROVIDER_UNAVAILABLE));
    }

    static Stream<ProviderExecutionResult> liveSmokeValidationFailures() {
        return Stream.of(
                new ProviderExecutionResult(
                        ProviderId.GEMINI,
                        "gemini-live",
                        "{\"unexpected\":\"" + RAW_PROVIDER_DETAIL + "\"}",
                        ProviderUsage.NONE,
                        Duration.ZERO),
                new ProviderExecutionResult(
                        ProviderId.OLLAMA_CLOUD,
                        "ollama-live",
                        "{\"unexpected\":\"" + RAW_PROVIDER_DETAIL + "\"}",
                        ProviderUsage.NONE,
                        Duration.ZERO));
    }

    static Stream<FailureCase> unsupportedRoutes() {
        return Stream.of(
                new FailureCase(
                        "Gemini unsupported route", ProviderId.GEMINI,
                        new GeminiProviderAdapter(mock(ChatModel.class)),
                        ProviderTestFixtures.request(ProviderId.OLLAMA_CLOUD, "wrong-provider"),
                        ProviderFailureType.UNSUPPORTED_TASK, () -> {}),
                new FailureCase(
                        "Ollama unsupported route", ProviderId.OLLAMA_CLOUD,
                        new OllamaCloudProviderAdapter(RestClient.create()),
                        ProviderTestFixtures.request(ProviderId.GEMINI, "wrong-provider"),
                        ProviderFailureType.UNSUPPORTED_TASK, () -> {}));
    }

    private static FailureCase geminiFailure(
            String name, RuntimeException providerFailure, ProviderFailureType expectedFailure) {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenThrow(new IllegalStateException(RAW_PROVIDER_DETAIL, providerFailure));
        return new FailureCase(
                name, ProviderId.GEMINI, new GeminiProviderAdapter(chatModel),
                ProviderTestFixtures.request(ProviderId.GEMINI, "gemini-selected"),
                expectedFailure, () -> {});
    }

    private static FailureCase ollamaHttpFailure(
            String name, int status, ProviderFailureType expectedFailure) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ollama.com/api");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://ollama.com/api/chat"))
                .andRespond(withRawStatus(status).body(RAW_PROVIDER_DETAIL).contentType(MediaType.TEXT_PLAIN));
        return new FailureCase(
                name, ProviderId.OLLAMA_CLOUD, new OllamaCloudProviderAdapter(builder.build()),
                ProviderTestFixtures.request(ProviderId.OLLAMA_CLOUD, "ollama-selected"),
                expectedFailure, server::verify);
    }

    private static FailureCase ollamaTimeoutFailure() {
        ClientHttpRequestFactory requestFactory = (uri, method) -> {
            throw new ResourceAccessException(RAW_PROVIDER_DETAIL, new SocketTimeoutException(RAW_PROVIDER_DETAIL));
        };
        return new FailureCase(
                "Ollama timeout", ProviderId.OLLAMA_CLOUD,
                new OllamaCloudProviderAdapter(RestClient.builder().requestFactory(requestFactory).build()),
                ProviderTestFixtures.request(ProviderId.OLLAMA_CLOUD, "ollama-selected"),
                ProviderFailureType.TIMEOUT, () -> {});
    }

    record AdapterCase(
            String name,
            AiProviderAdapter adapter,
            ProviderExecutionRequest request,
            String expectedModelId,
            int expectedInputTokens,
            int expectedOutputTokens,
            Runnable verifier) {
        void verify() {
            verifier.run();
        }

        @Override
        public String toString() {
            return name;
        }
    }

    record FailureCase(
            String name,
            ProviderId providerId,
            AiProviderAdapter adapter,
            ProviderExecutionRequest request,
            ProviderFailureType expectedFailure,
            Runnable verifier) {
        void verify() {
            verifier.run();
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
