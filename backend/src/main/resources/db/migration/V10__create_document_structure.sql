CREATE TABLE document_nodes (
    id UUID NOT NULL,
    material_version_id UUID NOT NULL,
    parent_id UUID NULL,
    node_type VARCHAR NOT NULL,
    title VARCHAR NULL,
    ordinal INT NULL,
    start_page INT NULL,
    end_page INT NULL,
    start_offset BIGINT NULL,
    end_offset BIGINT NULL,
    detection_origin VARCHAR NOT NULL,
    detection_confidence VARCHAR NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_document_nodes PRIMARY KEY (id),
    CONSTRAINT uq_document_nodes_id_material_version UNIQUE (id, material_version_id),
    CONSTRAINT fk_document_nodes_material_version FOREIGN KEY (material_version_id)
        REFERENCES material_versions (id) ON DELETE CASCADE,
    CONSTRAINT fk_document_nodes_parent_same_version
        FOREIGN KEY (parent_id, material_version_id)
        REFERENCES document_nodes (id, material_version_id)
        ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT chk_document_nodes_not_self_parent CHECK (parent_id IS NULL OR parent_id <> id),
    CONSTRAINT chk_document_nodes_document_is_root CHECK (node_type <> 'DOCUMENT' OR parent_id IS NULL),
    CONSTRAINT chk_document_nodes_node_type CHECK (node_type IN (
        'DOCUMENT', 'CHAPTER', 'SECTION', 'SUBSECTION', 'HEADING',
        'PAGE_GROUP', 'TRANSCRIPT_SEGMENT_GROUP'
    )),
    CONSTRAINT chk_document_nodes_detection_origin CHECK (detection_origin IN (
        'NATIVE', 'HEURISTIC', 'AI_ASSISTED', 'USER_CONFIRMED'
    )),
    CONSTRAINT chk_document_nodes_ordinal CHECK (ordinal IS NULL OR ordinal >= 1),
    CONSTRAINT chk_document_nodes_start_page CHECK (start_page IS NULL OR start_page >= 1),
    CONSTRAINT chk_document_nodes_end_page CHECK (end_page IS NULL OR end_page >= 1),
    CONSTRAINT chk_document_nodes_page_range CHECK (
        start_page IS NULL OR end_page IS NULL OR start_page <= end_page
    ),
    CONSTRAINT chk_document_nodes_start_offset CHECK (start_offset IS NULL OR start_offset >= 0),
    CONSTRAINT chk_document_nodes_end_offset CHECK (end_offset IS NULL OR end_offset >= 0),
    CONSTRAINT chk_document_nodes_offset_range CHECK (
        start_offset IS NULL OR end_offset IS NULL OR start_offset <= end_offset
    )
);

CREATE FUNCTION enforce_document_nodes_acyclic()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        WITH RECURSIVE ancestry AS (
            SELECT
                node.id,
                node.parent_id,
                ARRAY[node.id] AS path,
                FALSE AS cycle
            FROM document_nodes node
            WHERE node.id = NEW.id
              AND node.material_version_id = NEW.material_version_id

            UNION ALL

            SELECT
                parent.id,
                parent.parent_id,
                ancestry.path || parent.id,
                parent.id = ANY(ancestry.path) AS cycle
            FROM document_nodes parent
            JOIN ancestry
              ON parent.id = ancestry.parent_id
             AND parent.material_version_id = NEW.material_version_id
            WHERE NOT ancestry.cycle
        )
        SELECT 1
        FROM ancestry
        WHERE cycle
    ) THEN
        RAISE EXCEPTION 'document_nodes hierarchy must be acyclic'
            USING ERRCODE = '23514',
                  CONSTRAINT = 'chk_document_nodes_acyclic',
                  TABLE = 'document_nodes';
    END IF;

    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER chk_document_nodes_acyclic
    AFTER INSERT OR UPDATE OF parent_id, material_version_id
    ON document_nodes
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION enforce_document_nodes_acyclic();

CREATE UNIQUE INDEX uq_document_nodes_document_root
    ON document_nodes (material_version_id)
    WHERE node_type = 'DOCUMENT' AND parent_id IS NULL;

CREATE UNIQUE INDEX uq_document_nodes_sibling_ordinal
    ON document_nodes (material_version_id, parent_id, ordinal) NULLS NOT DISTINCT
    WHERE ordinal IS NOT NULL;

CREATE TABLE text_blocks (
    id UUID NOT NULL,
    material_version_id UUID NOT NULL,
    document_node_id UUID NULL,
    page_number INT NULL,
    block_type VARCHAR NOT NULL,
    ordinal INT NOT NULL,
    content TEXT NOT NULL,
    extraction_method VARCHAR NOT NULL,
    quality VARCHAR NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_text_blocks PRIMARY KEY (id),
    CONSTRAINT fk_text_blocks_material_version FOREIGN KEY (material_version_id)
        REFERENCES material_versions (id) ON DELETE CASCADE,
    CONSTRAINT fk_text_blocks_node_same_version
        FOREIGN KEY (document_node_id, material_version_id)
        REFERENCES document_nodes (id, material_version_id)
        ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT chk_text_blocks_page_number CHECK (page_number IS NULL OR page_number >= 1),
    CONSTRAINT chk_text_blocks_ordinal CHECK (ordinal >= 1),
    CONSTRAINT chk_text_blocks_block_type CHECK (block_type IN (
        'PAGE_TEXT', 'HEADING', 'PARAGRAPH', 'LIST', 'CAPTION', 'TABLE_TEXT', 'TRANSCRIPT'
    )),
    CONSTRAINT chk_text_blocks_extraction_method CHECK (extraction_method IN ('NATIVE', 'OCR')),
    CONSTRAINT chk_text_blocks_quality CHECK (quality IS NULL OR quality IN ('STRONG', 'LIMITED', 'POOR')),
    CONSTRAINT uq_text_blocks_material_version_ordinal UNIQUE (material_version_id, ordinal)
);

CREATE INDEX idx_text_blocks_material_version_page
    ON text_blocks (material_version_id, page_number);
