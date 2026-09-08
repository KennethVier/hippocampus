package com.hippocampus.materials.port;

import com.hippocampus.materials.domain.PdfTablePage;

@FunctionalInterface
public interface PdfTablePageSink {
    void accept(PdfTablePage page);
}
