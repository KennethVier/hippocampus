package com.hippocampus.learning.port;

import java.util.List;
import com.hippocampus.learning.domain.Subtopic;

public record SubtopicPage(List<Subtopic> items, int page, int size, long totalElements, int totalPages) {
    public SubtopicPage { items = List.copyOf(items); }
}
