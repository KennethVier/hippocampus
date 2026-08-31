package com.hippocampus.learning.port;

import java.util.List;
import com.hippocampus.learning.domain.Topic;

public record TopicPage(List<Topic> items, int page, int size, long totalElements, int totalPages) {
    public TopicPage { items = List.copyOf(items); }
}
