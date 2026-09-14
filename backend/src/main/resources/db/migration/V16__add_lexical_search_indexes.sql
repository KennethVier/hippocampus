CREATE INDEX idx_chunks_active_content_fts
    ON chunks USING GIN (to_tsvector('simple', content))
    WHERE is_active = true;

CREATE INDEX idx_chunks_active_content_trgm
    ON chunks USING GIN (content gin_trgm_ops)
    WHERE is_active = true;
