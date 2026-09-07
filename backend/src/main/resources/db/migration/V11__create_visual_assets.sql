CREATE TABLE visual_assets (
    id UUID NOT NULL,
    material_version_id UUID NOT NULL,
    document_node_id UUID NULL,
    page_number INT NOT NULL,
    storage_key VARCHAR NOT NULL,
    visual_type VARCHAR NOT NULL,
    caption TEXT NULL,
    nearby_text TEXT NULL,
    interpretation_status VARCHAR NOT NULL,
    width_px INT NULL,
    height_px INT NULL,
    content_hash VARCHAR NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_visual_assets PRIMARY KEY (id),
    CONSTRAINT fk_visual_assets_material_version FOREIGN KEY (material_version_id)
        REFERENCES material_versions (id) ON DELETE CASCADE,
    CONSTRAINT fk_visual_assets_node_same_version
        FOREIGN KEY (document_node_id, material_version_id)
        REFERENCES document_nodes (id, material_version_id)
        ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT uq_visual_assets_page_content
        UNIQUE (material_version_id, page_number, content_hash),
    CONSTRAINT uq_visual_assets_storage_key UNIQUE (storage_key),
    CONSTRAINT chk_visual_assets_page_number CHECK (page_number >= 1),
    CONSTRAINT chk_visual_assets_width CHECK (width_px IS NULL OR width_px >= 1),
    CONSTRAINT chk_visual_assets_height CHECK (height_px IS NULL OR height_px >= 1),
    CONSTRAINT chk_visual_assets_visual_type CHECK (visual_type IN (
        'ANATOMY_DIAGRAM', 'HISTOLOGY', 'PATHOLOGY', 'RADIOLOGY',
        'FLOW_DIAGRAM', 'TABLE', 'CHART', 'SLIDE_FIGURE', 'OTHER'
    )),
    CONSTRAINT chk_visual_assets_interpretation_status CHECK (interpretation_status IN (
        'UNASSESSED', 'SUPPORTED', 'LIMITED', 'UNSUPPORTED', 'FAILED'
    ))
);

CREATE INDEX idx_visual_assets_material_version_page
    ON visual_assets (material_version_id, page_number);
