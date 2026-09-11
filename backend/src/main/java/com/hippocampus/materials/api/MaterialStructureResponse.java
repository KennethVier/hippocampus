package com.hippocampus.materials.api;

import java.util.List;
import java.util.UUID;

public record MaterialStructureResponse(boolean available, MaterialNode root) {
    public record MaterialNode(
            UUID id,
            String nodeType,
            String title,
            Integer startPage,
            Integer endPage,
            List<MaterialNode> children) {}
}