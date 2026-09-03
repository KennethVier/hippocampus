package com.hippocampus.materials.domain;

import java.util.List;

public record PdfPageBatch(int firstPage, int lastPage, List<PdfNativePage> pages) {
    public PdfPageBatch {
        pages = List.copyOf(pages);
        if (firstPage < 1 || lastPage < firstPage || pages.isEmpty()) {
            throw new IllegalArgumentException("PDF page batch range must be non-empty and positive");
        }
        if (pages.size() != lastPage - firstPage + 1) {
            throw new IllegalArgumentException("PDF page batch size must match its range");
        }
        for (int index = 0; index < pages.size(); index++) {
            if (pages.get(index).pageNumber() != firstPage + index) {
                throw new IllegalArgumentException("PDF page batch pages must be contiguous and ordered");
            }
        }
    }
}
