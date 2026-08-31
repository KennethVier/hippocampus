package com.hippocampus.materials.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import jakarta.persistence.Column;

class MaterialPersistenceMappingTests {

    @Test
    void keepsRevisionIdentityStructurallyImmutable() throws Exception {
        assertThat(column("id").updatable()).isFalse();
        assertThat(column("materialId").updatable()).isFalse();
        assertThat(column("versionNumber").updatable()).isFalse();
        assertThat(column("createdAt").updatable()).isFalse();
        assertThat(MaterialVersionEntity.class.getDeclaredMethods())
                .extracting(java.lang.reflect.Method::getName)
                .doesNotContain("setMaterialId", "setVersionNumber", "setCreatedAt");
    }

    @Test
    void permitsLaterProcessingToPopulateSourceAndLifecycleMetadata() throws Exception {
        assertThat(column("storageKey").updatable()).isTrue();
        assertThat(column("fileSizeBytes").updatable()).isTrue();
        assertThat(column("pageCount").updatable()).isTrue();
        assertThat(column("contentHash").updatable()).isTrue();
        assertThat(column("processingStatus").updatable()).isTrue();
        assertThat(column("processingProgress").updatable()).isTrue();
        assertThat(column("extractionMethod").updatable()).isTrue();
        assertThat(column("extractionQuality").updatable()).isTrue();
        assertThat(column("activatedAt").updatable()).isTrue();
    }

    private static Column column(String fieldName) throws NoSuchFieldException {
        Field field = MaterialVersionEntity.class.getDeclaredField(fieldName);
        return field.getAnnotation(Column.class);
    }
}
