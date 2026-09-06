package com.hippocampus.materials.infrastructure.ocr;

import com.hippocampus.materials.domain.TextBlockQuality;

final class TesseractQualityClassifier {
    // Calibrated implementation heuristics, not medical-correctness thresholds.
    static final double STRONG_MINIMUM_CONFIDENCE = 85.0;
    static final double LIMITED_MINIMUM_CONFIDENCE = 60.0;

    TextBlockQuality classify(double meanWordConfidence) {
        if (!Double.isFinite(meanWordConfidence) || meanWordConfidence < 0 || meanWordConfidence > 100) {
            throw new IllegalArgumentException("mean confidence must be between 0 and 100");
        }
        if (meanWordConfidence >= STRONG_MINIMUM_CONFIDENCE) {
            return TextBlockQuality.STRONG;
        }
        if (meanWordConfidence >= LIMITED_MINIMUM_CONFIDENCE) {
            return TextBlockQuality.LIMITED;
        }
        return TextBlockQuality.POOR;
    }
}
