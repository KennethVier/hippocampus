package com.hippocampus.rag.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.hippocampus.rag.application.EmbedMaterialVersion;
import com.hippocampus.rag.application.PersistEmbeddingBatch;
import com.hippocampus.materials.application.CompleteProcessingStage;
import com.hippocampus.materials.application.ExecuteClaimedProcessingJob;
import com.hippocampus.materials.application.ProcessingFailureClassifier;
import com.hippocampus.materials.application.ProcessingStageHandler;
import com.hippocampus.materials.application.ProcessingStageResult;
import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.ProcessingFailure;
import com.hippocampus.materials.domain.ProcessingJobType;
import com.hippocampus.rag.port.ChunkEmbedding;
import com.hippocampus.rag.port.EmbeddingBatchRequest;
import com.hippocampus.rag.port.EmbeddingBatchResult;
import com.hippocampus.rag.port.EmbeddingFailureException;
import com.hippocampus.rag.port.EmbeddingJobRepository;
import com.hippocampus.rag.port.EmbeddingModelMetadata;
import com.hippocampus.rag.port.EmbeddingPort;
import com.hippocampus.rag.port.EmbeddingUsageMetadata;
import com.hippocampus.rag.port.EmbeddingVector;
import com.hippocampus.rag.port.EmbeddingVectorResult;
import com.hippocampus.rag.port.IndexGeneration;
import com.hippocampus.testing.PostgresIntegrationTestSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class EmbeddingJobPersistenceIntegrationTests extends PostgresIntegrationTestSupport {

    @BeforeEach
    void resetDatabase() throws SQLException {
        resetPostgresSchema();
    }

    @Test
    void persistsRealPgvectorBatchesAndReplayConvergesWithoutDuplicates() {
        try (ConfigurableApplicationContext context = startEmbeddingApplication()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            ScriptedEmbeddingPort provider = context.getBean(ScriptedEmbeddingPort.class);
            UUID version = insertMaterialWithChunks(jdbc, 7, null);
            List<Progress> progress = new ArrayList<>();

            context.getBean(EmbedMaterialVersion.class).execute(
                    version, () -> {}, (current, total) -> progress.add(new Progress(current, total)));

            assertThat(provider.batchSizes()).containsExactly(3, 3, 1);
            assertThat(embeddingCount(jdbc)).isEqualTo(7);
            assertThat(jdbc.sql("SELECT DISTINCT vector_dims(embedding) FROM chunk_embeddings")
                    .query(Integer.class).single()).isEqualTo(3);
            assertThat(progress).containsExactly(
                    new Progress(0, 7), new Progress(3, 7),
                    new Progress(6, 7), new Progress(7, 7));

            provider.clearCalls();
            context.getBean(EmbedMaterialVersion.class).execute(version, () -> {}, (current, total) -> {});
            assertThat(provider.batchSizes()).isEmpty();
            assertThat(embeddingCount(jdbc)).isEqualTo(7);
        }
    }

    @Test
    void durablePartialStateSurvivesProviderFailureAndRetryEmbedsOnlyMissingChunks() {
        try (ConfigurableApplicationContext context = startEmbeddingApplication()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            ScriptedEmbeddingPort provider = context.getBean(ScriptedEmbeddingPort.class);
            UUID version = insertMaterialWithChunks(jdbc, 7, null);
            provider.failOnCall(2);

            assertThatThrownBy(() -> context.getBean(EmbedMaterialVersion.class)
                    .execute(version, () -> {}, (current, total) -> {}))
                    .isInstanceOf(EmbeddingFailureException.class);
            assertThat(embeddingCount(jdbc)).isEqualTo(3);

            provider.clearCalls();
            provider.failOnCall(0);
            context.getBean(EmbedMaterialVersion.class)
                    .execute(version, () -> {}, (current, total) -> {});

            assertThat(provider.batchSizes()).containsExactly(3, 1);
            assertThat(embeddingCount(jdbc)).isEqualTo(7);
        }
    }

    @Test
    void excludesInactiveChunksAndCreatesGenerationFromFirstSuccessfulMetadata() {
        try (ConfigurableApplicationContext context = startEmbeddingApplication()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            ScriptedEmbeddingPort provider = context.getBean(ScriptedEmbeddingPort.class);
            UUID version = insertMaterialWithChunks(jdbc, 4, 2);

            context.getBean(EmbedMaterialVersion.class)
                    .execute(version, () -> {}, (current, total) -> {});

            assertThat(provider.batchSizes()).containsExactly(3);
            assertThat(provider.referenceIds()).doesNotContain(new UUID(0, 2));
            assertThat(embeddingCount(jdbc)).isEqualTo(3);
            assertThat(jdbc.sql("""
                    SELECT embedding_provider || ':' || embedding_model || ':' ||
                           embedding_model_version || ':' || embedding_dimension || ':' ||
                           chunking_version || ':' || status
                    FROM index_generations
                    """).query(String.class).single())
                    .isEqualTo("test-provider:test-model:v1:3:CHUNKER_V1:BUILDING");
        }
    }

    @Test
    void uniquenessIsGenerationScopedAndDimensionTriggerRejectsMalformedVectors() {
        try (ConfigurableApplicationContext context = startEmbeddingApplication()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID version = insertMaterialWithChunks(jdbc, 1, null);
            UUID chunk = new UUID(0, 1);
            UUID first = insertGeneration(jdbc, "first", 3, "BUILDING");
            UUID second = insertGeneration(jdbc, "second", 3, "INACTIVE");

            insertEmbedding(jdbc, chunk, first, "[1,2,3]");
            insertEmbedding(jdbc, chunk, second, "[4,5,6]");
            assertThat(embeddingCount(jdbc)).isEqualTo(2);

            UUID wrongDimension = insertGeneration(jdbc, "wrong", 4, "INACTIVE");
            assertThatThrownBy(() -> insertEmbedding(jdbc, chunk, wrongDimension, "[1,2,3]"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(embeddingCount(jdbc)).isEqualTo(2);
            assertThat(version).isNotNull();
        }
    }

    @Test
    void generationSelectionPrefersOneActiveAndFailsClosedWhenAmbiguous() {
        try (ConfigurableApplicationContext context = startEmbeddingApplication()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            EmbeddingJobRepository repository = context.getBean(EmbeddingJobRepository.class);
            UUID building = insertGeneration(jdbc, "building", 3, "BUILDING");
            UUID active = insertGeneration(jdbc, "active", 3, "ACTIVE");

            assertThat(repository.findPreferredGeneration()).get()
                    .extracting(IndexGeneration::id).isEqualTo(active);

            insertGeneration(jdbc, "other-active", 3, "ACTIVE");
            assertThatThrownBy(repository::findPreferredGeneration)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ambiguous");
            assertThat(building).isNotEqualTo(active);
        }
    }

    @Test
    void concurrentInitialPersistenceCreatesExactlyOneCompatibleGeneration() throws Exception {
        try (ConfigurableApplicationContext context = startEmbeddingApplication()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID version = insertMaterialWithChunks(jdbc, 1, null);
            PersistEmbeddingBatch persistence = context.getBean(PersistEmbeddingBatch.class);
            EmbeddingModelMetadata model = new EmbeddingModelMetadata(
                    "test-provider", "test-model", "v1", 3);
            List<ChunkEmbedding> batch = List.of(new ChunkEmbedding(
                    new UUID(0, 1), new EmbeddingVector(List.of(1.0f, 2.0f, 3.0f))));
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<IndexGeneration> first = executor.submit(() -> {
                    start.await();
                    return persistence.execute(version, null, model, "CHUNKER_V1", batch);
                });
                Future<IndexGeneration> second = executor.submit(() -> {
                    start.await();
                    return persistence.execute(version, null, model, "CHUNKER_V1", batch);
                });
                start.countDown();

                assertThat(first.get(10, TimeUnit.SECONDS).id())
                        .isEqualTo(second.get(10, TimeUnit.SECONDS).id());
                assertThat(jdbc.sql("SELECT count(*) FROM index_generations")
                        .query(Long.class).single()).isEqualTo(1);
                assertThat(embeddingCount(jdbc)).isEqualTo(1);
            } finally {
                executor.shutdownNow();
                assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @Test
    void mixedMaterialBatchFailsClosedAndRollsBackEveryEmbedding() {
        try (ConfigurableApplicationContext context = startEmbeddingApplication()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID requestedVersion = insertMaterialWithChunks(jdbc, 1, null, 0);
            insertMaterialWithChunks(jdbc, 1, null, 1);
            UUID generation = insertGeneration(jdbc, "test-model", 3, "BUILDING");
            List<ChunkEmbedding> mixedBatch = List.of(
                    new ChunkEmbedding(
                            new UUID(0, 1), new EmbeddingVector(List.of(1.0f, 2.0f, 3.0f))),
                    new ChunkEmbedding(
                            new UUID(1, 1), new EmbeddingVector(List.of(4.0f, 5.0f, 6.0f))));

            assertThatThrownBy(() -> context.getBean(PersistEmbeddingBatch.class).execute(
                    requestedVersion,
                    context.getBean(EmbeddingJobRepository.class).findPreferredGeneration().orElseThrow(),
                    new EmbeddingModelMetadata("test-provider", "test-model", "v1", 3),
                    "CHUNKER_V1",
                    mixedBatch))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("requested material version");

            assertThat(jdbc.sql("SELECT count(*) FROM chunk_embeddings WHERE index_generation_id=?")
                    .param(generation).query(Long.class).single()).isZero();
        }
    }

    @Test
    void availableEmbedHandlerCreatesDurableTransitionAndStopsBeforeIndex() {
        try (ConfigurableApplicationContext context = startEmbeddingApplication()) {
            JdbcClient jdbc = context.getBean(JdbcClient.class);
            UUID version = insertMaterialWithChunks(jdbc, 2, null);
            UUID user = jdbc.sql("""
                    SELECT m.user_id FROM materials m
                    JOIN material_versions mv ON mv.material_id=m.id WHERE mv.id=?
                    """).param(version).query(UUID.class).single();
            UUID chunkJob = insertRunningJob(jdbc, user, version, ProcessingJobType.CHUNK);
            assertThat(context.getBeansOfType(ProcessingStageHandler.class).values())
                    .anyMatch(handler -> handler.jobType() == ProcessingJobType.EMBED);

            context.getBean(CompleteProcessingStage.class).execute(
                    new ClaimedProcessingJob(chunkJob, ProcessingJobType.CHUNK, version,
                            "processor-v1", "test-worker", 1, 3),
                    new ProcessingStageResult(ProcessingJobType.CHUNK, ProcessingJobType.EMBED));
            UUID embedJob = jdbc.sql("SELECT id FROM processing_jobs WHERE job_type='EMBED'")
                    .query(UUID.class).single();
            jdbc.sql("""
                    UPDATE processing_jobs SET status='RUNNING', attempt_count=1,
                        locked_by='test-worker', locked_at=CURRENT_TIMESTAMP,
                        last_heartbeat_at=CURRENT_TIMESTAMP
                    WHERE id=?
                    """).param(embedJob).update();

            ProcessingStageResult result = context.getBean(ExecuteClaimedProcessingJob.class).execute(
                    new ClaimedProcessingJob(embedJob, ProcessingJobType.EMBED, version,
                            "processor-v1", "test-worker", 1, 3));

            assertThat(result).isEqualTo(new ProcessingStageResult(ProcessingJobType.EMBED, null));
            assertThat(jdbc.sql("SELECT status FROM processing_jobs WHERE id=?")
                    .param(embedJob).query(String.class).single()).isEqualTo("COMPLETED");
            assertThat(jdbc.sql("SELECT count(*) FROM processing_jobs WHERE job_type='INDEX'")
                    .query(Long.class).single()).isZero();
            assertThat(embeddingCount(jdbc)).isEqualTo(2);
        }
    }

    @Test
    void embeddingFailuresReceiveStableTransientAndFatalRecoveryCodes() {
        try (ConfigurableApplicationContext context = startEmbeddingApplication()) {
            ProcessingFailureClassifier classifier = context.getBean(ProcessingFailureClassifier.class);
            assertThat(classifier.classify(new EmbeddingFailureException(
                    EmbeddingFailureException.Reason.PROVIDER_FAILURE)))
                    .isEqualTo(new ProcessingFailure(
                            ProcessingFailure.Kind.TRANSIENT, "EMBEDDING_PROVIDER_UNAVAILABLE"));
            assertThat(classifier.classify(new EmbeddingFailureException(
                    EmbeddingFailureException.Reason.INVALID_RESPONSE)))
                    .isEqualTo(new ProcessingFailure(
                            ProcessingFailure.Kind.FATAL, "EMBEDDING_RESPONSE_INVALID"));
        }
    }

    private static ConfigurableApplicationContext startEmbeddingApplication() {
        return startApplicationWithFlywayAndArguments(
                new Class<?>[] {ProviderConfiguration.class},
                "--hippocampus.materials.processing.recovery.enabled=false",
                "--hippocampus.rag.embedding.processing.batch-size=3");
    }

    private static UUID insertMaterialWithChunks(
            JdbcClient jdbc, int count, Integer inactiveIndex) {
        return insertMaterialWithChunks(jdbc, count, inactiveIndex, 0);
    }

    private static UUID insertMaterialWithChunks(
            JdbcClient jdbc, int count, Integer inactiveIndex, long chunkNamespace) {
        UUID user = UUID.randomUUID();
        UUID material = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        jdbc.sql("INSERT INTO users(id,email,status,created_at,updated_at) VALUES (?,?,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
                .params(user, user + "@example.test").update();
        jdbc.sql("INSERT INTO materials(id,user_id,title,material_type,mime_type,status,created_at,updated_at) VALUES (?,?,'PDF','PDF','application/pdf','PROCESSING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
                .params(material, user).update();
        jdbc.sql("INSERT INTO material_versions(id,material_id,version_number,processing_status,created_at) VALUES (?,?,1,'PROCESSING',CURRENT_TIMESTAMP)")
                .params(version, material).update();
        for (int index = 1; index <= count; index++) {
            jdbc.sql("""
                    INSERT INTO chunks(
                        id, material_version_id, chunk_index, content, token_count,
                        content_type, extraction_method, source_order, is_active, created_at)
                    VALUES(?,?,?, ?,1,'TEXT','NATIVE',?,?,CURRENT_TIMESTAMP)
                    """).params(new UUID(chunkNamespace, index), version, index, "canonical-" + index,
                            index, !Integer.valueOf(index).equals(inactiveIndex)).update();
        }
        return version;
    }

    private static UUID insertGeneration(
            JdbcClient jdbc, String model, int dimension, String status) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO index_generations(
                    id, embedding_provider, embedding_model, embedding_model_version,
                    embedding_dimension, chunking_version, status, created_at)
                VALUES(?, 'test-provider', ?, 'v1', ?, 'CHUNKER_V1', ?, CURRENT_TIMESTAMP)
                """).params(id, model, dimension, status).update();
        return id;
    }

    private static void insertEmbedding(
            JdbcClient jdbc, UUID chunk, UUID generation, String vector) {
        jdbc.sql("""
                INSERT INTO chunk_embeddings(id,chunk_id,index_generation_id,embedding,created_at)
                VALUES(?,?,?,CAST(? AS vector),CURRENT_TIMESTAMP)
                """).params(UUID.randomUUID(), chunk, generation, vector).update();
    }

    private static long embeddingCount(JdbcClient jdbc) {
        return jdbc.sql("SELECT count(*) FROM chunk_embeddings").query(Long.class).single();
    }

    private static UUID insertRunningJob(
            JdbcClient jdbc, UUID user, UUID version, ProcessingJobType type) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO processing_jobs(
                    id,user_id,material_version_id,job_type,status,priority,attempt_count,max_attempts,
                    locked_by,locked_at,last_heartbeat_at,processing_version,created_at,updated_at)
                VALUES(?,?,?,?,'RUNNING',0,1,3,'test-worker',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,
                       'processor-v1',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """).params(id, user, version, type.name()).update();
        return id;
    }

    private record Progress(long current, long total) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class ProviderConfiguration {
        @Bean
        ScriptedEmbeddingPort scriptedEmbeddingPort() {
            return new ScriptedEmbeddingPort();
        }

    }

    static final class ScriptedEmbeddingPort implements EmbeddingPort {
        private static final EmbeddingModelMetadata MODEL =
                new EmbeddingModelMetadata("test-provider", "test-model", "v1", 3);
        private final List<Integer> batchSizes = new ArrayList<>();
        private final List<UUID> referenceIds = new ArrayList<>();
        private final AtomicInteger calls = new AtomicInteger();
        private int failureCall;

        @Override
        public EmbeddingBatchResult embed(EmbeddingBatchRequest request) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            int call = calls.incrementAndGet();
            batchSizes.add(request.inputs().size());
            request.inputs().forEach(input -> referenceIds.add(input.referenceId()));
            if (call == failureCall) {
                throw new EmbeddingFailureException(EmbeddingFailureException.Reason.PROVIDER_FAILURE);
            }
            return new EmbeddingBatchResult(
                    MODEL,
                    new EmbeddingUsageMetadata(request.inputs().size()),
                    request.inputs().stream()
                            .map(input -> new EmbeddingVectorResult(
                                    input.referenceId(), new EmbeddingVector(List.of(1.0f, 2.0f, 3.0f))))
                            .toList());
        }

        void failOnCall(int call) {
            failureCall = call;
        }

        void clearCalls() {
            calls.set(0);
            batchSizes.clear();
            referenceIds.clear();
        }

        List<Integer> batchSizes() {
            return List.copyOf(batchSizes);
        }

        List<UUID> referenceIds() {
            return List.copyOf(referenceIds);
        }
    }
}
