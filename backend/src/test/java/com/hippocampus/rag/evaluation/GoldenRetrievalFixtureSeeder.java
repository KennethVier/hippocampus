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
        jdbc.sql("DELETE FROM chunk_embeddings").update();
        jdbc.sql("DELETE FROM chunks").update();
        jdbc.sql("DELETE FROM document_nodes").update();
        jdbc.sql("DELETE FROM material_topic_links").update();
        jdbc.sql("DELETE FROM material_versions").update();
        jdbc.sql("DELETE FROM materials").update();
        jdbc.sql("DELETE FROM topics").update();
        jdbc.sql("DELETE FROM subjects").update();
        jdbc.sql("DELETE FROM users").update();

        corpus.users().forEach((k, v) -> jdbc.sql("INSERT INTO users (id, email, display_name, status, created_at, updated_at) VALUES (:id, :email, :name, 'ACTIVE', now(), now())")
                .param("id", deriveUuid("user", k)).param("email", k + "@example.com").param("name", v.name()).update());

        corpus.subjects().forEach((k, v) -> {
            UUID userId = deriveUuid("user", corpus.users().keySet().iterator().next());
            jdbc.sql("INSERT INTO subjects (id, user_id, name, status, created_at, updated_at) VALUES (:id, :uid, :name, 'ACTIVE', now(), now())")
                    .param("id", deriveUuid("subject", k)).param("uid", userId).param("name", v.name()).update();
        });

        corpus.topics().forEach((k, v) -> {
            UUID subjectId = deriveUuid("subject", v.subject());
            jdbc.sql("INSERT INTO topics (id, subject_id, name, status, created_at, updated_at) VALUES (:id, :sid, :name, 'ACTIVE', now(), now())")
                    .param("id", deriveUuid("topic", k)).param("sid", subjectId).param("name", v.name()).update();
        });

        corpus.materials().forEach((k, v) -> {
            UUID userId = deriveUuid("user", corpus.users().keySet().iterator().next());
            jdbc.sql("INSERT INTO materials (id, user_id, title, material_type, status, created_at, updated_at) VALUES (:id, :uid, :title, 'PDF', 'ACTIVE', now(), now())")
                    .param("id", deriveUuid("material", k)).param("uid", userId).param("title", v.title()).update();
        });

        corpus.materialVersions().forEach((k, v) -> jdbc.sql("INSERT INTO material_versions (id, material_id, version_number, storage_key, file_size_bytes, page_count, processing_status, created_at) VALUES (:id, :mat, :ver, 'synth-key', 1024, 100, 'ACTIVE', now())")
                .param("id", deriveUuid("version", k)).param("mat", deriveUuid("material", v.material())).param("ver", (int) Double.parseDouble(v.version())).update());

        corpus.materialVersions().forEach((k, v) -> {
            jdbc.sql("UPDATE materials SET active_version_id = :vid WHERE id = :mid")
                    .param("vid", deriveUuid("version", k))
                    .param("mid", deriveUuid("material", v.material()))
                    .update();
        });

        corpus.materialTopicLinks().forEach(l -> {
            UUID mvId = deriveUuid("version", l.materialVersion());
            UUID matId = deriveUuid("material", corpus.materialVersions().get(l.materialVersion()).material());
            jdbc.sql("INSERT INTO material_topic_links (id, topic_id, material_id, material_version_id, link_origin, status, created_at, updated_at) VALUES (:id, :topic, :mat, :mv, 'USER_SELECTED', 'ACTIVE', now(), now())")
                    .param("id", UUID.randomUUID()).param("topic", deriveUuid("topic", l.topic())).param("mat", matId).param("mv", mvId).update();
        });

        Map<UUID, Integer> nodeOrdinals = new HashMap<>();
        corpus.documentNodes().forEach((k, v) -> {
            UUID versionUuid = deriveUuid("version", v.version());
            int ordinal = nodeOrdinals.getOrDefault(versionUuid, 0) + 1;
            nodeOrdinals.put(versionUuid, ordinal);
            jdbc.sql("INSERT INTO document_nodes (id, material_version_id, node_type, title, ordinal, start_page, end_page, detection_origin, detection_confidence, created_at) VALUES (:id, :mv, 'SECTION', :title, :ord, 1, 100, 'USER_CONFIRMED', '1.0', now())")
                    .param("id", deriveUuid("node", k)).param("mv", versionUuid).param("title", v.title()).param("ord", ordinal).update();
        });

        Map<UUID, Integer> chunkOrdinals = new HashMap<>();
        corpus.chunks().forEach((k, v) -> {
            UUID nodeUuid = deriveUuid("node", v.node());
            UUID versionUuidDerived = deriveUuid("version", corpus.documentNodes().get(v.node()).version());

            String em = v.extractionMethod();

            int ordinal = chunkOrdinals.getOrDefault(versionUuidDerived, 0) + 1;
            chunkOrdinals.put(versionUuidDerived, ordinal);

            jdbc.sql("INSERT INTO chunks (id, material_version_id, document_node_id, chunk_index, content, token_count, page_start, page_end, heading_path, content_type, extraction_method, quality, source_order, is_active, created_at) VALUES (:id, :mv, :node, :idx, :content, 100, :ps, :pe, CAST(:hp AS jsonb), :ct, :em, :q, 1, true, now())")
                    .param("id", deriveUuid("chunk", k)).param("mv", versionUuidDerived).param("node", nodeUuid).param("idx", ordinal).param("content", v.content())
                    .param("ps", v.pageStart()).param("pe", v.pageEnd()).param("hp", "[]")
                    .param("ct", v.contentType()).param("em", em).param("q", v.quality()).update();
        });

        corpus.indexGenerations().forEach((k, v) -> jdbc.sql("INSERT INTO index_generations (id, embedding_provider, embedding_model, embedding_dimension, chunking_version, status, created_at) VALUES (:id, 'synthetic', :name, :dim, 'v1', 'ACTIVE', now())")
                .param("id", deriveUuid("gen", k)).param("name", v.model()).param("dim", v.dimension()).update());

        corpus.chunkEmbeddings().forEach((k, v) -> {
            UUID chunkId = deriveUuid("chunk", k);
            UUID genId = deriveUuid("gen", "gen-v1");
            // Convert float[] to pgvector format [v1, v2, ...]
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < v.length; i++) {
                sb.append(v[i]).append(i < v.length - 1 ? "," : "");
            }
            sb.append("]");
            jdbc.sql("INSERT INTO chunk_embeddings (id, chunk_id, index_generation_id, embedding, created_at) VALUES (:id, :chunk, :gen, CAST(:vec AS vector), now())")
                    .param("id", UUID.randomUUID()).param("chunk", chunkId).param("gen", genId).param("vec", sb.toString()).update();
        });
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
        if ("node".equals(type)) {
            return corpusReference.documentNodes().entrySet().stream()
                    .filter(e -> deriveUuid("node", e.getKey()).equals(uuid))
                    .map(Map.Entry::getKey)
                    .findFirst().orElse("unknown");
        }
        return "unknown";
    }
}
