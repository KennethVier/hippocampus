package com.hippocampus.materials.application;

public final class MaterialTopicLinkAlreadyActiveException extends RuntimeException {

    public MaterialTopicLinkAlreadyActiveException() {
        super("An active link already exists for this target.");
    }
}
