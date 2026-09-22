package com.hippocampus.ai.application.provider;

import java.util.function.Consumer;

@FunctionalInterface
public interface ProviderEventStream {
    void consume(Consumer<? super ProviderStreamEvent> consumer);
}
