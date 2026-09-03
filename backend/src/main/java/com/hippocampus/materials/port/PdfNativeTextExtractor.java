package com.hippocampus.materials.port;

import com.hippocampus.materials.domain.PdfDocumentMetadata;
public interface PdfNativeTextExtractor {
    PdfDocumentMetadata extract(PdfExtractionSource source, PdfPageBatchSink sink);
}
