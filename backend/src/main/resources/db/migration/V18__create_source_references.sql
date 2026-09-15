CREATE TABLE source_references (
    id UUID NOT NULL,
    material_id UUID NOT NULL,
    material_version_id UUID NOT NULL,
    document_node_id UUID NULL,
    chunk_id UUID NULL,
    visual_asset_id UUID NULL,
    page_number INT NULL,
    timestamp_start_ms BIGINT NULL,
    timestamp_end_ms BIGINT NULL,
    display_label VARCHAR NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_source_references PRIMARY KEY (id),
    CONSTRAINT fk_source_references_material_version
        FOREIGN KEY (material_id, material_version_id)
        REFERENCES material_versions (material_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_source_references_node_same_version
        FOREIGN KEY (document_node_id, material_version_id)
        REFERENCES document_nodes (id, material_version_id) ON DELETE RESTRICT,
    CONSTRAINT fk_source_references_chunk_same_version
        FOREIGN KEY (chunk_id, material_version_id)
        REFERENCES chunks (id, material_version_id) ON DELETE RESTRICT,
    CONSTRAINT fk_source_references_visual_same_version
        FOREIGN KEY (visual_asset_id, material_version_id)
        REFERENCES visual_assets (id, material_version_id) ON DELETE RESTRICT,
    CONSTRAINT chk_source_references_meaningful_target CHECK (
        document_node_id IS NOT NULL OR chunk_id IS NOT NULL OR visual_asset_id IS NOT NULL
        OR page_number IS NOT NULL OR timestamp_start_ms IS NOT NULL
    ),
    CONSTRAINT chk_source_references_page_number CHECK (page_number IS NULL OR page_number >= 1),
    CONSTRAINT chk_source_references_timestamp_start CHECK (
        timestamp_start_ms IS NULL OR timestamp_start_ms >= 0
    ),
    CONSTRAINT chk_source_references_timestamp_end CHECK (
        timestamp_end_ms IS NULL OR timestamp_end_ms >= 0
    ),
    CONSTRAINT chk_source_references_timestamp_end_requires_start CHECK (
        timestamp_end_ms IS NULL OR timestamp_start_ms IS NOT NULL
    ),
    CONSTRAINT chk_source_references_timestamp_range CHECK (
        timestamp_start_ms IS NULL OR timestamp_end_ms IS NULL OR timestamp_end_ms >= timestamp_start_ms
    ),
    CONSTRAINT chk_source_references_chunk_visual_exclusive CHECK (
        chunk_id IS NULL OR visual_asset_id IS NULL
    ),
    CONSTRAINT uq_source_references_canonical_identity UNIQUE NULLS NOT DISTINCT (
        material_id, material_version_id, document_node_id, chunk_id, visual_asset_id,
        page_number, timestamp_start_ms, timestamp_end_ms
    )
);

CREATE INDEX idx_source_references_document_node
    ON source_references (document_node_id) WHERE document_node_id IS NOT NULL;
CREATE INDEX idx_source_references_chunk
    ON source_references (chunk_id) WHERE chunk_id IS NOT NULL;
CREATE INDEX idx_source_references_visual_asset
    ON source_references (visual_asset_id) WHERE visual_asset_id IS NOT NULL;
