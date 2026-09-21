package com.hippocampus.ai.application.provider;

public enum ProviderFailureType {
    PROVIDER_UNAVAILABLE,
    RATE_LIMITED,
    QUOTA_EXHAUSTED,
    TIMEOUT,
    INVALID_RESPONSE,
    AUTHENTICATION_FAILURE,
    UNSUPPORTED_TASK
}
