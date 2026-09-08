package com.hippocampus.materials.port;

public interface PdfVisualExtractor {
    void extract(PdfExtractionSource source, PdfVisualSink sink);
}
