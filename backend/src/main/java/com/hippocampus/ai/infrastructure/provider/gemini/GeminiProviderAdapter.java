package com.hippocampus.ai.infrastructure.provider.gemini;

import java.math.BigInteger;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import com.google.genai.errors.ApiException;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.http.HttpHeaders;

import com.hippocampus.ai.application.provider.AiProviderAdapter;
import com.hippocampus.ai.application.provider.ProviderExecutionException;
import com.hippocampus.ai.application.provider.ProviderExecutionRequest;
import com.hippocampus.ai.application.provider.ProviderExecutionResult;
import com.hippocampus.ai.application.provider.ProviderEventStream;
import com.hippocampus.ai.application.provider.ProviderFailureType;
import com.hippocampus.ai.application.provider.ProviderStreamCompleted;
import com.hippocampus.ai.application.provider.ProviderStreamEvent;
import com.hippocampus.ai.application.provider.ProviderTextDelta;
import com.hippocampus.ai.application.provider.ProviderUsage;
import com.hippocampus.ai.application.routing.ProviderId;
import com.hippocampus.ai.domain.AiTaskType;
import com.hippocampus.ai.infrastructure.provider.ProviderStructuredOutputSchema;

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

        Prompt prompt = prompt(request);

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
            throw normalizedFailure(exception);
        }
    }

    @Override
    public ProviderEventStream stream(ProviderExecutionRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        validateRequest(request);
        Prompt prompt = prompt(request);

        return consumer -> consumeStream(request, prompt, Objects.requireNonNull(consumer, "consumer must not be null"));
    }

    private void consumeStream(
            ProviderExecutionRequest request,
            Prompt prompt,
            Consumer<? super ProviderStreamEvent> consumer) {
        long startedAt = System.nanoTime();
        StreamState state = new StreamState(request.target().modelId());
        try {
            chatModel.stream(prompt).doOnNext(response -> mapStreamResponse(response, state, consumer)).blockLast();
            if (!state.textReceived) {
                throw failure(ProviderFailureType.INVALID_RESPONSE);
            }
            emit(consumer, new ProviderStreamCompleted(
                    providerId(),
                    state.modelId,
                    state.usage,
                    Duration.ofNanos(System.nanoTime() - startedAt),
                    Optional.ofNullable(state.finishReason)));
        } catch (ProviderExecutionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            RuntimeException consumerFailure = consumerFailure(exception);
            if (consumerFailure != null) {
                throw consumerFailure;
            }
            throw normalizedFailure(exception);
        }
    }

    private static void mapStreamResponse(
            ChatResponse response,
            StreamState state,
            Consumer<? super ProviderStreamEvent> consumer) {
        if (response == null) {
            throw failure(ProviderFailureType.INVALID_RESPONSE);
        }
        state.modelId = responseModelId(response, state.modelId);
        ProviderUsage usage = mapUsage(response);
        if (!usage.equals(ProviderUsage.NONE)) {
            state.usage = usage;
        }
        if (response.getResult() == null || response.getResult().getOutput() == null) {
            return;
        }
        String text = response.getResult().getOutput().getText();
        if (text != null && !text.isEmpty()) {
            state.textReceived = true;
            emit(consumer, new ProviderTextDelta(text));
        }
        if (response.getResult().getMetadata() != null) {
            state.finishReason = response.getResult().getMetadata().getFinishReason();
        }
    }

    private static Prompt prompt(ProviderExecutionRequest request) {
        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
                .model(request.target().modelId())
                .maxOutputTokens(request.promptContext().reservedOutputTokens())
                .responseMimeType(JSON_MIME_TYPE)
                .responseSchema(ProviderStructuredOutputSchema.geminiSchema(request.outputContract()))
                .build();
        return new Prompt(List.of(
                new SystemMessage(request.promptContext().systemPrompt()),
                new UserMessage(request.promptContext().taskPrompt())), options);
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
            if (current instanceof TimeoutException) {
                return ProviderFailureType.TIMEOUT;
            }
            if (current instanceof ApiException apiFailure) {
                return classifyStatus(apiFailure.code());
            }
            if (current instanceof HttpStatusCodeException statusFailure) {
                return classifyStatus(statusFailure.getStatusCode().value());
            }
        }
        return ProviderFailureType.PROVIDER_UNAVAILABLE;
    }

    private static ProviderExecutionException normalizedFailure(Throwable failure) {
        ProviderFailureType type = classify(failure);
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof HttpStatusCodeException statusFailure) {
                return new ProviderExecutionException(
                        ProviderId.GEMINI, type, retryAfter(statusFailure.getResponseHeaders()));
            }
        }
        return failure(type);
    }

    private static Optional<Duration> retryAfter(HttpHeaders headers) {
        if (headers == null) return Optional.empty();
        String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null || value.isBlank()) return Optional.empty();
        try {
            BigInteger seconds = new BigInteger(value.trim());
            if (seconds.signum() <= 0) return Optional.empty();
            long saturatedSeconds = seconds.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
            return Optional.of(Duration.ofSeconds(saturatedSeconds));
        } catch (NumberFormatException ignored) {
            try {
                Duration duration = Duration.between(
                        Instant.now(), ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
                return duration.isPositive() ? Optional.of(duration) : Optional.empty();
            } catch (RuntimeException invalidDate) {
                return Optional.empty();
            }
        }
    }

    private static void emit(
            Consumer<? super ProviderStreamEvent> consumer,
            ProviderStreamEvent event) {
        try {
            consumer.accept(event);
        } catch (RuntimeException exception) {
            throw new ConsumerDeliveryException(exception);
        }
    }

    private static RuntimeException consumerFailure(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof ConsumerDeliveryException deliveryFailure) {
                return deliveryFailure.consumerFailure;
            }
        }
        return null;
    }

    private static ProviderFailureType classifyStatus(int status) {
        if (status == 401 || status == 403) {
            return ProviderFailureType.AUTHENTICATION_FAILURE;
        }
        if (status == 402) {
            return ProviderFailureType.QUOTA_EXHAUSTED;
        }
        if (status == 429) {
            return ProviderFailureType.RATE_LIMITED;
        }
        if (status >= 500) {
            return ProviderFailureType.PROVIDER_UNAVAILABLE;
        }
        if (status >= 400) {
            return ProviderFailureType.INVALID_RESPONSE;
        }
        return ProviderFailureType.PROVIDER_UNAVAILABLE;
    }

    private static ProviderExecutionException failure(ProviderFailureType type) {
        return new ProviderExecutionException(ProviderId.GEMINI, type);
    }

    private static final class StreamState {
        private String modelId;
        private ProviderUsage usage = ProviderUsage.NONE;
        private String finishReason;
        private boolean textReceived;

        private StreamState(String modelId) {
            this.modelId = modelId;
        }
    }

    private static final class ConsumerDeliveryException extends RuntimeException {
        private final RuntimeException consumerFailure;

        private ConsumerDeliveryException(RuntimeException consumerFailure) {
            super(consumerFailure);
            this.consumerFailure = consumerFailure;
        }
    }
}
