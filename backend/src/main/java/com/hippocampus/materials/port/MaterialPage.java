package com.hippocampus.materials.port;

import java.util.List;

public record MaterialPage(
        List<MaterialMetadata> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public MaterialPage {
        items = List.copyOf(items);
    }
}
