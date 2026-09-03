package com.hippocampus.materials.infrastructure.pdf;

import java.io.IOException;
import java.nio.file.Path;

interface PdfTemporaryFiles {
    Path create() throws IOException;

    void delete(Path path) throws IOException;
}
