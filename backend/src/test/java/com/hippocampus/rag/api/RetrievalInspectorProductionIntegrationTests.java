package com.hippocampus.rag.api;

import static com.hippocampus.testing.security.OwnershipTestRequests.authenticatedAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.hippocampus.rag.port.EmbeddingBatchResult;
import com.hippocampus.rag.port.EmbeddingModelMetadata;
import com.hippocampus.rag.port.EmbeddingPort;
import com.hippocampus.rag.port.EmbeddingUsageMetadata;
import com.hippocampus.rag.port.EmbeddingVector;
import com.hippocampus.rag.port.EmbeddingVectorResult;
import com.hippocampus.testing.PostgresIntegrationTestSupport;
import com.hippocampus.testing.security.OwnershipTestUser;

class RetrievalInspectorProductionIntegrationTests extends PostgresIntegrationTestSupport {
    private static final String PRIVATE = "PRIVATE POSTERIOR CORD SOURCE TEXT";
    private static final String FOREIGN_PRIVATE = "FOREIGN PRIVATE POSTERIOR CORD SOURCE TEXT";

    @BeforeEach void reset() throws SQLException { resetPostgresSchema(); }

    @Test
    void productionComposedInspectorShowsAuthorizedSignalsAndNeverSourceTextOrForeignCandidate() throws Exception {
        try (var context = startApplicationWithFlywayAndArguments(new Class<?>[] {FakeEmbeddingConfiguration.class},
                "--spring.profiles.active=local,test",
                "--hippocampus.rag.inspector.enabled=true",
                "--hippocampus.materials.processing.recovery.enabled=false")) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID owner = user(jdbc, "owner");
            UUID foreign = user(jdbc, "foreign");
            UUID topic = topic(jdbc, owner);
            Material owned = material(jdbc, owner);
            Material other = material(jdbc, foreign);
            UUID node = node(jdbc, owned.version(), "Posterior Cord");
            UUID foreignNode = node(jdbc, other.version(), "Foreign Posterior Cord");
            link(jdbc, topic, owned);
            UUID ownedChunk = chunk(jdbc, owned.version(), node, PRIVATE);
            UUID foreignChunk = chunk(jdbc, other.version(), foreignNode, FOREIGN_PRIVATE);
            UUID generation = generation(jdbc);
            embedding(jdbc, ownedChunk, generation, "[0.8,0.2]");
            embedding(jdbc, foreignChunk, generation, "[1,0]");

            OwnershipTestUser principal = new OwnershipTestUser(owner, "owner@example.test");
            MockMvc mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context)
                    .apply(springSecurity()).build();
            String response = mvc.perform(post("/api/dev/rag/inspect")
                            .with(authenticatedAs(principal)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"topicId":"%s","query":"posterior cord","groundingMode":"STRICT_SOURCE"}
                                    """.formatted(topic)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.lexical.candidateCount").value(1))
                    .andExpect(jsonPath("$.lexical.candidates[0].chunkId").value(ownedChunk.toString()))
                    .andExpect(jsonPath("$.lexical.candidates[0].headingPath[0]").value("Posterior Cord"))
                    .andExpect(jsonPath("$.lexical.candidates[0].pageStart").value(12))
                    .andExpect(jsonPath("$.lexical.candidates[0].quality").value("STRONG"))
                    .andExpect(jsonPath("$.lexical.candidates[0].fullTextRank").isNumber())
                    .andExpect(jsonPath("$.vector.status").value("AVAILABLE"))
                    .andExpect(jsonPath("$.vector.candidates[0].chunkId").value(ownedChunk.toString()))
                    .andExpect(jsonPath("$.vector.candidates[0].cosineSimilarity").isNumber())
                    .andExpect(jsonPath("$.hybrid.candidates[0].chunkId").value(ownedChunk.toString()))
                    .andExpect(jsonPath("$.hybrid.candidates[0].fusionScore").isNumber())
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(PRIVATE))))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(FOREIGN_PRIVATE))))
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString(foreignChunk.toString()))))
                    .andReturn().getResponse().getContentAsString();

            assertThat(response).contains("Posterior Cord", ownedChunk.toString(), generation.toString());
        }
    }

    private static UUID user(JdbcClient jdbc, String name) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO users(id,email,status,created_at,updated_at) VALUES (?,?,'ACTIVE',now(),now())")
                .params(id, name + "-" + id + "@example.test").update();
        return id;
    }

    private static UUID topic(JdbcClient jdbc, UUID user) {
        UUID subject = UUID.randomUUID();
        UUID topic = UUID.randomUUID();
        jdbc.sql("INSERT INTO subjects(id,user_id,name,status,created_at,updated_at) VALUES (?,?,'Neuro','ACTIVE',now(),now())")
                .params(subject, user).update();
        jdbc.sql("INSERT INTO topics(id,subject_id,name,status,created_at,updated_at) VALUES (?,?,'Spinal cord','ACTIVE',now(),now())")
                .params(topic, subject).update();
        return topic;
    }

    private static Material material(JdbcClient jdbc, UUID user) {
        UUID material = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        jdbc.sql("INSERT INTO materials(id,user_id,title,material_type,status,created_at,updated_at) VALUES (?,?,'Neuro source','PDF','ACTIVE',now(),now())")
                .params(material, user).update();
        jdbc.sql("INSERT INTO material_versions(id,material_id,version_number,processing_status,created_at) VALUES (?,?,1,'READY',now())")
                .params(version, material).update();
        jdbc.sql("UPDATE materials SET active_version_id=? WHERE id=?").params(version, material).update();
        return new Material(material, version);
    }

    private static UUID node(JdbcClient jdbc, UUID version, String title) {
        UUID node = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO document_nodes(id,material_version_id,node_type,title,ordinal,start_page,end_page,
                    detection_origin,created_at) VALUES (?,?,'SECTION',?,1,12,13,'NATIVE',now())
                """).params(node, version, title).update();
        return node;
    }

    private static void link(JdbcClient jdbc, UUID topic, Material material) {
        jdbc.sql("""
                INSERT INTO material_topic_links(id,topic_id,material_id,material_version_id,link_origin,status,
                    created_at,updated_at) VALUES (?,?,?,?,'USER_SELECTED','ACTIVE',now(),now())
                """).params(UUID.randomUUID(), topic, material.id(), material.version()).update();
    }

    private static UUID chunk(JdbcClient jdbc, UUID version, UUID node, String content) {
        UUID chunk = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO chunks(id,material_version_id,document_node_id,chunk_index,content,page_start,page_end,
                    heading_path,content_type,extraction_method,quality,is_active,created_at)
                VALUES (?,?,?,1,?,12,13,'["Posterior Cord"]'::jsonb,'TEXT','NATIVE','STRONG',true,now())
                """).params(chunk, version, node, content).update();
        return chunk;
    }

    private static UUID generation(JdbcClient jdbc) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO index_generations(id,embedding_provider,embedding_model,embedding_dimension,
                    chunking_version,status,created_at) VALUES (?,'TEST','synthetic',2,'CHUNKER_V1','ACTIVE',now())
                """).param(id).update();
        return id;
    }

    private static void embedding(JdbcClient jdbc, UUID chunk, UUID generation, String vector) {
        jdbc.sql("""
                INSERT INTO chunk_embeddings(id,chunk_id,index_generation_id,embedding,created_at)
                VALUES (?,?,?,CAST(? AS vector),now())
                """).params(UUID.randomUUID(), chunk, generation, vector).update();
    }

    private record Material(UUID id, UUID version) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeEmbeddingConfiguration {
        @Bean
        EmbeddingPort embeddingPort() {
            EmbeddingModelMetadata model = new EmbeddingModelMetadata("TEST", "synthetic", null, 2);
            return request -> new EmbeddingBatchResult(model, EmbeddingUsageMetadata.unavailable(),
                    List.of(new EmbeddingVectorResult(request.inputs().getFirst().referenceId(),
                            new EmbeddingVector(List.of(1.0F, 0.0F)))));
        }
    }
}
