package com.hippocampus.rag.api;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record RetrievalInspectionRequest(
        @NotNull UUID topicId,
        @NotBlank @Size(max = 1000) String query,
        @NotNull @Pattern(regexp = "GENERAL_KNOWLEDGE|SOURCE_FIRST|STRICT_SOURCE") String groundingMode) {
}
