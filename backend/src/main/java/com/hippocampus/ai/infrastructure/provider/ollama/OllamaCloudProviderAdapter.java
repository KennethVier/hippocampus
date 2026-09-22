package com.hippocampus.ai.infrastructure.provider.ollama;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
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

public final class OllamaCloudProviderAdapter implements AiProviderAdapter {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient restClient;

    public OllamaCloudProviderAdapter(RestClient restClient) {
        this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
    }

    @Override
    public ProviderId providerId() {
        return ProviderId.OLLAMA_CLOUD;
    }

    @Override
    public boolean supports(AiTaskType taskType) {
        return taskType != null;
    }

    @Override
    public ProviderExecutionResult execute(ProviderExecutionRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        validateRequest(request);

        OllamaChatRequest providerRequest = providerRequest(request, false);

        long startedAt = System.nanoTime();
        try {
            OllamaChatResponse response = restClient.post()
                    .uri("/chat")
                    .body(providerRequest)
                    .retrieve()
                    .body(OllamaChatResponse.class);
            Duration latency = Duration.ofNanos(System.nanoTime() - startedAt);
            if (response == null || response.message() == null
                    || response.message().content() == null || response.message().content().isBlank()) {
                throw failure(ProviderFailureType.INVALID_RESPONSE);
            }
            String modelId = response.model() == null || response.model().isBlank()
                    ? request.target().modelId()
                    : response.model();
            return new ProviderExecutionResult(
                    providerId(),
                    modelId,
                    response.message().content(),
                    ProviderUsage.of(response.promptEvalCount(), response.evalCount(), null),
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
        OllamaChatRequest providerRequest = providerRequest(request, true);

        return consumer -> consumeStream(
                request,
                providerRequest,
                Objects.requireNonNull(consumer, "consumer must not be null"));
    }

    private void consumeStream(
            ProviderExecutionRequest request,
            OllamaChatRequest providerRequest,
            Consumer<? super ProviderStreamEvent> consumer) {
        long startedAt = System.nanoTime();
        StreamState state = new StreamState(request.target().modelId());
        try {
            restClient.post()
                    .uri("/chat")
                    .body(providerRequest)
                    .exchange((ignored, response) -> {
                        if (response.getStatusCode().isError()) {
                            throw new ProviderExecutionException(
                                    ProviderId.OLLAMA_CLOUD,
                                    classifyStatus(response.getStatusCode().value()),
                                    retryAfter(response.getHeaders()));
                        }
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.getBody(), java.nio.charset.StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (!line.isBlank()) {
                                    mapStreamResponse(line, state, consumer, startedAt);
                                }
                            }
                        }
                        return null;
                    });
            if (!state.completed) {
                throw failure(ProviderFailureType.INVALID_RESPONSE);
            }
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

    private void mapStreamResponse(
            String line,
            StreamState state,
            Consumer<? super ProviderStreamEvent> consumer,
            long startedAt) {
        OllamaChatResponse response;
        try {
            response = OBJECT_MAPPER.readValue(line, OllamaChatResponse.class);
        } catch (JsonProcessingException exception) {
            throw failure(ProviderFailureType.INVALID_RESPONSE);
        }
        if (response.model() != null && !response.model().isBlank()) {
            state.modelId = response.model();
        }
        if (response.message() != null
                && response.message().content() != null
                && !response.message().content().isEmpty()) {
            state.textReceived = true;
            emit(consumer, new ProviderTextDelta(response.message().content()));
        }
        if (Boolean.TRUE.equals(response.done())) {
            if (!state.textReceived || state.completed) {
                throw failure(ProviderFailureType.INVALID_RESPONSE);
            }
            state.completed = true;
            emit(consumer, new ProviderStreamCompleted(
                    providerId(),
                    state.modelId,
                    ProviderUsage.of(response.promptEvalCount(), response.evalCount(), null),
                    Duration.ofNanos(System.nanoTime() - startedAt),
                    Optional.ofNullable(response.doneReason())));
        }
    }

    private static OllamaChatRequest providerRequest(ProviderExecutionRequest request, boolean stream) {
        return new OllamaChatRequest(
                request.target().modelId(),
                List.of(
                        new OllamaMessage("system", request.promptContext().systemPrompt()),
                        new OllamaMessage("user", request.promptContext().taskPrompt())),
                stream,
                "json",
                Map.of("num_predict", request.promptContext().reservedOutputTokens()));
    }

    private void validateRequest(ProviderExecutionRequest request) {
        if (request.target().providerId() != providerId() || !supports(request.taskType())) {
            throw failure(ProviderFailureType.UNSUPPORTED_TASK);
        }
    }

    private static ProviderFailureType classify(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof HttpTimeoutException || current instanceof java.net.SocketTimeoutException) {
                return ProviderFailureType.TIMEOUT;
            }
        }
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof ResourceAccessException) {
                return ProviderFailureType.PROVIDER_UNAVAILABLE;
            }
            if (current instanceof HttpStatusCodeException statusFailure) {
                return classifyStatus(statusFailure.getStatusCode().value());
            }
            if (current instanceof RestClientException) {
                return ProviderFailureType.INVALID_RESPONSE;
            }
        }
        return ProviderFailureType.PROVIDER_UNAVAILABLE;
    }

    private static ProviderExecutionException normalizedFailure(Throwable failure) {
        ProviderFailureType type = classify(failure);
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof HttpStatusCodeException statusFailure) {
                return new ProviderExecutionException(
                        ProviderId.OLLAMA_CLOUD, type, retryAfter(statusFailure.getResponseHeaders()));
            }
        }
        return failure(type);
    }

    private static Optional<Duration> retryAfter(HttpHeaders headers) {
        if (headers == null) return Optional.empty();
        String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null || value.isBlank()) return Optional.empty();
        try {
            long seconds = Long.parseLong(value.trim());
            return seconds > 0 ? Optional.of(Duration.ofSeconds(seconds)) : Optional.empty();
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
        if (status == 429) {
            return ProviderFailureType.RATE_LIMITED;
        }
        if (status >= 500) {
            return ProviderFailureType.PROVIDER_UNAVAILABLE;
        }
        return ProviderFailureType.INVALID_RESPONSE;
    }

    private static ProviderExecutionException failure(ProviderFailureType type) {
        return new ProviderExecutionException(ProviderId.OLLAMA_CLOUD, type);
    }

    record OllamaChatRequest(
            String model,
            List<OllamaMessage> messages,
            boolean stream,
            String format,
            Map<String, Integer> options) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OllamaMessage(String role, String content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OllamaChatResponse(
            String model,
            OllamaMessage message,
            Boolean done,
            @JsonProperty("done_reason") String doneReason,
            @JsonProperty("prompt_eval_count") Integer promptEvalCount,
            @JsonProperty("eval_count") Integer evalCount) {}

    private static final class StreamState {
        private String modelId;
        private boolean textReceived;
        private boolean completed;

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
