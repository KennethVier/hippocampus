package com.hippocampus.learning.application;

import java.util.List;
import com.hippocampus.learning.port.TopicPage;

public record TopicPageResult(List<TopicResult> items, int page, int size, long totalElements, int totalPages) {
    public TopicPageResult { items = List.copyOf(items); }
    static TopicPageResult from(TopicPage page) {
        return new TopicPageResult(page.items().stream().map(TopicResult::from).toList(), page.page(), page.size(),
                page.totalElements(), page.totalPages());
    }
}
