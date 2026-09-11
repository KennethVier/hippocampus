package com.hippocampus.materials.port;

/**
 * Performs bounded, provider-neutral inspection of a stored PDF source without
 * extracting or persisting document content.
 */
public interface PdfSourceInspector {

    void inspect(PdfExtractionSource source);
}
