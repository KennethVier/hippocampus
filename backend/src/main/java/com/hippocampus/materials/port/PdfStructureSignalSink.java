package com.hippocampus.materials.port;

import com.hippocampus.materials.domain.PdfStructureSignals;

public interface PdfStructureSignalSink {
    void acceptDocument(PdfStructureSignals.Document document);

    void acceptPages(PdfStructureSignals.PageBatch pages);
}
