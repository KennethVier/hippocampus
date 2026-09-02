package com.hippocampus.materials.port;

import java.io.InputStream;

public interface MaterialContentInspector {

    Inspection inspect(InputStream source, long contentLength);

    record Inspection(String mimeType) {
        public Inspection {
            if (mimeType == null || mimeType.isBlank()) {
                throw new IllegalArgumentException("mimeType must not be blank");
            }
            mimeType = mimeType.strip();
        }
    }
}
