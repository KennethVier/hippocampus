package com.hippocampus.ai.application.provider;

public sealed interface ProviderStreamEvent permits ProviderTextDelta, ProviderStreamCompleted {}
