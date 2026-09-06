package com.hippocampus.materials.port;

public interface PdfStructureInspector {
    void inspect(PdfExtractionSource source, PdfStructureSignalSink sink);
}
