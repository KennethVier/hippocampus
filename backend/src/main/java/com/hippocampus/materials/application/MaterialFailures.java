package com.hippocampus.materials.application;

import com.hippocampus.shared.application.error.ApplicationNotFoundException;
import com.hippocampus.shared.domain.error.ErrorCode;

final class MaterialFailures {
    private static final ErrorCode NOT_FOUND = new ErrorCode("MATERIAL_NOT_FOUND");

    private MaterialFailures() {}

    static ApplicationNotFoundException notFound() {
        return new ApplicationNotFoundException(NOT_FOUND, "Material was not found.");
    }
}
