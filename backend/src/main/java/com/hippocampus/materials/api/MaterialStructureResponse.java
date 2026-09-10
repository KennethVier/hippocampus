package com.hippocampus.materials.api;

import java.util.List;
import java.util.UUID;

public record MaterialStructureResponse(
        UUID id,
        String nodeType,
        String title,
        Integer startPage,
        Integer endPage,
        List<MaterialStructureResponse> children) {}