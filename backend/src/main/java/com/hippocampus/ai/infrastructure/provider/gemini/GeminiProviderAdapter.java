package com.hippocampus.ai.infrastructure.provider.gemini;

import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.client.HttpStatusCodeException;

import com.hippocampus.ai.application.provider.AiProviderAdapter;
import com.hippocampus.ai.application.provider.ProviderExecutionException;
import com.hippocampus.ai.application.provider.ProviderExecutionRequest;
import com.hippocampus.ai.application.provider.ProviderExecutionResult;
import com.hippocampus.ai.application.provider.ProviderFailureType;
import com.hippocampus.ai.application.provider.ProviderUsage;
import com.hippocampus.ai.application.routing.ProviderId;
import com.hippocampus.ai.domain.AiTaskType;

public final class GeminiProviderAdapter implements AiProviderAdapter {
    private static final String JSON_MIME_TYPE = "application/json";

    private final ChatModel chatModel;

    public GeminiProviderAdapter(ChatModel chatModel) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel must not be null");
    }

    @Override
    public ProviderId providerId() {
        return ProviderId.GEMINI;
    }

    @Override
    public boolean supports(AiTaskType taskType) {
        return taskType != null;
    }

    @Override
    public ProviderExecutionResult execute(ProviderExecutionRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        validateRequest(request);

        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
                .model(request.target().modelId())
                .maxOutputTokens(request.promptContext().reservedOutputTokens())
                .responseMimeType(JSON_MIME_TYPE)
                .build();
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(request.promptContext().systemPrompt()),
                new UserMessage(request.promptContext().taskPrompt())), options);

        long startedAt = System.nanoTime();
        try {
            ChatResponse response = chatModel.call(prompt);
            Duration latency = Duration.ofNanos(System.nanoTime() - startedAt);
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                throw failure(ProviderFailureType.INVALID_RESPONSE);
            }
            String rawContent = response.getResult().getOutput().getText();
            if (rawContent == null || rawContent.isBlank()) {
                throw failure(ProviderFailureType.INVALID_RESPONSE);
            }
            return new ProviderExecutionResult(
                    providerId(),
                    responseModelId(response, request.target().modelId()),
                    rawContent,
                    mapUsage(response),
                    latency);
        } catch (ProviderExecutionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw failure(classify(exception));
        }
    }

    private void validateRequest(ProviderExecutionRequest request) {
        if (request.target().providerId() != providerId() || !supports(request.taskType())) {
            throw failure(ProviderFailureType.UNSUPPORTED_TASK);
        }
    }

    private static ProviderUsage mapUsage(ChatResponse response) {
        if (response.getMetadata() == null
                || response.getMetadata().getUsage() == null
                || response.getMetadata().getUsage() instanceof EmptyUsage) {
            return ProviderUsage.NONE;
        }
        Usage usage = response.getMetadata().getUsage();
        return ProviderUsage.of(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
    }

    private static String responseModelId(ChatResponse response, String configuredModelId) {
        if (response.getMetadata() == null
                || response.getMetadata().getModel() == null
                || response.getMetadata().getModel().isBlank()) {
            return configuredModelId;
        }
        return response.getMetadata().getModel();
    }

    private static ProviderFailureType classify(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof HttpTimeoutException || current instanceof java.net.SocketTimeoutException) {
                return ProviderFailureType.TIMEOUT;
            }
            if (current instanceof HttpStatusCodeException statusFailure) {
                int status = statusFailure.getStatusCode().value();
                if (status == 401 || status == 403) {
                    return ProviderFailureType.AUTHENTICATION_FAILURE;
                }
                if (status == 429) {
                    return ProviderFailureType.RATE_LIMITED;
                }
                if (status >= 500) {
                    return ProviderFailureType.PROVIDER_UNAVAILABLE;
                }
            }
        }
        return ProviderFailureType.PROVIDER_UNAVAILABLE;
    }

    private static ProviderExecutionException failure(ProviderFailureType type) {
        return new ProviderExecutionException(ProviderId.GEMINI, type);
    }
}
