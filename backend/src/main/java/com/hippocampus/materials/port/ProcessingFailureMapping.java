package com.hippocampus.materials.port;

import java.util.Optional;

import com.hippocampus.materials.domain.ProcessingFailure;

@FunctionalInterface
public interface ProcessingFailureMapping {
    Optional<ProcessingFailure> classify(RuntimeException failure);
}
