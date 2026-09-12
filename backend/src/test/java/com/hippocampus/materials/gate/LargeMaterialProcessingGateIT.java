package com.hippocampus.materials.gate;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.identity.infrastructure.persistence.UserEntity;
import com.hippocampus.identity.infrastructure.persistence.UserRepository;
import com.hippocampus.identity.infrastructure.persistence.UserStatus;
import com.hippocampus.identity.infrastructure.security.HippocampusPrincipal;
import com.hippocampus.materials.application.ClaimNextProcessingJob;
import com.hippocampus.materials.application.CompleteProcessingStage;
import com.hippocampus.materials.application.ExecuteClaimedProcessingJob;
import com.hippocampus.materials.application.MaterialUploadResult;
import com.hippocampus.materials.application.ChunkMaterialText;
import com.hippocampus.materials.application.FinalizeChunking;
import com.hippocampus.materials.application.FinalizeTextNormalization;
import com.hippocampus.materials.application.NormalizeMaterialText;
import com.hippocampus.materials.application.PersistChunkBatch;
import com.hippocampus.materials.application.PersistNormalizedText;
import com.hippocampus.materials.application.PersistPdfPageBatch;
import com.hippocampus.materials.application.UploadMaterial;
import com.hippocampus.materials.domain.ChunkDraft;
import com.hippocampus.materials.domain.ChunkIdentity;
import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.DeterministicChunkTokenCounter;
import com.hippocampus.materials.domain.DocumentNode;
import com.hippocampus.materials.domain.ExtractionNormalizationPolicy;
import com.hippocampus.materials.domain.HierarchyAwareChunkingPolicy;
import com.hippocampus.materials.domain.PdfPageBatch;
import com.hippocampus.materials.domain.ProcessingJobType;
import com.hippocampus.materials.domain.TextBlock;
import com.hippocampus.materials.infrastructure.config.ChunkingProperties;
import com.hippocampus.materials.infrastructure.config.PdfExtractionProperties;
import com.hippocampus.materials.infrastructure.config.PdfStructureInspectionProperties;
import com.hippocampus.materials.infrastructure.persistence.JdbcChunkRepository;
import com.hippocampus.materials.infrastructure.persistence.JdbcTextNormalizationRepository;
import com.hippocampus.materials.infrastructure.storage.filesystem.FileSystemBinaryObjectStore;
import com.hippocampus.materials.port.BinaryObjectKey;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.ChunkingSourceRepository;
import com.hippocampus.materials.port.PdfExtractionPersistence;
import com.hippocampus.materials.port.TextNormalizationSourceRepository;
import com.hippocampus.testing.PostgresIntegrationTestSupport;

@Tag("phase3-gate")
class LargeMaterialProcessingGateIT extends PostgresIntegrationTestSupport {
    private static final String WORKER_A = "phase3-gate-a";
    private static final String WORKER_B = "phase3-gate-b";
    private static final String WORKER_C = "phase3-gate-c";
    private static final Duration WAIT = Duration.ofSeconds(20);

    private Path storageRoot;
    private Path fixtureDirectory;

    @BeforeEach
    void reset() throws Exception {
        resetPostgresSchema();
        storageRoot = Files.createTempDirectory("hippocampus-phase3-store-");
        fixtureDirectory = Files.createTempDirectory("hippocampus-phase3-fixture-");
        GateConfiguration.storageRoot = storageRoot;
        CrashBeforeCompletionConfiguration.reset();
    }

    @AfterEach
    void removePrivateTestFiles() throws IOException {
        deleteTree(fixtureDirectory);
        deleteTree(storageRoot);
    }

    @Test
    void processesLargeMixedMaterialAcrossRestartAndRecoversAStaleChunkWorker() throws Exception {
        Path pdf = SyntheticLargeMedicalPdfFixture.create(fixtureDirectory);
        assertThat(Files.size(pdf)).isPositive();

        MaterialUploadResult upload;
        UUID structureJobId;
        List<String> extractedIdentity;
        try (ConfigurableApplicationContext contextA = context(GateConfiguration.class)) {
            upload = authenticatedUpload(contextA, pdf);
            JdbcClient jdbc = contextA.getBean(JdbcClient.class);
            assertOwnedUpload(jdbc, upload);
            executeNext(contextA, WORKER_A, ProcessingJobType.MATERIAL_VALIDATE);
            executeNext(contextA, WORKER_A, ProcessingJobType.MATERIAL_EXTRACT);
            assertJob(jdbc, upload.versionId(), ProcessingJobType.MATERIAL_VALIDATE, "COMPLETED");
            assertJob(jdbc, upload.versionId(), ProcessingJobType.MATERIAL_EXTRACT, "COMPLETED");
            structureJobId = jobId(jdbc, upload.versionId(), ProcessingJobType.STRUCTURE_DETECT);
            assertJob(jdbc, upload.versionId(), ProcessingJobType.STRUCTURE_DETECT, "PENDING");
            assertThat(pageCount(jdbc, upload.versionId())).isEqualTo(SyntheticLargeMedicalPdfFixture.PAGE_COUNT);
            extractedIdentity = textBlockIdentity(jdbc, upload.versionId());
            assertThat(extractedIdentity).hasSize(SyntheticLargeMedicalPdfFixture.PAGE_COUNT);
            assertProgress(contextA, ProcessingJobType.MATERIAL_EXTRACT, 20, 601);
            assertThat(contextA.getBean(BoundaryRecorder.class).extractionBatchMaximum()).isLessThanOrEqualTo(20);
        }

        List<String> chunksBeforeRetry;
        List<String> linksBeforeRetry;
        UUID chunkJobId;
        Instant heartbeatAtClaim;
        int chunkAttempt;
        ConfigurableApplicationContext contextB = context(
                GateConfiguration.class, CrashBeforeCompletionConfiguration.class);
        try {
            JdbcClient jdbc = contextB.getBean(JdbcClient.class);
            ClaimedProcessingJob resumed = claim(contextB, WORKER_B);
            assertThat(resumed.jobId()).isEqualTo(structureJobId);
            assertThat(resumed.jobType()).isEqualTo(ProcessingJobType.STRUCTURE_DETECT);
            contextB.getBean(ExecuteClaimedProcessingJob.class).execute(resumed);
            assertThat(textBlockIdentity(jdbc, upload.versionId())).isEqualTo(extractedIdentity);

            executeNext(contextB, WORKER_B, ProcessingJobType.VISUAL_EXTRACT);
            executeNext(contextB, WORKER_B, ProcessingJobType.NORMALIZE);
            assertProgress(contextB, ProcessingJobType.NORMALIZE, 20, 601);
            BoundaryRecorder boundaries = contextB.getBean(BoundaryRecorder.class);
            assertThat(boundaries.normalizationRangeMaximum()).isLessThanOrEqualTo(20);
            assertThat(boundaries.normalizationPersistencePageSpanMaximum()).isLessThanOrEqualTo(20);

            ClaimedProcessingJob chunk = claim(contextB, WORKER_B);
            assertThat(chunk.jobType()).isEqualTo(ProcessingJobType.CHUNK);
            chunkJobId = chunk.jobId();
            chunkAttempt = chunk.attemptNumber();
            heartbeatAtClaim = heartbeat(jdbc, chunkJobId);
            CrashBeforeCompletionConfiguration.arm();

            var executor = Executors.newSingleThreadExecutor();
            Future<?> abandoned = executor.submit(() -> contextB.getBean(ExecuteClaimedProcessingJob.class).execute(chunk));
            assertThat(CrashBeforeCompletionConfiguration.awaitCompletionBoundary(WAIT)).isTrue();
            await(() -> heartbeat(jdbc, chunkJobId).isAfter(heartbeatAtClaim), WAIT);
            Instant refreshed = heartbeat(jdbc, chunkJobId);
            assertThat(refreshed).isAfter(heartbeatAtClaim);
            assertRunningOwnership(jdbc, chunkJobId, WORKER_B, chunkAttempt);
            chunksBeforeRetry = chunkSnapshot(jdbc, upload.versionId());
            linksBeforeRetry = linkSnapshot(jdbc, upload.versionId());
            assertThat(chunksBeforeRetry).isNotEmpty();

            contextB.close();
            abandoned.cancel(true);
            executor.shutdownNow();
            assertThat(await(() -> abandoned.isDone(), WAIT)).isTrue();
        } finally {
            if (contextB.isActive()) contextB.close();
        }

        await(() -> staleHeartbeat(chunkJobId), Duration.ofSeconds(10));
        try (ConfigurableApplicationContext contextC = context(GateConfiguration.class)) {
            JdbcClient jdbc = contextC.getBean(JdbcClient.class);
            ClaimedProcessingJob reclaimed = claim(contextC, WORKER_C);
            assertThat(reclaimed.jobId()).isEqualTo(chunkJobId);
            assertThat(reclaimed.jobType()).isEqualTo(ProcessingJobType.CHUNK);
            assertThat(reclaimed.attemptNumber()).isEqualTo(chunkAttempt + 1);
            contextC.getBean(ExecuteClaimedProcessingJob.class).execute(reclaimed);

            assertThat(chunkSnapshot(jdbc, upload.versionId())).isEqualTo(chunksBeforeRetry);
            assertThat(linkSnapshot(jdbc, upload.versionId())).isEqualTo(linksBeforeRetry);
            assertProgress(contextC, ProcessingJobType.CHUNK, 100, chunksBeforeRetry.size());
            BoundaryRecorder boundaries = contextC.getBean(BoundaryRecorder.class);
            assertThat(boundaries.chunkRangeMaximum()).isLessThanOrEqualTo(20);
            assertThat(boundaries.chunkPersistenceMaximum()).isLessThanOrEqualTo(100);
            assertProvenance(contextC, upload);
            assertPreIndexBoundary(jdbc, upload);
        }
    }

    private ConfigurableApplicationContext context(Class<?>... sources) {
        String[] arguments = {
                "--hippocampus.materials.processing.recovery.enabled=false",
                "--hippocampus.materials.processing.recovery.heartbeat-interval=PT0.2S",
                "--hippocampus.materials.processing.recovery.stale-timeout=PT1S",
                "--hippocampus.materials.processing.pdf.page-batch-size=20",
                "--hippocampus.materials.processing.chunk.persistence-batch-size=100"
        };
        return startApplicationWithFlywayAndArguments(sources, arguments);
    }

    private static MaterialUploadResult authenticatedUpload(ConfigurableApplicationContext context, Path pdf)
            throws IOException {
        UserEntity owner = context.getBean(UserRepository.class).saveAndFlush(
                new UserEntity("phase3-gate@example.test", "Synthetic Student", UserStatus.ACTIVE));
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new HippocampusPrincipal(owner.getId(), owner.getEmail()), null, List.of()));
        try {
            return context.getBean(UploadMaterial.class).execute(new UploadMaterial.Command(
                    "synthetic-large-medical.pdf", "application/pdf", Files.size(pdf),
                    () -> Files.newInputStream(pdf)));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static void assertOwnedUpload(JdbcClient jdbc, MaterialUploadResult upload) {
        List<String> rows = jdbc.sql("""
                SELECT m.user_id || '|' || mv.material_id || '|' || pj.job_type || '|' || pj.status
                FROM materials m JOIN material_versions mv ON mv.material_id=m.id
                JOIN processing_jobs pj ON pj.material_version_id=mv.id
                WHERE m.id=:material AND mv.id=:version
                """).param("material", upload.materialId()).param("version", upload.versionId())
                .query(String.class).list();
        assertThat(rows).singleElement().asString().contains("|" + upload.materialId() + "|MATERIAL_VALIDATE|PENDING");
    }

    private static void executeNext(ConfigurableApplicationContext context, String worker, ProcessingJobType expected) {
        ClaimedProcessingJob job = claim(context, worker);
        assertThat(job.jobType()).isEqualTo(expected);
        context.getBean(ExecuteClaimedProcessingJob.class).execute(job);
    }

    private static ClaimedProcessingJob claim(ConfigurableApplicationContext context, String worker) {
        return context.getBean(ClaimNextProcessingJob.class).execute(worker).orElseThrow();
    }

    private static void assertJob(JdbcClient jdbc, UUID version, ProcessingJobType type, String status) {
        assertThat(jdbc.sql("SELECT status FROM processing_jobs WHERE material_version_id=:v AND job_type=:t")
                .param("v", version).param("t", type.name()).query(String.class).single()).isEqualTo(status);
    }

    private static UUID jobId(JdbcClient jdbc, UUID version, ProcessingJobType type) {
        return jdbc.sql("SELECT id FROM processing_jobs WHERE material_version_id=:v AND job_type=:t")
                .param("v", version).param("t", type.name()).query(UUID.class).single();
    }

    private static int pageCount(JdbcClient jdbc, UUID version) {
        return jdbc.sql("SELECT page_count FROM material_versions WHERE id=:v").param("v", version)
                .query(Integer.class).single();
    }

    private static List<String> textBlockIdentity(JdbcClient jdbc, UUID version) {
        return jdbc.sql("""
                SELECT id || '|' || page_number || '|' || block_type || '|' || ordinal || '|' || extraction_method
                FROM text_blocks WHERE material_version_id=:v AND block_type='PAGE_TEXT' ORDER BY page_number
                """).param("v", version).query(String.class).list();
    }

    private static Instant heartbeat(JdbcClient jdbc, UUID job) {
        return jdbc.sql("SELECT last_heartbeat_at FROM processing_jobs WHERE id=:id").param("id", job)
                .query(Instant.class).single();
    }

    private static void assertRunningOwnership(JdbcClient jdbc, UUID job, String worker, int attempt) {
        String state = jdbc.sql("SELECT status || '|' || locked_by || '|' || attempt_count FROM processing_jobs WHERE id=:id")
                .param("id", job).query(String.class).single();
        assertThat(state).isEqualTo("RUNNING|" + worker + "|" + attempt);
    }

    private static boolean staleHeartbeat(UUID job) {
        try (var connection = openPostgresConnection(); var statement = connection.prepareStatement("""
                SELECT last_heartbeat_at < CURRENT_TIMESTAMP - INTERVAL '1 second'
                FROM processing_jobs WHERE id=? AND status='RUNNING'
                """)) {
            statement.setObject(1, job);
            try (var result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static List<String> chunkSnapshot(JdbcClient jdbc, UUID version) {
        return jdbc.sql("""
                SELECT id || '|' || chunk_index || '|' || document_node_id || '|' || md5(content) || '|'
                    || page_start || '|' || page_end || '|' || content_type || '|' || extraction_method
                FROM chunks WHERE material_version_id=:v ORDER BY chunk_index
                """).param("v", version).query(String.class).list();
    }

    private static List<String> linkSnapshot(JdbcClient jdbc, UUID version) {
        return jdbc.sql("""
                SELECT l.chunk_id || '|' || l.text_block_id || '|' || l.source_position || '|' || l.is_overlap
                FROM chunk_text_block_links l WHERE l.material_version_id=:v
                ORDER BY l.chunk_id,l.source_position
                """).param("v", version).query(String.class).list();
    }

    private static void assertProgress(ConfigurableApplicationContext context, ProcessingJobType type,
            long maximumStep, long finalTotal) {
        RecordingExecutionRepository recorder = context.getBean(RecordingExecutionRepository.class);
        List<Progress> observations = recorder.progress.stream().filter(p -> p.type() == type).toList();
        assertThat(observations).isNotEmpty();
        assertThat(observations.getFirst().current()).isPositive();
        long previous = 0;
        Long knownTotal = null;
        for (Progress progress : observations) {
            assertThat(progress.current()).isGreaterThanOrEqualTo(previous);
            assertThat(progress.current() - previous).isLessThanOrEqualTo(maximumStep);
            if (progress.total() != null) {
                assertThat(progress.current()).isLessThanOrEqualTo(progress.total());
                if (knownTotal != null) assertThat(progress.total()).isEqualTo(knownTotal);
                knownTotal = progress.total();
            }
            previous = progress.current();
        }
        Progress last = observations.getLast();
        assertThat(last.current()).isEqualTo(finalTotal);
        assertThat(last.total()).isEqualTo(finalTotal);
    }

    private static void assertProvenance(ConfigurableApplicationContext context, MaterialUploadResult upload)
            throws IOException {
        JdbcClient jdbc = context.getBean(JdbcClient.class);
        UUID version = upload.versionId();
        assertThat(jdbc.sql("""
                SELECT count(*) FROM document_nodes WHERE material_version_id=:v AND node_type='DOCUMENT'
                  AND parent_id IS NULL AND start_page=1 AND end_page=601
                """).param("v", version).query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("""
                SELECT count(*) FROM document_nodes WHERE material_version_id=:v
                  AND node_type IN ('CHAPTER','SECTION') AND start_page BETWEEN 1 AND 601 AND end_page BETWEEN start_page AND 601
                """).param("v", version).query(Integer.class).single()).isGreaterThan(1);

        UUID tableBlock = jdbc.sql("""
                SELECT id FROM text_blocks WHERE material_version_id=:v AND page_number=:p AND block_type='TABLE_TEXT'
                  AND content LIKE '%Na+%' AND content LIKE '%Ca2+%' AND content LIKE E'%\\t%'
                ORDER BY ordinal LIMIT 1
                """).param("v", version).param("p", SyntheticLargeMedicalPdfFixture.TABLE_PAGE)
                .query(UUID.class).single();
        assertThat(jdbc.sql("""
                SELECT count(*) FROM chunks c JOIN chunk_text_block_links l ON l.chunk_id=c.id
                WHERE c.material_version_id=:v AND c.content_type='TABLE' AND l.text_block_id=:block
                """).param("v", version).param("block", tableBlock).query(Integer.class).single()).isPositive();

        Visual visual = jdbc.sql("""
                SELECT va.id,va.storage_key,va.content_hash,va.page_number
                FROM visual_assets va JOIN document_nodes dn ON dn.id=va.document_node_id
                WHERE va.material_version_id=:v AND va.page_number=:p AND dn.material_version_id=:v
                ORDER BY va.id LIMIT 1
                """).param("v", version).param("p", SyntheticLargeMedicalPdfFixture.MIXED_VISUAL_PAGE)
                .query((row, ignored) -> new Visual(row.getObject(1, UUID.class), row.getString(2), row.getString(3), row.getInt(4)))
                .single();
        assertThat(visual.hash()).isNotBlank();
        assertThat(visual.storageKey()).isNotBlank();
        assertThat(visual.page()).isEqualTo(SyntheticLargeMedicalPdfFixture.MIXED_VISUAL_PAGE);
        Path resolved = Files.createTempFile("phase3-visual-", ".bin");
        try (OutputStream destination = Files.newOutputStream(resolved)) {
            context.getBean(BinaryObjectStore.class).get(new BinaryObjectKey(visual.storageKey()), destination);
        }
        assertThat(Files.size(resolved)).isPositive();

        List<ChunkRow> chunks = jdbc.sql("""
                SELECT id,chunk_index,page_start,page_end,content,extraction_method,content_type
                FROM chunks WHERE material_version_id=:v ORDER BY chunk_index
                """).param("v", version).query((row, ignored) -> new ChunkRow(
                        row.getObject(1, UUID.class), row.getInt(2), row.getInt(3), row.getInt(4),
                        row.getString(5), row.getString(6), row.getString(7))).list();
        assertThat(chunks).isNotEmpty();
        for (int index = 0; index < chunks.size(); index++) {
            ChunkRow chunk = chunks.get(index);
            assertThat(chunk.index()).isEqualTo(index + 1);
            assertThat(chunk.id()).isEqualTo(ChunkIdentity.forChunk(version, index + 1));
            assertThat(chunk.start()).isBetween(1, 601);
            assertThat(chunk.end()).isBetween(chunk.start(), 601);
        }
        assertThat(chunks).extracting(ChunkRow::id).doesNotHaveDuplicates();
        String allContent = chunks.stream().map(ChunkRow::content).reduce("", (left, right) -> left + "\n" + right);
        assertThat(allContent).contains("Na+", "K+", "Ca2+", "C5-T1", "CN VII", "IL-6", "pH");
        assertThat(jdbc.sql("""
                SELECT count(*) FROM chunks c JOIN chunk_text_block_links l ON l.chunk_id=c.id
                JOIN text_blocks t ON t.id=l.text_block_id
                WHERE c.material_version_id=:v AND c.extraction_method<>t.extraction_method
                """).param("v", version).query(Integer.class).single()).isZero();
    }

    private static void assertPreIndexBoundary(JdbcClient jdbc, MaterialUploadResult upload) {
        assertThat(jdbc.sql("SELECT count(*) FROM processing_jobs WHERE material_version_id=:v AND status='COMPLETED'")
                .param("v", upload.versionId()).query(Integer.class).single()).isEqualTo(6);
        assertThat(jdbc.sql("SELECT count(*) FROM processing_jobs WHERE material_version_id=:v AND job_type='EMBED'")
                .param("v", upload.versionId()).query(Integer.class).single()).isZero();
        String state = jdbc.sql("""
                SELECT m.status || '|' || mv.processing_status || '|' || (m.active_version_id IS NULL) || '|'
                    || (mv.activated_at IS NULL)
                FROM materials m JOIN material_versions mv ON mv.material_id=m.id
                WHERE m.id=:m AND mv.id=:v
                """).param("m", upload.materialId()).param("v", upload.versionId()).query(String.class).single();
        assertThat(state).isEqualTo("PROCESSING|PROCESSING|true|true");
        assertThat(jdbc.sql("SELECT count(*) FROM processing_jobs WHERE material_version_id=:v")
                .param("v", upload.versionId()).query(Integer.class).single()).isEqualTo(6);
    }

    private static boolean await(BooleanSupplier condition, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return true;
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(25));
        }
        return condition.getAsBoolean();
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || Files.notExists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private record Progress(ProcessingJobType type, long current, Long total) {}
    private record Visual(UUID id, String storageKey, String hash, int page) {}
    private record ChunkRow(UUID id, int index, int start, int end, String content, String method, String type) {}

    @Configuration(proxyBeanMethods = false)
    static class GateConfiguration {
        static Path storageRoot;

        @Bean
        BinaryObjectStore binaryObjectStore() {
            return new FileSystemBinaryObjectStore(storageRoot);
        }

        @Bean
        @Primary
        RecordingExecutionRepository recordingExecutionRepository(
                @Qualifier("processingJobExecutionRepository") com.hippocampus.materials.port.ProcessingJobExecutionRepository delegate) {
            return new RecordingExecutionRepository(delegate);
        }

        @Bean
        BoundaryRecorder boundaryRecorder() {
            return new BoundaryRecorder();
        }

        @Bean
        @Primary
        PersistPdfPageBatch recordingPdfPagePersistence(PdfExtractionPersistence persistence,
                BoundaryRecorder recorder) {
            return new PersistPdfPageBatch(persistence) {
                @Override
                @Transactional
                public void execute(UUID version, PdfPageBatch batch) {
                    recorder.extractionBatches.add(batch.pages().size());
                    super.execute(version, batch);
                }
            };
        }

        @Bean
        @Primary
        PersistNormalizedText recordingNormalizedPersistence(JdbcTextNormalizationRepository repository,
                BoundaryRecorder recorder) {
            return new PersistNormalizedText(repository) {
                @Override
                @Transactional
                public void execute(UUID version, List<TextBlock> blocks) {
                    if (!blocks.isEmpty()) {
                        int first = blocks.stream().mapToInt(TextBlock::pageNumber).min().orElseThrow();
                        int last = blocks.stream().mapToInt(TextBlock::pageNumber).max().orElseThrow();
                        recorder.normalizationPersistenceSpans.add(last - first + 1);
                    }
                    super.execute(version, blocks);
                }
            };
        }

        @Bean
        @Primary
        NormalizeMaterialText recordingNormalizeMaterialText(JdbcTextNormalizationRepository repository,
                PersistNormalizedText persistence, FinalizeTextNormalization finalization,
                PdfExtractionProperties properties, BoundaryRecorder recorder) {
            TextNormalizationSourceRepository recordingSource = new TextNormalizationSourceRepository() {
                @Override public int requirePageCount(UUID version) { return repository.requirePageCount(version); }
                @Override public List<TextBlock> findPageText(UUID version, int first, int last) {
                    recorder.normalizationRanges.add(last - first + 1);
                    return repository.findPageText(version, first, last);
                }
                @Override public List<TextBlock> findTableText(UUID version, int first, int last) {
                    recorder.normalizationRanges.add(last - first + 1);
                    return repository.findTableText(version, first, last);
                }
            };
            return new NormalizeMaterialText(recordingSource, new ExtractionNormalizationPolicy(), persistence,
                    finalization, properties.pageBatchSize());
        }

        @Bean
        @Primary
        PersistChunkBatch recordingChunkPersistence(JdbcChunkRepository repository, BoundaryRecorder recorder) {
            return new PersistChunkBatch(repository) {
                @Override
                @Transactional
                public void execute(UUID version, List<ChunkDraft> chunks) {
                    recorder.chunkPersistenceBatches.add(chunks.size());
                    super.execute(version, chunks);
                }
            };
        }

        @Bean
        @Primary
        ChunkMaterialText recordingChunkMaterialText(JdbcChunkRepository repository, PersistChunkBatch persistence,
                FinalizeChunking finalization, PdfExtractionProperties pdf,
                PdfStructureInspectionProperties structure, ChunkingProperties chunking, BoundaryRecorder recorder) {
            ChunkingSourceRepository recordingSource = new ChunkingSourceRepository() {
                @Override public int requirePageCount(UUID version) { return repository.requirePageCount(version); }
                @Override public List<DocumentNode> findHierarchy(UUID version) { return repository.findHierarchy(version); }
                @Override public List<TextBlock> findByPhysicalPage(UUID version, int first, int last) {
                    recorder.chunkRanges.add(last - first + 1);
                    return repository.findByPhysicalPage(version, first, last);
                }
                @Override public List<VisualSource> findVisualsByPhysicalPage(UUID version, int first, int last) {
                    return repository.findVisualsByPhysicalPage(version, first, last);
                }
            };
            HierarchyAwareChunkingPolicy policy = new HierarchyAwareChunkingPolicy(
                    new DeterministicChunkTokenCounter(), chunking.targetTokenCount(), chunking.hardTokenCount(),
                    chunking.overlapTokenCount());
            return new ChunkMaterialText(recordingSource, policy, persistence, finalization, pdf.pageBatchSize(),
                    chunking.persistenceBatchSize(), structure.maxNodesPerDocument(), structure.maxOutlineDepth());
        }
    }

    static final class BoundaryRecorder {
        private final List<Integer> extractionBatches = new java.util.concurrent.CopyOnWriteArrayList<>();
        private final List<Integer> normalizationRanges = new java.util.concurrent.CopyOnWriteArrayList<>();
        private final List<Integer> normalizationPersistenceSpans = new java.util.concurrent.CopyOnWriteArrayList<>();
        private final List<Integer> chunkRanges = new java.util.concurrent.CopyOnWriteArrayList<>();
        private final List<Integer> chunkPersistenceBatches = new java.util.concurrent.CopyOnWriteArrayList<>();

        int extractionBatchMaximum() { return extractionBatches.stream().mapToInt(Integer::intValue).max().orElseThrow(); }
        int normalizationRangeMaximum() { return normalizationRanges.stream().mapToInt(Integer::intValue).max().orElseThrow(); }
        int normalizationPersistencePageSpanMaximum() {
            return normalizationPersistenceSpans.stream().mapToInt(Integer::intValue).max().orElseThrow();
        }
        int chunkRangeMaximum() { return chunkRanges.stream().mapToInt(Integer::intValue).max().orElseThrow(); }
        int chunkPersistenceMaximum() {
            return chunkPersistenceBatches.stream().mapToInt(Integer::intValue).max().orElseThrow();
        }
    }

    static final class RecordingExecutionRepository implements com.hippocampus.materials.port.ProcessingJobExecutionRepository {
        private final com.hippocampus.materials.port.ProcessingJobExecutionRepository delegate;
        private final List<Progress> progress = new java.util.concurrent.CopyOnWriteArrayList<>();

        RecordingExecutionRepository(com.hippocampus.materials.port.ProcessingJobExecutionRepository delegate) {
            this.delegate = delegate;
        }

        @Override public boolean heartbeat(ClaimedProcessingJob job) { return delegate.heartbeat(job); }
        @Override public boolean progress(ClaimedProcessingJob job, long current, Long total) {
            boolean updated = delegate.progress(job, current, total);
            if (updated) progress.add(new Progress(job.jobType(), current, total));
            return updated;
        }
        @Override public boolean retry(ClaimedProcessingJob job, String code, Duration delay) {
            return delegate.retry(job, code, delay);
        }
        @Override public boolean fail(ClaimedProcessingJob job, String code) { return delegate.fail(job, code); }
    }

    @Configuration(proxyBeanMethods = false)
    static class CrashBeforeCompletionConfiguration {
        private static final CountDownLatch NEVER = new CountDownLatch(1);
        private static volatile CountDownLatch reached = new CountDownLatch(1);
        private static volatile boolean armed;

        static void reset() { reached = new CountDownLatch(1); armed = false; }
        static void arm() { armed = true; }
        static boolean awaitCompletionBoundary(Duration timeout) throws InterruptedException {
            return reached.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        @Bean
        @Primary
        CompleteProcessingStage crashWindowCompleteProcessingStage(
                @Qualifier("processingJobStageCompletionRepository")
                com.hippocampus.materials.port.ProcessingJobStageCompletionRepository jobs,
                com.hippocampus.materials.application.DeriveMaterialReadiness readiness) {
            return new CompleteProcessingStage(jobs, readiness) {
                @Override
                public void execute(ClaimedProcessingJob job, com.hippocampus.materials.application.ProcessingStageResult result) {
                    if (armed && job.jobType() == ProcessingJobType.CHUNK) {
                        reached.countDown();
                        try {
                            NEVER.await();
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new SimulatedWorkerDeath();
                        }
                    }
                    super.execute(job, result);
                }
            };
        }
    }

    static final class SimulatedWorkerDeath extends Error {}
}
