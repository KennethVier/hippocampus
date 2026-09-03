package com.hippocampus.materials.port;

import com.hippocampus.materials.domain.PdfPageBatch;

@FunctionalInterface
public interface PdfPageBatchSink {
    void accept(PdfPageBatch batch);
}
