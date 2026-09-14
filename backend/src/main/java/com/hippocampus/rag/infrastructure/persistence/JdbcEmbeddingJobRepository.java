package com.hippocampus.rag.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.hippocampus.rag.port.ChunkEmbedding;
import com.hippocampus.rag.port.EmbeddableChunk;
import com.hippocampus.rag.port.EmbeddingJobRepository;
import com.hippocampus.rag.port.EmbeddingFailureException;
import com.hippocampus.rag.port.EmbeddingModelMetadata;
import com.hippocampus.rag.port.IndexGeneration;

public final class JdbcEmbeddingJobRepository implements EmbeddingJobRepository {
    private static final String GENERATIONS = """
            SELECT id, embedding_provider, embedding_model, embedding_model_version,
                   embedding_dimension, chunking_version, status
            FROM index_generations
            WHERE status IN ('ACTIVE', 'BUILDING')
            ORDER BY created_at, id
            """;

    private final JdbcClient jdbc;

    public JdbcEmbeddingJobRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<IndexGeneration> findPreferredGeneration() {
        List<IndexGeneration> generations = jdbc.sql(GENERATIONS)
                .query((row, number) -> new IndexGeneration(
                        row.getObject("id", UUID.class),
                        new EmbeddingModelMetadata(
                                row.getString("embedding_provider"),
                                row.getString("embedding_model"),
                                row.getString("embedding_model_version"),
                                row.getInt("embedding_dimension")),
                        row.getString("chunking_version"),
                        IndexGeneration.Status.valueOf(row.getString("status"))))
                .list();
        List<IndexGeneration> active = generations.stream()
                .filter(generation -> generation.status() == IndexGeneration.Status.ACTIVE)
                .toList();
        if (active.size() > 1) {
            throw new IllegalStateException("Multiple ACTIVE index generations are ambiguous");
        }
        if (active.size() == 1) {
            return Optional.of(active.getFirst());
        }
        if (generations.size() > 1) {
            throw new IllegalStateException("Multiple BUILDING index generations are ambiguous");
        }
        return generations.stream().findFirst();
    }

    @Override
    public long countEligibleChunks(UUID materialVersionId) {
        return jdbc.sql("SELECT count(*) FROM chunks WHERE material_version_id=:version AND is_active")
                .param("version", materialVersionId).query(Long.class).single();
    }

    @Override
    public long countDurableEmbeddings(UUID materialVersionId, UUID indexGenerationId) {
        return jdbc.sql("""
                SELECT count(*) FROM chunks c
                JOIN chunk_embeddings ce ON ce.chunk_id=c.id
                WHERE c.material_version_id=:version AND c.is_active
                  AND ce.index_generation_id=:generation
                """).param("version", materialVersionId).param("generation", indexGenerationId)
                .query(Long.class).single();
    }

    @Override
    public List<EmbeddableChunk> findMissingChunks(
            UUID materialVersionId, UUID indexGenerationId, int batchSize) {
        String generationFilter = indexGenerationId == null ? "" : """
                  AND NOT EXISTS (
                    SELECT 1 FROM chunk_embeddings ce
                    WHERE ce.chunk_id=c.id AND ce.index_generation_id=:generation)
                """;
        JdbcClient.StatementSpec query = jdbc.sql("""
                SELECT c.id, c.chunk_index, c.content
                FROM chunks c
                WHERE c.material_version_id=:version AND c.is_active
                """ + generationFilter + " ORDER BY c.chunk_index LIMIT :limit")
                .param("version", materialVersionId).param("limit", batchSize);
        if (indexGenerationId != null) {
            query = query.param("generation", indexGenerationId);
        }
        return query.query((row, number) -> new EmbeddableChunk(
                row.getObject("id", UUID.class), row.getInt("chunk_index"), row.getString("content")))
                .list();
    }

    @Override
    public IndexGeneration getOrCreateInitialGeneration(
            EmbeddingModelMetadata model, String chunkingVersion) {
        requireTransaction();
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtext('hippocampus-initial-index-generation'))")
                .query((row, number) -> Boolean.TRUE).single();
        Optional<IndexGeneration> existing = findPreferredGeneration();
        if (existing.isPresent()) {
            IndexGeneration generation = existing.orElseThrow();
            if (!generation.isCompatibleWith(model, chunkingVersion)) {
                throw new EmbeddingFailureException(EmbeddingFailureException.Reason.INVALID_RESPONSE);
            }
            return generation;
        }

        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO index_generations(
                    id, embedding_provider, embedding_model, embedding_model_version,
                    embedding_dimension, chunking_version, status, created_at)
                VALUES(:id, :provider, :model, :modelVersion, :dimension, :chunking, 'BUILDING', CURRENT_TIMESTAMP)
                """).param("id", id).param("provider", model.provider()).param("model", model.model())
                .param("modelVersion", model.version(), java.sql.Types.VARCHAR)
                .param("dimension", model.dimension()).param("chunking", chunkingVersion).update();
        return new IndexGeneration(id, model, chunkingVersion, IndexGeneration.Status.BUILDING);
    }

    @Override
    public void persistBatch(
            UUID materialVersionId, UUID indexGenerationId, List<ChunkEmbedding> embeddings) {
        requireTransaction();
        if (embeddings.isEmpty()) {
            throw new IllegalArgumentException("Embedding persistence batch must not be empty");
        }
        List<UUID> chunkIds = new ArrayList<>(embeddings.size());
        for (ChunkEmbedding embedding : embeddings) {
            UUID eligible = jdbc.sql("""
                    SELECT id FROM chunks
                    WHERE id=:chunk AND material_version_id=:version AND is_active
                    FOR SHARE
                    """).param("chunk", embedding.chunkId()).param("version", materialVersionId)
                    .query(UUID.class).optional()
                    .orElseThrow(() -> new IllegalStateException("Embedding chunk is not active in the requested material version"));
            chunkIds.add(eligible);
            jdbc.sql("""
                    INSERT INTO chunk_embeddings(id, chunk_id, index_generation_id, embedding, created_at)
                    VALUES(:id, :chunk, :generation, CAST(:embedding AS vector), CURRENT_TIMESTAMP)
                    ON CONFLICT (chunk_id, index_generation_id) DO NOTHING
                    """).param("id", UUID.randomUUID()).param("chunk", eligible)
                    .param("generation", indexGenerationId)
                    .param("embedding", vectorLiteral(embedding)).update();
        }
        long durable = jdbc.sql("""
                SELECT count(*) FROM chunk_embeddings
                WHERE index_generation_id=:generation AND chunk_id IN (:chunks)
                """).param("generation", indexGenerationId).param("chunks", chunkIds)
                .query(Long.class).single();
        if (durable != chunkIds.size()) {
            throw new IllegalStateException("Embedding batch was not durably persisted");
        }
    }

    private static String vectorLiteral(ChunkEmbedding embedding) {
        return embedding.vector().values().toString().replace(" ", "");
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Embedding persistence requires a transaction");
        }
    }
}
