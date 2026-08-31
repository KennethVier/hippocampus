package com.hippocampus.learning.api;
import jakarta.validation.constraints.NotBlank;
public record CreateSubtopicRequest(@NotBlank String name,String description,Integer sortOrder) {}
