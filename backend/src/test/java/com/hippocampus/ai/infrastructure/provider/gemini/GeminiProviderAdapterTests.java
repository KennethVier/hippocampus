package com.hippocampus.ai.infrastructure.provider.gemini;

import static com.hippocampus.ai.infrastructure.provider.ProviderTestFixtures.liveExplanationRequest;
import static com.hippocampus.ai.infrastructure.provider.ProviderTestFixtures.request;
import static com.hippocampus.ai.infrastructure.provider.ProviderTestFixtures.validateLiveExplanation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;

import com.google.genai.errors.ClientException;
import com.google.genai.errors.ServerException;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import com.hippocampus.ai.application.provider.ProviderExecutionException;
import com.hippocampus.ai.application.provider.ProviderExecutionResult;
import com.hippocampus.ai.application.provider.ProviderFailureType;
import com.hippocampus.ai.application.provider.ProviderStreamCompleted;
import com.hippocampus.ai.application.provider.ProviderStreamEvent;
import com.hippocampus.ai.application.provider.ProviderTextDelta;
import com.hippocampus.ai.application.routing.ProviderId;
import com.hippocampus.ai.domain.AiOutputContract;
import com.hippocampus.ai.domain.ExplanationResult;
import com.hippocampus.ai.domain.ValidatedAiResult;
import com.hippocampus.ai.infrastructure.provider.ProviderStructuredOutputSchema;
import reactor.core.publisher.Flux;

class GeminiProviderAdapterTests {

    @Test
    void mapsCanonicalPromptRouteOptionsRawResultUsageAndLatency() {
        ChatModel chatModel = mock(ChatModel.class);
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .model("gemini-actual")
                .usage(new DefaultUsage(11, 7, 18))
                .build();
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("{\"answer\":\"untrusted\"}"))), metadata));
        GeminiProviderAdapter adapter = new GeminiProviderAdapter(chatModel);

        ProviderExecutionResult result = adapter.execute(request(ProviderId.GEMINI, "gemini-selected"));

        assertThat(adapter.providerId()).isEqualTo(ProviderId.GEMINI);
        assertThat(result.providerId()).isEqualTo(ProviderId.GEMINI);
        assertThat(result.modelId()).isEqualTo("gemini-actual");
        assertThat(result.rawContent()).isEqualTo("{\"answer\":\"untrusted\"}");
        assertThat(result.usage().inputTokens()).contains(11);
        assertThat(result.usage().outputTokens()).contains(7);
        assertThat(result.usage().totalTokens()).contains(18);
        assertThat(result.latency().isNegative()).isFalse();

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        Prompt prompt = promptCaptor.getValue();
        assertThat(prompt.getInstructions()).hasSize(2);
        assertThat(prompt.getInstructions().get(0)).isInstanceOf(SystemMessage.class);
        assertThat(prompt.getInstructions().get(0).getText()).isEqualTo("system-policy-secret-marker");
        assertThat(prompt.getInstructions().get(1)).isInstanceOf(UserMessage.class);
        assertThat(prompt.getInstructions().get(1).getText()).isEqualTo("student-task-secret-marker");
        GoogleGenAiChatOptions options = (GoogleGenAiChatOptions) prompt.getOptions();
        assertThat(options.getModel()).isEqualTo("gemini-selected");
        assertThat(options.getMaxOutputTokens()).isEqualTo(64);
        assertThat(options.getResponseMimeType()).isEqualTo("application/json");
        assertThat(options.getResponseSchema())
                .isEqualTo(ProviderStructuredOutputSchema.geminiSchema(AiOutputContract.EXPLANATION))
                .contains("\"concept\"", "\"supplementalKnowledgeUsed\"", "\"required\"")
                .doesNotContain("additionalProperties");
    }

    @Test
    void liveSmokeFixtureUsesStructuredOutputAndPassesStrictValidation() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(
                new AssistantMessage("""
                        {
                          "concept": "atrioventricular nodal delay",
                          "explanation": "Slow conduction allows the atria to empty before ventricular contraction.",
                          "keyPoints": ["The delay supports sequential chamber contraction"],
                          "prerequisitesUsed": ["cardiac conduction"],
                          "sourceReferences": [],
                          "supplementalKnowledgeUsed": true,
                          "limitations": []
                        }
                        """)))));
        GeminiProviderAdapter adapter = new GeminiProviderAdapter(chatModel);

        ProviderExecutionResult result = adapter.execute(liveExplanationRequest(ProviderId.GEMINI, "gemini-live"));
        ValidatedAiResult<?> validated = validateLiveExplanation(result);

        assertThat(validated.result()).isInstanceOf(ExplanationResult.class);
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        GoogleGenAiChatOptions options = (GoogleGenAiChatOptions) promptCaptor.getValue().getOptions();
        assertThat(options.getResponseSchema())
                .isEqualTo(ProviderStructuredOutputSchema.geminiSchema(AiOutputContract.EXPLANATION));
    }

    @Test
    void keepsAbsentUsageAbsent() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("{}")))));

        ProviderExecutionResult result = new GeminiProviderAdapter(chatModel)
                .execute(request(ProviderId.GEMINI, "gemini-selected"));

        assertThat(result.usage().inputTokens()).isEmpty();
        assertThat(result.usage().outputTokens()).isEmpty();
        assertThat(result.usage().totalTokens()).isEmpty();
    }

    @Test
    void rejectsAnotherProviderRoute() {
        GeminiProviderAdapter adapter = new GeminiProviderAdapter(mock(ChatModel.class));

        assertThatThrownBy(() -> adapter.execute(request(ProviderId.OLLAMA_CLOUD, "wrong")))
                .isInstanceOfSatisfying(ProviderExecutionException.class, failure -> {
                    assertThat(failure.failureType()).isEqualTo(ProviderFailureType.UNSUPPORTED_TASK);
                    assertThat(failure.providerId()).isEqualTo(ProviderId.GEMINI);
                });
    }

    @Test
    void safelyNormalizesProviderFailureWithoutLeakingSecretPromptOrBody() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(new IllegalStateException(
                "api-key-secret system-policy-secret-marker student-task-secret-marker raw-provider-body"));

        assertThatThrownBy(() -> new GeminiProviderAdapter(chatModel)
                        .execute(request(ProviderId.GEMINI, "gemini-selected")))
                .isInstanceOfSatisfying(ProviderExecutionException.class, failure -> {
                    assertThat(failure.failureType()).isEqualTo(ProviderFailureType.PROVIDER_UNAVAILABLE);
                    assertThat(failure).hasNoCause();
                    assertThat(failure.getMessage()).doesNotContain(
                            "api-key-secret", "system-policy-secret-marker", "student-task-secret-marker", "raw-provider-body");
                });
    }

    @Test
    void streamsProviderNeutralTextDeltasAndTerminalMetadata() {
        ChatModel chatModel = mock(ChatModel.class);
        ChatResponseMetadata terminalMetadata = ChatResponseMetadata.builder()
                .model("gemini-actual")
                .usage(new DefaultUsage(8, 3, 11))
                .build();
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage("{\"answer\":")))),
                new ChatResponse(List.of(new Generation(new AssistantMessage("\"untrusted\"}"))), terminalMetadata)));
        List<ProviderStreamEvent> events = new ArrayList<>();

        new GeminiProviderAdapter(chatModel)
                .stream(request(ProviderId.GEMINI, "gemini-selected"))
                .consume(events::add);

        assertThat(events.subList(0, 2)).containsExactly(
                new ProviderTextDelta("{\"answer\":"),
                new ProviderTextDelta("\"untrusted\"}"));
        assertThat(events.get(2)).isInstanceOfSatisfying(ProviderStreamCompleted.class, completed -> {
            assertThat(completed.providerId()).isEqualTo(ProviderId.GEMINI);
            assertThat(completed.modelId()).isEqualTo("gemini-actual");
            assertThat(completed.usage().totalTokens()).contains(11);
        });
    }

    @Test
    void preservesApplicationFailureThrownByStreamConsumer() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage("valid text delta"))))));
        RuntimeException marker = new RuntimeException("consumer-marker");

        assertThatThrownBy(() -> new GeminiProviderAdapter(chatModel)
                        .stream(request(ProviderId.GEMINI, "gemini-selected"))
                        .consume(ignored -> {
                            throw marker;
                        }))
                .isSameAs(marker)
                .isNotInstanceOf(ProviderExecutionException.class);
    }

    @Test
    void classifiesGenAiSdkStatusAndTimeoutFailuresWithoutLeakingDetails() {
        assertSdkFailure(new ClientException(401, "UNAUTHENTICATED", "api-key-secret raw-provider-body"),
                ProviderFailureType.AUTHENTICATION_FAILURE);
        assertSdkFailure(new ClientException(403, "PERMISSION_DENIED", "student-task-secret-marker"),
                ProviderFailureType.AUTHENTICATION_FAILURE);
        assertSdkFailure(new ClientException(402, "PAYMENT_REQUIRED", "quota exhausted raw-provider-body"),
                ProviderFailureType.QUOTA_EXHAUSTED);
        assertSdkFailure(new ClientException(429, "RESOURCE_EXHAUSTED", "raw-provider-body"),
                ProviderFailureType.RATE_LIMITED);
        assertSdkFailure(new ClientException(400, "INVALID_ARGUMENT", "system-policy-secret-marker"),
                ProviderFailureType.INVALID_RESPONSE);
        assertSdkFailure(new ServerException(503, "UNAVAILABLE", "api-key-secret"),
                ProviderFailureType.PROVIDER_UNAVAILABLE);
        assertSdkFailure(new IllegalStateException("wrapper", new SocketTimeoutException("student-task-secret-marker")),
                ProviderFailureType.TIMEOUT);
    }

    @Test
    void preservesNormalizedRetryAfterWithoutLeakingProviderDetails() {
        ChatModel chatModel = mock(ChatModel.class);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "7");
        when(chatModel.call(any(Prompt.class))).thenThrow(HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS,
                "raw-provider-status",
                headers,
                "api-key-secret raw-provider-body".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new GeminiProviderAdapter(chatModel)
                        .execute(request(ProviderId.GEMINI, "gemini-selected")))
                .isInstanceOfSatisfying(ProviderExecutionException.class, failure -> {
                    assertThat(failure.failureType()).isEqualTo(ProviderFailureType.RATE_LIMITED);
                    assertThat(failure.retryAfter()).contains(Duration.ofSeconds(7));
                    assertThat(failure).hasNoCause();
                    assertThat(failure.getMessage()).doesNotContain("api-key-secret", "raw-provider-body");
                });
    }

    @Test
    void saturatesExtremelyLargeRetryAfterDeltaWithoutLeakingProviderDetails() {
        ChatModel chatModel = mock(ChatModel.class);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "999999999999999999999999999999999999999");
        when(chatModel.call(any(Prompt.class))).thenThrow(HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS,
                "raw-provider-status",
                headers,
                "api-key-secret raw-provider-body".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new GeminiProviderAdapter(chatModel)
                        .execute(request(ProviderId.GEMINI, "gemini-selected")))
                .isInstanceOfSatisfying(ProviderExecutionException.class, failure -> {
                    assertThat(failure.failureType()).isEqualTo(ProviderFailureType.RATE_LIMITED);
                    assertThat(failure.retryAfter()).contains(Duration.ofSeconds(Long.MAX_VALUE));
                    assertThat(failure).hasNoCause();
                    assertThat(failure.getMessage()).doesNotContain("api-key-secret", "raw-provider-body");
                });
    }

    @Test
    void normalizesFarFutureRetryAfterDateWithoutLeakingProviderDetails() {
        ChatModel chatModel = mock(ChatModel.class);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "Fri, 31 Dec 9999 23:59:59 GMT");
        when(chatModel.call(any(Prompt.class))).thenThrow(HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS,
                "raw-provider-status",
                headers,
                "api-key-secret raw-provider-body".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new GeminiProviderAdapter(chatModel)
                        .execute(request(ProviderId.GEMINI, "gemini-selected")))
                .isInstanceOfSatisfying(ProviderExecutionException.class, failure -> {
                    assertThat(failure.failureType()).isEqualTo(ProviderFailureType.RATE_LIMITED);
                    assertThat(failure.retryAfter()).hasValueSatisfying(
                            duration -> assertThat(duration).isGreaterThan(Duration.ofDays(365_000)));
                    assertThat(failure).hasNoCause();
                    assertThat(failure.getMessage()).doesNotContain("api-key-secret", "raw-provider-body");
                });
    }

    @Test
    void normalizesStreamingSdkFailureWithoutLeakingDetails() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.error(
                new ClientException(429, "RESOURCE_EXHAUSTED", "api-key-secret student-task-secret-marker")));

        assertThatThrownBy(() -> new GeminiProviderAdapter(chatModel)
                        .stream(request(ProviderId.GEMINI, "gemini-selected"))
                        .consume(ignored -> {}))
                .isInstanceOfSatisfying(ProviderExecutionException.class, failure -> {
                    assertThat(failure.failureType()).isEqualTo(ProviderFailureType.RATE_LIMITED);
                    assertThat(failure).hasNoCause();
                    assertThat(failure.getMessage()).doesNotContain("api-key-secret", "student-task-secret-marker");
                });
    }

    private static void assertSdkFailure(RuntimeException sdkFailure, ProviderFailureType expected) {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("wrapper raw-provider-body", sdkFailure));

        assertThatThrownBy(() -> new GeminiProviderAdapter(chatModel)
                        .execute(request(ProviderId.GEMINI, "gemini-selected")))
                .isInstanceOfSatisfying(ProviderExecutionException.class, failure -> {
                    assertThat(failure.failureType()).isEqualTo(expected);
                    assertThat(failure).hasNoCause();
                    assertThat(failure.getMessage()).doesNotContain(
                            "api-key-secret", "system-policy-secret-marker", "student-task-secret-marker", "raw-provider-body");
                });
    }
}
