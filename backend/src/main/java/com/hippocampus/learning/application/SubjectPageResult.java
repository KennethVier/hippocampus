package com.hippocampus.learning.application;

import java.util.List;

import com.hippocampus.learning.port.SubjectPage;

public record SubjectPageResult(
        List<SubjectResult> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public SubjectPageResult {
        items = List.copyOf(items);
    }

    static SubjectPageResult from(SubjectPage page) {
        return new SubjectPageResult(
                page.items().stream().map(SubjectResult::from).toList(),
                page.page(), page.size(), page.totalElements(), page.totalPages());
    }
}
