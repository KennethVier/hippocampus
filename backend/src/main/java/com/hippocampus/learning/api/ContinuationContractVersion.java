package com.hippocampus.learning.api;

import org.springframework.http.ProblemDetail;
import org.springframework.util.MultiValueMap;

enum ContinuationContractVersion {
    LEGACY, ONE, INVALID;

    static ContinuationContractVersion parse(MultiValueMap<String, String> query) {
        var values = query.get("continuationContract");
        if (values == null) return LEGACY;
        return values.size() == 1 && "1".equals(values.getFirst()) ? ONE : INVALID;
    }

    static ProblemDetail invalid() {
        var problem = ProblemDetail.forStatus(400);
        problem.setProperty("code", "INVALID_CONTINUATION_CONTRACT");
        problem.setProperty("message", "The continuation contract version is invalid.");
        return problem;
    }
}
