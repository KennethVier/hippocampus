package com.hippocampus.rag.infrastructure.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions;

import com.hippocampus.rag.port.EmbeddingBatchRequest;
import com.hippocampus.rag.port.EmbeddingBatchResult;
import com.hippocampus.rag.port.EmbeddingInput;
import com.hippocampus.rag.port.EmbeddingPort;

class GeminiEmbeddingLiveSmokeTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "HIPPOCAMPUS_GEMINI_LIVE_TEST", matches = "(?i)true")
    void embedsTinyBatchThroughRealPort() {
        String apiKey = System.getenv("GEMINI_API_KEY");
        assertThat(apiKey).as("GEMINI_API_KEY must be injected for the authorized live smoke").isNotBlank();

        GoogleGenAiEmbeddingConnectionDetails connection = GoogleGenAiEmbeddingConnectionDetails.builder()
                .apiKey(apiKey)
                .build();
        GoogleGenAiTextEmbeddingOptions options = GoogleGenAiTextEmbeddingOptions.builder()
                .model("gemini-embedding-2")
                .dimensions(768)
                .build();
        EmbeddingModel model = new GoogleGenAiTextEmbeddingModel(connection, options);
        EmbeddingPort port = new GeminiEmbeddingAdapter(model, "gemini-embedding-2", 768);
        UUID first = UUID.fromString("30000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("30000000-0000-0000-0000-000000000002");

        EmbeddingBatchResult result = port.embed(new EmbeddingBatchRequest(List.of(
                new EmbeddingInput(first, "radial nerve"),
                new EmbeddingInput(second, "wrist drop"))));

        assertThat(result.vectors()).extracting(vector -> vector.referenceId()).containsExactly(first, second);
        assertThat(result.model().model()).isNotBlank();
        assertThat(result.model().dimension()).isEqualTo(768);
        assertThat(result.vectors()).allSatisfy(vector -> {
            assertThat(vector.vector().values()).hasSize(768).allMatch(Float::isFinite);
        });
    }
}
