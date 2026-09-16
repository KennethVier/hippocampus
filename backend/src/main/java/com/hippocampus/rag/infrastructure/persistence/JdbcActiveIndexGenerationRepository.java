package com.hippocampus.rag.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import com.hippocampus.rag.port.ActiveIndexGenerationRepository;
import com.hippocampus.rag.port.EmbeddingModelMetadata;
import com.hippocampus.rag.port.IndexGeneration;

public final class JdbcActiveIndexGenerationRepository implements ActiveIndexGenerationRepository {
    private final JdbcClient jdbc;

    public JdbcActiveIndexGenerationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<IndexGeneration> findActiveGeneration() {
        List<IndexGeneration> active = jdbc.sql("""
                SELECT id, embedding_provider, embedding_model, embedding_model_version,
                       embedding_dimension, chunking_version, status
                FROM index_generations
                WHERE status = 'ACTIVE'
                ORDER BY created_at, id
                """)
                .query((row, number) -> new IndexGeneration(
                        row.getObject("id", UUID.class),
                        new EmbeddingModelMetadata(
                                row.getString("embedding_provider"),
                                row.getString("embedding_model"),
                                row.getString("embedding_model_version"),
                                row.getInt("embedding_dimension")),
                        row.getString("chunking_version"),
                        IndexGeneration.Status.ACTIVE))
                .list();
        if (active.size() > 1) {
            throw new IllegalStateException("Multiple ACTIVE index generations are ambiguous");
        }
        return active.stream().findFirst();
    }
}
