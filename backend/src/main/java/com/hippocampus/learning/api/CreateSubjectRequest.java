package com.hippocampus.learning.api;

import jakarta.validation.constraints.NotBlank;

public record CreateSubjectRequest(@NotBlank String name, String description, Integer sortOrder) {}
