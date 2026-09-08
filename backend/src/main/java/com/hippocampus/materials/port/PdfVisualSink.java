package com.hippocampus.materials.port;

import com.hippocampus.materials.domain.ExtractedPdfVisual;

@FunctionalInterface
public interface PdfVisualSink {
    void accept(ExtractedPdfVisual visual);
}
