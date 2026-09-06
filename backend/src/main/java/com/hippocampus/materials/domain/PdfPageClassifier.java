package com.hippocampus.materials.domain;

import java.util.Objects;

public final class PdfPageClassifier {
    /*
     * Implementation heuristic: on image-bearing pages, eight meaningful code
     * points distinguish useful native text from small scanner/page overlays.
     * Text-only pages need just one meaningful code point so short notation is
     * not discarded. A meaningful code point is neither whitespace nor control.
     */
    static final int MINIMUM_USEFUL_CODE_POINTS_ON_IMAGE_PAGE = 8;

    public PdfPageExtractionType classify(String nativeText, boolean hasPaintedImage) {
        Objects.requireNonNull(nativeText, "nativeText must not be null");
        long meaningfulCodePoints = nativeText.codePoints()
                .filter(PdfPageClassifier::isMeaningful)
                .limit(MINIMUM_USEFUL_CODE_POINTS_ON_IMAGE_PAGE)
                .count();

        boolean hasUsefulNativeText = meaningfulCodePoints >= (hasPaintedImage
                ? MINIMUM_USEFUL_CODE_POINTS_ON_IMAGE_PAGE
                : 1);
        if (hasUsefulNativeText) {
            return hasPaintedImage ? PdfPageExtractionType.MIXED : PdfPageExtractionType.NATIVE_TEXT;
        }
        return hasPaintedImage ? PdfPageExtractionType.IMAGE_ONLY : PdfPageExtractionType.UNREADABLE;
    }

    private static boolean isMeaningful(int codePoint) {
        if (Character.isWhitespace(codePoint)) {
            return false;
        }
        return switch (Character.getType(codePoint)) {
            case Character.CONTROL, Character.FORMAT, Character.SURROGATE, Character.UNASSIGNED -> false;
            default -> true;
        };
    }
}
