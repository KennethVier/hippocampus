package com.hippocampus.learning.api;
import jakarta.validation.constraints.NotBlank;
public record CreateTopicRequest(@NotBlank String name,String description) {}
