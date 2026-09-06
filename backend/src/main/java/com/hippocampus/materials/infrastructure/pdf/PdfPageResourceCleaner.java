package com.hippocampus.materials.infrastructure.pdf;

import org.apache.pdfbox.pdmodel.PDPage;

@FunctionalInterface
interface PdfPageResourceCleaner {
    void clean(PDPage page);
}
