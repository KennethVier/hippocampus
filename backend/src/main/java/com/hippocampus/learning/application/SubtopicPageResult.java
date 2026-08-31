package com.hippocampus.learning.application;

import java.util.List;
import com.hippocampus.learning.port.SubtopicPage;

public record SubtopicPageResult(List<SubtopicResult> items, int page, int size, long totalElements, int totalPages) {
    public SubtopicPageResult { items = List.copyOf(items); }
    static SubtopicPageResult from(SubtopicPage page) {
        return new SubtopicPageResult(page.items().stream().map(SubtopicResult::from).toList(), page.page(), page.size(),
                page.totalElements(), page.totalPages());
    }
}
