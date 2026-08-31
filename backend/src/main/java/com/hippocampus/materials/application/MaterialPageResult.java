package com.hippocampus.materials.application;

import java.util.List;

import com.hippocampus.materials.port.MaterialPage;

public record MaterialPageResult(
        List<MaterialResult> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public MaterialPageResult {
        items = List.copyOf(items);
    }

    public static MaterialPageResult from(MaterialPage page) {
        return new MaterialPageResult(
                page.items().stream().map(MaterialResult::from).toList(),
                page.page(), page.size(), page.totalElements(), page.totalPages());
    }
}
