ALTER TABLE text_blocks
    ADD CONSTRAINT uq_text_blocks_id_material_version UNIQUE (id, material_version_id);

ALTER TABLE visual_assets
    ADD CONSTRAINT uq_visual_assets_id_material_version UNIQUE (id, material_version_id);

CREATE TABLE chunks (
    id UUID NOT NULL,
    material_version_id UUID NOT NULL,
    document_node_id UUID NULL,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    token_count INT NULL,
    page_start INT NULL,
    page_end INT NULL,
    timestamp_start_ms BIGINT NULL,
    timestamp_end_ms BIGINT NULL,
    heading_path JSONB NULL,
    content_type VARCHAR NOT NULL,
    extraction_method VARCHAR NOT NULL,
    quality VARCHAR NULL,
    source_order BIGINT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_chunks PRIMARY KEY (id),
    CONSTRAINT uq_chunks_id_material_version UNIQUE (id, material_version_id),
    CONSTRAINT uq_chunks_material_version_index UNIQUE (material_version_id, chunk_index),
    CONSTRAINT fk_chunks_material_version FOREIGN KEY (material_version_id) REFERENCES material_versions(id) ON DELETE CASCADE,
    CONSTRAINT fk_chunks_node_same_version FOREIGN KEY (document_node_id, material_version_id)
        REFERENCES document_nodes(id, material_version_id) ON DELETE NO ACTION,
    CONSTRAINT chk_chunks_index CHECK (chunk_index >= 1),
    CONSTRAINT chk_chunks_content CHECK (length(content) > 0),
    CONSTRAINT chk_chunks_token_count CHECK (token_count IS NULL OR token_count > 0),
    CONSTRAINT chk_chunks_page_start CHECK (page_start IS NULL OR page_start >= 1),
    CONSTRAINT chk_chunks_page_range CHECK (page_start IS NULL OR page_end IS NULL OR page_end >= page_start),
    CONSTRAINT chk_chunks_content_type CHECK (content_type IN ('TEXT','TABLE')),
    CONSTRAINT chk_chunks_extraction_method CHECK (extraction_method IN ('NATIVE','OCR')),
    CONSTRAINT chk_chunks_quality CHECK (quality IS NULL OR quality IN ('STRONG','LIMITED','POOR'))
);

CREATE TABLE chunk_text_block_links (
    chunk_id UUID NOT NULL,
    text_block_id UUID NOT NULL,
    material_version_id UUID NOT NULL,
    source_position INT NOT NULL,
    is_overlap BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_chunk_text_block_links PRIMARY KEY (chunk_id, source_position),
    CONSTRAINT chk_chunk_text_block_links_position CHECK (source_position >= 1),
    CONSTRAINT fk_chunk_text_block_links_chunk FOREIGN KEY (chunk_id, material_version_id)
        REFERENCES chunks(id, material_version_id) ON DELETE CASCADE,
    CONSTRAINT fk_chunk_text_block_links_text_block FOREIGN KEY (text_block_id, material_version_id)
        REFERENCES text_blocks(id, material_version_id) ON DELETE CASCADE
);
CREATE INDEX idx_chunk_text_block_links_text_block ON chunk_text_block_links(text_block_id, material_version_id);

CREATE TABLE chunk_visual_links (
    chunk_id UUID NOT NULL,
    visual_asset_id UUID NOT NULL,
    material_version_id UUID NOT NULL,
    relationship_type VARCHAR NOT NULL,
    CONSTRAINT pk_chunk_visual_links PRIMARY KEY (chunk_id, visual_asset_id),
    CONSTRAINT fk_chunk_visual_links_chunk FOREIGN KEY (chunk_id, material_version_id)
        REFERENCES chunks(id, material_version_id) ON DELETE CASCADE,
    CONSTRAINT fk_chunk_visual_links_visual FOREIGN KEY (visual_asset_id, material_version_id)
        REFERENCES visual_assets(id, material_version_id) ON DELETE CASCADE,
    CONSTRAINT chk_chunk_visual_links_relationship CHECK (relationship_type IN ('NEARBY','CAPTION_FOR','REFERENCES'))
);
