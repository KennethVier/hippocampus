package com.hippocampus.learning.api;
import jakarta.validation.constraints.NotBlank;
public record UpdateTopicRequest(@NotBlank String name,String description) {}
