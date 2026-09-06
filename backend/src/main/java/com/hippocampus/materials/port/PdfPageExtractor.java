package com.hippocampus.materials.port;

import com.hippocampus.materials.domain.PdfDocumentMetadata;
public interface PdfPageExtractor {
    PdfDocumentMetadata extract(PdfExtractionSource source, PdfPageBatchSink sink);
}
