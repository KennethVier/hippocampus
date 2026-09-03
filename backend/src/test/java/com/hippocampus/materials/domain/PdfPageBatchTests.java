package com.hippocampus.materials.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class PdfPageBatchTests {
    @Test
    void defensivelyCopiesAnOrderedContiguousBatch() {
        List<PdfNativePage> mutable = new ArrayList<>(List.of(page(4), page(5)));
        PdfPageBatch batch = new PdfPageBatch(4, 5, mutable);
        mutable.clear();

        assertThatThrownBy(() -> batch.pages().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsEmptyMismatchedAndNonContiguousBatches() {
        assertThatThrownBy(() -> new PdfPageBatch(1, 1, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PdfPageBatch(1, 2, List.of(page(1))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PdfPageBatch(1, 2, List.of(page(1), page(3))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static PdfNativePage page(int number) {
        return new PdfNativePage(number, 612, 792, "");
    }
}
