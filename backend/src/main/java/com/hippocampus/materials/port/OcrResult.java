package com.hippocampus.materials.port;

import java.util.Objects;

import com.hippocampus.materials.domain.TextBlockQuality;

public sealed interface OcrResult permits OcrResult.RecognizedText, OcrResult.NoUsableText {
    record RecognizedText(String text, TextBlockQuality quality) implements OcrResult {
        public RecognizedText {
            Objects.requireNonNull(text, "text must not be null");
            Objects.requireNonNull(quality, "quality must not be null");
            if (text.isBlank()) {
                throw new IllegalArgumentException("recognized text must not be blank");
            }
        }
    }

    record NoUsableText() implements OcrResult {}
}
