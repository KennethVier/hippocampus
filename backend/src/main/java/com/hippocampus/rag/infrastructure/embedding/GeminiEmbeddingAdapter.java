package com.hippocampus.rag.infrastructure.embedding;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;

import com.hippocampus.rag.port.EmbeddingBatchRequest;
import com.hippocampus.rag.port.EmbeddingBatchResult;
import com.hippocampus.rag.port.EmbeddingFailureException;
import com.hippocampus.rag.port.EmbeddingFailureException.Reason;
import com.hippocampus.rag.port.EmbeddingModelMetadata;
import com.hippocampus.rag.port.EmbeddingPort;
import com.hippocampus.rag.port.EmbeddingUsageMetadata;
import com.hippocampus.rag.port.EmbeddingVector;
import com.hippocampus.rag.port.EmbeddingVectorResult;

public final class GeminiEmbeddingAdapter implements EmbeddingPort {
    static final String PROVIDER = "google-genai";

    private final EmbeddingModel embeddingModel;
    private final String configuredModel;
    private final int configuredDimension;

    public GeminiEmbeddingAdapter(EmbeddingModel embeddingModel, String configuredModel, int configuredDimension) {
        this.embeddingModel = java.util.Objects.requireNonNull(embeddingModel, "embeddingModel must not be null");
        if (configuredModel == null || configuredModel.isBlank() || configuredDimension < 1) {
            throw new IllegalArgumentException("Gemini embedding adapter configuration is invalid");
        }
        this.configuredModel = configuredModel;
        this.configuredDimension = configuredDimension;
    }

    @Override
    public EmbeddingBatchResult embed(EmbeddingBatchRequest request) {
        java.util.Objects.requireNonNull(request, "request must not be null");
        EmbeddingResponse response;
        try {
            List<String> texts = request.inputs().stream().map(input -> input.text()).toList();
            response = embeddingModel.call(new EmbeddingRequest(texts, null));
        }
        catch (RuntimeException exception) {
            throw failure(Reason.PROVIDER_FAILURE);
        }

        try {
            return translate(request, response);
        }
        catch (EmbeddingFailureException exception) {
            throw exception;
        }
        catch (RuntimeException exception) {
            throw failure(Reason.INVALID_RESPONSE);
        }
    }

    private EmbeddingBatchResult translate(EmbeddingBatchRequest request, EmbeddingResponse response) {
        if (response == null || response.getResults() == null
                || response.getResults().size() != request.inputs().size()) {
            throw failure(Reason.INVALID_RESPONSE);
        }

        List<EmbeddingVectorResult> correlated = new ArrayList<>(java.util.Collections.nCopies(
                request.inputs().size(), null));
        for (Embedding embedding : response.getResults()) {
            if (embedding == null || embedding.getIndex() == null) {
                throw failure(Reason.INVALID_RESPONSE);
            }
            int index = embedding.getIndex();
            if (index < 0 || index >= request.inputs().size() || correlated.get(index) != null) {
                throw failure(Reason.INVALID_RESPONSE);
            }

            float[] output = embedding.getOutput();
            if (output == null || output.length != configuredDimension) {
                throw failure(Reason.INVALID_RESPONSE);
            }
            EmbeddingVector vector = new EmbeddingVector(box(output));
            correlated.set(index, new EmbeddingVectorResult(request.inputs().get(index).referenceId(), vector));
        }
        if (correlated.stream().anyMatch(java.util.Objects::isNull)) {
            throw failure(Reason.INVALID_RESPONSE);
        }

        EmbeddingResponseMetadata metadata = response.getMetadata();
        String responseModel = metadata == null ? null : metadata.getModel();
        String model = responseModel == null || responseModel.isBlank() ? configuredModel : responseModel;
        return new EmbeddingBatchResult(
                new EmbeddingModelMetadata(PROVIDER, model, null, configuredDimension),
                usage(metadata),
                correlated);
    }

    private static List<Float> box(float[] values) {
        List<Float> boxed = new ArrayList<>(values.length);
        for (float value : values) {
            boxed.add(value);
        }
        return boxed;
    }

    private static EmbeddingUsageMetadata usage(EmbeddingResponseMetadata metadata) {
        Usage providerUsage = metadata == null ? null : metadata.getUsage();
        Integer aggregateEmbeddingTokens = providerUsage == null ? null : providerUsage.getTotalTokens();
        if (aggregateEmbeddingTokens == null) {
            return EmbeddingUsageMetadata.unavailable();
        }
        if (aggregateEmbeddingTokens < 0) {
            throw failure(Reason.INVALID_RESPONSE);
        }
        return new EmbeddingUsageMetadata(aggregateEmbeddingTokens.longValue());
    }

    private static EmbeddingFailureException failure(Reason reason) {
        return new EmbeddingFailureException(reason);
    }
}
