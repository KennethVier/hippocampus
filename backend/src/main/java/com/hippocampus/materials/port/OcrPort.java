package com.hippocampus.materials.port;

@FunctionalInterface
public interface OcrPort {
    OcrResult recognize(OcrInput input);
}
