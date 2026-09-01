CREATE TABLE material_topic_links (
    id UUID NOT NULL,
    topic_id UUID NOT NULL,
    material_id UUID NOT NULL,
    material_version_id UUID NULL,
    document_node_id UUID NULL,
    link_origin VARCHAR NOT NULL,
    status VARCHAR NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_material_topic_links PRIMARY KEY (id),
    CONSTRAINT fk_material_topic_links_topic FOREIGN KEY (topic_id)
        REFERENCES topics (id) ON DELETE RESTRICT,
    CONSTRAINT fk_material_topic_links_material FOREIGN KEY (material_id)
        REFERENCES materials (id) ON DELETE RESTRICT,
    CONSTRAINT fk_material_topic_links_material_version FOREIGN KEY (material_id, material_version_id)
        REFERENCES material_versions (material_id, id) ON DELETE RESTRICT,
    CONSTRAINT chk_material_topic_links_origin CHECK (link_origin IN (
        'USER_SELECTED', 'STRUCTURE_DETECTED', 'SYSTEM_SUGGESTED', 'AI_ASSISTED'
    )),
    CONSTRAINT chk_material_topic_links_status CHECK (status IN (
        'ACTIVE', 'DISMISSED', 'ARCHIVED'
    )),
    CONSTRAINT chk_material_topic_links_document_node_requires_version CHECK (
        document_node_id IS NULL OR material_version_id IS NOT NULL
    ),
    CONSTRAINT chk_material_topic_links_document_node_phase2_disabled CHECK (
        document_node_id IS NULL
    )
);

CREATE INDEX idx_material_topic_links_topic_status
    ON material_topic_links (topic_id, status);

CREATE UNIQUE INDEX uq_material_topic_links_active_exact_target
    ON material_topic_links (topic_id, material_id, material_version_id, document_node_id)
    NULLS NOT DISTINCT
    WHERE status = 'ACTIVE';
