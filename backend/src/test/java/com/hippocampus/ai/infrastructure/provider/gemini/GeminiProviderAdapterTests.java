package com.hippocampus.ai.infrastructure.provider.gemini;

import static com.hippocampus.ai.infrastructure.provider.ProviderTestFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

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

import com.hippocampus.ai.application.provider.ProviderExecutionException;
import com.hippocampus.ai.application.provider.ProviderExecutionResult;
import com.hippocampus.ai.application.provider.ProviderFailureType;
import com.hippocampus.ai.application.routing.ProviderId;

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
}
