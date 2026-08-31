package com.hippocampus.learning.api;
import jakarta.validation.constraints.NotBlank;
public record UpdateSubtopicRequest(@NotBlank String name,String description,Integer sortOrder) {}
