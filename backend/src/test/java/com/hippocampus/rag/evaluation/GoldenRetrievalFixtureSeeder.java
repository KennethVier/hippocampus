package com.hippocampus.rag.evaluation;

import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;

public class GoldenRetrievalFixtureSeeder {
    private final JdbcClient jdbc;
    private GoldenRetrievalCorpus corpusReference;

    public GoldenRetrievalFixtureSeeder(JdbcClient jdbc) {
        this.jdbc = java.util.Objects.requireNonNull(jdbc);
    }

    public void seed(GoldenRetrievalCorpus corpus) {
        this.corpusReference = corpus;
        jdbc.sql("DELETE FROM chunk_text_block_links").update();
        jdbc.sql("DELETE FROM chunk_visual_links").update();
        jdbc.sql("DELETE FROM chunk_embeddings").update();
        jdbc.sql("DELETE FROM chunks").update();
        jdbc.sql("DELETE FROM document_nodes").update();
        jdbc.sql("DELETE FROM material_topic_links").update();
        jdbc.sql("DELETE FROM material_versions").update();
        jdbc.sql("DELETE FROM materials").update();
        jdbc.sql("DELETE FROM topics").update();

        corpus.users().forEach((k, v) -> jdbc.sql("INSERT INTO users (id, name, status) VALUES (:id, :name, 'ACTIVE')")
                .param("id", deriveUuid("user", k)).param("name", v.name()).update());

        corpus.topics().forEach((k, v) -> jdbc.sql("INSERT INTO topics (id, name, subject, status) VALUES (:id, :name, :subject, 'ACTIVE')")
                .param("id", deriveUuid("topic", k)).param("name", v.name()).param("subject", v.subject()).update());

        corpus.materials().forEach((k, v) -> jdbc.sql("INSERT INTO materials (id, title, subject, status, material_type, mime_type) VALUES (:id, :title, :subject, 'ACTIVE', 'PDF', 'application/pdf')")
                .param("id", deriveUuid("material", k)).param("title", v.title()).param("subject", v.subject()).update());

        corpus.materialVersions().forEach((k, v) -> jdbc.sql("INSERT INTO material_versions (id, material_id, version, storage_key, file_size_bytes, page_count, status) VALUES (:id, :mat, :ver, 'synth-key', 1024, 100, 'ACTIVE')")
                .param("id", deriveUuid("version", k)).param("mat", deriveUuid("material", v.material())).param("ver", v.version()).update());

        corpus.materialTopicLinks().forEach(l -> jdbc.sql("INSERT INTO material_topic_links (material_version_id, topic_id, status) VALUES (:mv, :topic, 'ACTIVE')")
                .param("mv", deriveUuid("version", l.materialVersion())).param("topic", deriveUuid("topic", l.topic())).update());

        corpus.documentNodes().forEach((k, v) -> jdbc.sql("INSERT INTO document_nodes (id, material_version_id, title, ordinal, start_page, end_page, detection_origin, detection_confidence, created_at) VALUES (:id, :mv, :title, 1, 1, 100, 'MANUAL', '1.0', now())")
                .param("id", deriveUuid("node", k)).param("mv", deriveUuid("version", v.version())).param("title", v.title()).update());

        corpus.chunks().forEach((k, v) -> {
            UUID nodeUuid = deriveUuid("node", v.node());
            UUID versionUuidDerived = deriveUuid("version", corpus.documentNodes().get(v.node()).version());

            jdbc.sql("INSERT INTO chunks (id, material_version_id, document_node_id, chunk_index, content, token_count, page_start, page_end, heading_path, content_type, extraction_method, quality, source_order, is_active, created_at) VALUES (:id, :mv, :node, :idx, :content, 100, :ps, :pe, CAST(:hp AS jsonb), :ct, :em, :q, 1, true, now())")
                    .param("id", deriveUuid("chunk", k)).param("mv", versionUuidDerived).param("node", nodeUuid).param("idx", v.index()).param("content", v.content())
                    .param("ps", v.pageStart()).param("pe", v.pageEnd()).param("hp", "[]")
                    .param("ct", v.contentType()).param("em", v.extractionMethod()).param("q", v.quality()).update());
        });

        corpus.indexGenerations().forEach((k, v) -> jdbc.sql("INSERT INTO index_generations (id, model_provider, model_name, model_version, dimension, status, created_at) VALUES (:id, :prov, :name, :ver, :dim, 'ACTIVE', now())")
                .param("id", deriveUuid("gen", k)).param("prov", "synthetic").param("name", v.model()).param("ver", "1.0").param("dim", v.dimension()).update());

        corpus.chunkEmbeddings().forEach((k, v) -> jdbc.sql("INSERT INTO chunk_embeddings (chunk_id, index_generation_id, vector) VALUES (:chunk, :gen, :vec)")
                .param("chunk", deriveUuid("chunk", k)).param("gen", deriveUuid("gen", "gen-v1")).param("vec", v).update());
    }

    public UUID deriveUuid(String type, String key) {
        return UUID.nameUUIDFromBytes(("golden-retrieval-v1:" + type + ":" + key).getBytes());
    }

    public String deriveLogicalKey(String type, UUID uuid) {
        // In a real scenario, we'd look this up in the DB or a map.
        // For the golden dataset, we can iterate the corpus.
        if ("chunk".equals(type)) {
            return corpusReference.chunks().entrySet().stream()
                    .filter(e -> deriveUuid("chunk", e.getKey()).equals(uuid))
                    .map(Map.Entry::getKey)
                    .findFirst().orElse("unknown");
        }
        return "unknown";
    }
}
