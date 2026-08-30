package com.hippocampus.learning.api;

import java.util.List;

import com.hippocampus.learning.application.SubjectPageResult;

public record SubjectPageResponse(
        List<SubjectResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public SubjectPageResponse {
        items = List.copyOf(items);
    }

    static SubjectPageResponse from(SubjectPageResult result) {
        return new SubjectPageResponse(
                result.items().stream().map(SubjectResponse::from).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
    }
}
