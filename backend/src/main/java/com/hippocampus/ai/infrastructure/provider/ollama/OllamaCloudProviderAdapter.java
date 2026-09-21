package com.hippocampus.ai.infrastructure.provider.ollama;

import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.hippocampus.ai.application.provider.AiProviderAdapter;
import com.hippocampus.ai.application.provider.ProviderExecutionException;
import com.hippocampus.ai.application.provider.ProviderExecutionRequest;
import com.hippocampus.ai.application.provider.ProviderExecutionResult;
import com.hippocampus.ai.application.provider.ProviderFailureType;
import com.hippocampus.ai.application.provider.ProviderUsage;
import com.hippocampus.ai.application.routing.ProviderId;
import com.hippocampus.ai.domain.AiTaskType;

public final class OllamaCloudProviderAdapter implements AiProviderAdapter {
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

        OllamaChatRequest providerRequest = new OllamaChatRequest(
                request.target().modelId(),
                List.of(
                        new OllamaMessage("system", request.promptContext().systemPrompt()),
                        new OllamaMessage("user", request.promptContext().taskPrompt())),
                false,
                "json",
                Map.of("num_predict", request.promptContext().reservedOutputTokens()));

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
            throw failure(classify(exception));
        }
    }

    private void validateRequest(ProviderExecutionRequest request) {
        if (request.target().providerId() != providerId() || !supports(request.taskType())) {
            throw failure(ProviderFailureType.UNSUPPORTED_TASK);
        }
    }

    private static ProviderFailureType classify(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof HttpTimeoutException
                    || current instanceof java.net.SocketTimeoutException
                    || current instanceof ResourceAccessException) {
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
                return ProviderFailureType.INVALID_RESPONSE;
            }
            if (current instanceof RestClientException) {
                return ProviderFailureType.INVALID_RESPONSE;
            }
        }
        return ProviderFailureType.PROVIDER_UNAVAILABLE;
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

    record OllamaMessage(String role, String content) {}

    record OllamaChatResponse(
            String model,
            OllamaMessage message,
            @JsonProperty("prompt_eval_count") Integer promptEvalCount,
            @JsonProperty("eval_count") Integer evalCount) {}
}
