package com.hippocampus.materials.port;

public interface PdfTableExtractor {
    int extract(PdfExtractionSource source, PdfTablePageSink sink);
}
