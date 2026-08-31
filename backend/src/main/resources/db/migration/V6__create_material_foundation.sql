CREATE TABLE materials (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    title VARCHAR NOT NULL,
    material_type VARCHAR NOT NULL,
    original_filename VARCHAR NULL,
    mime_type VARCHAR NULL,
    storage_key VARCHAR NULL,
    status VARCHAR NOT NULL,
    active_version_id UUID NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_materials PRIMARY KEY (id),
    CONSTRAINT fk_materials_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE material_versions (
    id UUID NOT NULL,
    material_id UUID NOT NULL,
    version_number INT NOT NULL,
    storage_key VARCHAR NULL,
    file_size_bytes BIGINT NULL,
    page_count INT NULL,
    content_hash VARCHAR NULL,
    processing_status VARCHAR NOT NULL,
    processing_progress NUMERIC(5,2) NULL,
    extraction_method VARCHAR NULL,
    extraction_quality VARCHAR NULL,
    activated_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_material_versions PRIMARY KEY (id),
    CONSTRAINT fk_material_versions_material FOREIGN KEY (material_id)
        REFERENCES materials (id) ON DELETE RESTRICT,
    CONSTRAINT uq_material_versions_material_version_number
        UNIQUE (material_id, version_number),
    CONSTRAINT uq_material_versions_material_id_id
        UNIQUE (material_id, id),
    CONSTRAINT chk_material_versions_version_number
        CHECK (version_number >= 1),
    CONSTRAINT chk_material_versions_file_size
        CHECK (file_size_bytes >= 0),
    CONSTRAINT chk_material_versions_page_count
        CHECK (page_count >= 0)
);

ALTER TABLE materials
    ADD CONSTRAINT fk_materials_active_version
    FOREIGN KEY (id, active_version_id)
    REFERENCES material_versions (material_id, id)
    ON DELETE RESTRICT;

CREATE INDEX idx_materials_user_status
    ON materials (user_id, status);

CREATE INDEX idx_material_versions_material_processing_status
    ON material_versions (material_id, processing_status);
