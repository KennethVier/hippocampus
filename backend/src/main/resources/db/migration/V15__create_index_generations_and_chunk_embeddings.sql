CREATE TABLE index_generations (
    id UUID NOT NULL,
    embedding_provider VARCHAR NOT NULL,
    embedding_model VARCHAR NOT NULL,
    embedding_model_version VARCHAR NULL,
    embedding_dimension INT NOT NULL,
    chunking_version VARCHAR NOT NULL,
    status VARCHAR NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    activated_at TIMESTAMPTZ NULL,
    CONSTRAINT pk_index_generations PRIMARY KEY (id),
    CONSTRAINT chk_index_generations_embedding_dimension CHECK (embedding_dimension > 0),
    CONSTRAINT chk_index_generations_status CHECK (
        status IN ('BUILDING', 'ACTIVE', 'INACTIVE', 'FAILED')
    )
);

CREATE TABLE chunk_embeddings (
    id UUID NOT NULL,
    chunk_id UUID NOT NULL,
    index_generation_id UUID NOT NULL,
    embedding VECTOR NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_chunk_embeddings PRIMARY KEY (id),
    CONSTRAINT fk_chunk_embeddings_chunk FOREIGN KEY (chunk_id)
        REFERENCES chunks (id) ON DELETE CASCADE,
    CONSTRAINT fk_chunk_embeddings_index_generation FOREIGN KEY (index_generation_id)
        REFERENCES index_generations (id),
    CONSTRAINT uq_chunk_embeddings_chunk_generation UNIQUE (chunk_id, index_generation_id)
);

CREATE FUNCTION enforce_chunk_embedding_dimension()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    expected_dimension INT;
BEGIN
    SELECT embedding_dimension
      INTO expected_dimension
      FROM public.index_generations
     WHERE id = NEW.index_generation_id
       FOR KEY SHARE;

    IF expected_dimension IS NOT NULL
       AND public.vector_dims(NEW.embedding) <> expected_dimension THEN
        RAISE EXCEPTION 'embedding dimension % does not match index generation dimension %',
                public.vector_dims(NEW.embedding), expected_dimension
            USING ERRCODE = '23514', CONSTRAINT = 'chk_chunk_embeddings_dimension';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_chunk_embeddings_dimension
BEFORE INSERT OR UPDATE OF embedding, index_generation_id ON chunk_embeddings
FOR EACH ROW
EXECUTE FUNCTION enforce_chunk_embedding_dimension();

CREATE FUNCTION prevent_invalid_index_generation_dimension_change()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.embedding_dimension IS DISTINCT FROM OLD.embedding_dimension
       AND EXISTS (
           SELECT 1
             FROM public.chunk_embeddings
            WHERE index_generation_id = OLD.id
              AND public.vector_dims(embedding) <> NEW.embedding_dimension
       ) THEN
        RAISE EXCEPTION 'index generation dimension % does not match existing embeddings',
                NEW.embedding_dimension
            USING ERRCODE = '23514', CONSTRAINT = 'chk_index_generations_dimension_change';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_index_generations_dimension_change
BEFORE UPDATE OF embedding_dimension ON index_generations
FOR EACH ROW
EXECUTE FUNCTION prevent_invalid_index_generation_dimension_change();
