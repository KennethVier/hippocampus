package com.hippocampus.learning.api;

import jakarta.validation.constraints.NotBlank;

public record UpdateSubjectRequest(@NotBlank String name, String description, Integer sortOrder) {}
