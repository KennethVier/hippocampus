package com.hippocampus.materials.domain;

import java.util.List;
import java.util.Objects;

public record PdfTablePage(int pageNumber, int pageCount, List<ExtractedPdfTable> tables) {
    public PdfTablePage {
        if (pageNumber < 1 || pageCount < pageNumber) {
            throw new IllegalArgumentException("page location must be positive and bounded");
        }
        tables = List.copyOf(Objects.requireNonNull(tables));
    }
}
