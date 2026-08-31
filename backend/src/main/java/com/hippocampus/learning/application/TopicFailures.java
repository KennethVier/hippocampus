package com.hippocampus.learning.application;

import com.hippocampus.shared.application.error.ApplicationNotFoundException;
import com.hippocampus.shared.domain.error.ErrorCode;

final class TopicFailures {
    private static final ErrorCode NOT_FOUND = new ErrorCode("TOPIC_NOT_FOUND");
    private TopicFailures() {}
    static ApplicationNotFoundException notFound() {
        return new ApplicationNotFoundException(NOT_FOUND, "Topic was not found.");
    }
}
