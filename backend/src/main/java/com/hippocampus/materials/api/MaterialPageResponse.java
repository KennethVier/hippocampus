package com.hippocampus.materials.api;

import java.util.List;

import com.hippocampus.materials.application.MaterialPageResult;

public record MaterialPageResponse(
        List<MaterialResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public MaterialPageResponse {
        items = List.copyOf(items);
    }

    static MaterialPageResponse from(MaterialPageResult result) {
        return new MaterialPageResponse(
                result.items().stream().map(MaterialResponse::from).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
    }
}
