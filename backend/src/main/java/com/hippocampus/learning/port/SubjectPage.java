package com.hippocampus.learning.port;

import java.util.List;

import com.hippocampus.learning.domain.Subject;

public record SubjectPage(List<Subject> items, int page, int size, long totalElements, int totalPages) {
    public SubjectPage {
        items = List.copyOf(items);
    }
}
