package com.hippocampus.rag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.hippocampus.rag.port.ChunkEmbedding;
import com.hippocampus.rag.port.EmbeddableChunk;
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

class EmbedMaterialVersionTests {
    private static final UUID VERSION = UUID.randomUUID();
    private static final EmbeddingModelMetadata MODEL =
            new EmbeddingModelMetadata("provider", "model", "v1", 2);

    @Test
    void embedsSevenChunksInDeterministicThreeThreeOneBatchesWithoutChangingContent() {
        FakeRepository repository = new FakeRepository(7);
        List<List<String>> texts = new ArrayList<>();
        EmbeddingPort provider = request -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            texts.add(request.inputs().stream().map(input -> input.text()).toList());
            return successful(request, MODEL);
        };
        List<Progress> progress = new ArrayList<>();

        useCase(repository, provider, 3).execute(
                VERSION, () -> {}, (current, total) -> progress.add(new Progress(current, total)));

        assertThat(texts).containsExactly(
                List.of("canonical-1", "canonical-2", "canonical-3"),
                List.of("canonical-4", "canonical-5", "canonical-6"),
                List.of("canonical-7"));
        assertThat(repository.durable).hasSize(7);
        assertThat(progress).containsExactly(
                new Progress(0, 7), new Progress(3, 7),
                new Progress(6, 7), new Progress(7, 7));
    }

    @Test
    void partialSuccessSurvivesTransientFailureAndRetryConvergesFromMissingChunks() {
        FakeRepository repository = new FakeRepository(7);
        AtomicInteger calls = new AtomicInteger();
        EmbeddingPort failingProvider = request -> {
            if (calls.incrementAndGet() == 2) {
                throw new EmbeddingFailureException(EmbeddingFailureException.Reason.PROVIDER_FAILURE);
            }
            return successful(request, MODEL);
        };

        assertThatThrownBy(() -> useCase(repository, failingProvider, 3)
                .execute(VERSION, () -> {}, (current, total) -> {}))
                .isInstanceOf(EmbeddingFailureException.class)
                .extracting(failure -> ((EmbeddingFailureException) failure).reason())
                .isEqualTo(EmbeddingFailureException.Reason.PROVIDER_FAILURE);
        assertThat(repository.durable).hasSize(3);

        AtomicInteger retryCalls = new AtomicInteger();
        useCase(repository, request -> {
            retryCalls.incrementAndGet();
            return successful(request, MODEL);
        }, 3).execute(VERSION, () -> {}, (current, total) -> {});

        assertThat(retryCalls).hasValue(2);
        assertThat(repository.durable).hasSize(7);
    }

    @Test
    void replayAfterSuccessDoesNotCallProviderOrCreateDuplicates() {
        FakeRepository repository = new FakeRepository(4);
        useCase(repository, request -> successful(request, MODEL), 3)
                .execute(VERSION, () -> {}, (current, total) -> {});
        Map<UUID, ChunkEmbedding> firstState = Map.copyOf(repository.durable);
        AtomicInteger replayCalls = new AtomicInteger();

        useCase(repository, request -> {
            replayCalls.incrementAndGet();
            return successful(request, MODEL);
        }, 3).execute(VERSION, () -> {}, (current, total) -> {});

        assertThat(replayCalls).hasValue(0);
        assertThat(repository.durable).containsExactlyInAnyOrderEntriesOf(firstState);
    }

    @Test
    void malformedBatchIsRejectedBeforeAnyPersistence() {
        FakeRepository repository = new FakeRepository(3);
        EmbeddingPort malformed = request -> new EmbeddingBatchResult(
                MODEL,
                EmbeddingUsageMetadata.unavailable(),
                request.inputs().stream().limit(2)
                        .map(input -> new EmbeddingVectorResult(
                                input.referenceId(), new EmbeddingVector(List.of(1.0f, 2.0f))))
                        .toList());

        assertThatThrownBy(() -> useCase(repository, malformed, 3)
                .execute(VERSION, () -> {}, (current, total) -> {}))
                .isInstanceOf(EmbeddingFailureException.class)
                .extracting(failure -> ((EmbeddingFailureException) failure).reason())
                .isEqualTo(EmbeddingFailureException.Reason.INVALID_RESPONSE);
        assertThat(repository.durable).isEmpty();
    }

    @Test
    void providerMetadataMismatchFailsClosedWithoutPersistence() {
        List<EmbeddingModelMetadata> incompatible = List.of(
                new EmbeddingModelMetadata("other-provider", "model", "v1", 2),
                new EmbeddingModelMetadata("provider", "other-model", "v1", 2),
                new EmbeddingModelMetadata("provider", "model", "v1", 3));

        for (EmbeddingModelMetadata changed : incompatible) {
            FakeRepository repository = new FakeRepository(1);
            repository.generation = new IndexGeneration(
                    UUID.randomUUID(), MODEL, EmbedMaterialVersion.CHUNKING_VERSION,
                    IndexGeneration.Status.ACTIVE);
            assertThatThrownBy(() -> useCase(repository, request -> successful(request, changed), 1)
                    .execute(VERSION, () -> {}, (current, total) -> {}))
                    .isInstanceOf(EmbeddingFailureException.class);
            assertThat(repository.durable).isEmpty();
        }
    }

    @Test
    void ownershipLossBetweenBatchesStopsFurtherProviderWork() {
        FakeRepository repository = new FakeRepository(5);
        AtomicInteger providerCalls = new AtomicInteger();
        AtomicInteger ownershipChecks = new AtomicInteger();
        RuntimeException ownershipLost = new RuntimeException("ownership lost");

        assertThatThrownBy(() -> useCase(repository, request -> {
            providerCalls.incrementAndGet();
            return successful(request, MODEL);
        }, 3).execute(VERSION, () -> {
            if (ownershipChecks.incrementAndGet() == 3) throw ownershipLost;
        }, (current, total) -> {})).isSameAs(ownershipLost);

        assertThat(providerCalls).hasValue(1);
        assertThat(repository.durable).hasSize(3);
    }

    @Test
    void rejectsIncompatibleChunkingGenerationBeforeCallingProvider() {
        FakeRepository repository = new FakeRepository(1);
        repository.generation = new IndexGeneration(
                UUID.randomUUID(), MODEL, "OTHER_CHUNKER", IndexGeneration.Status.BUILDING);
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> useCase(repository, request -> {
            calls.incrementAndGet();
            return successful(request, MODEL);
        }, 1).execute(VERSION, () -> {}, (current, total) -> {}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("chunking");
        assertThat(calls).hasValue(0);
        assertThat(repository.durable).isEmpty();
    }

    private static EmbedMaterialVersion useCase(
            FakeRepository repository, EmbeddingPort provider, int batchSize) {
        return new EmbedMaterialVersion(
                repository, provider, new PersistEmbeddingBatch(repository), batchSize);
    }

    private static EmbeddingBatchResult successful(
            EmbeddingBatchRequest request, EmbeddingModelMetadata model) {
        return new EmbeddingBatchResult(
                model,
                new EmbeddingUsageMetadata(request.inputs().size()),
                request.inputs().stream()
                        .map(input -> new EmbeddingVectorResult(
                                input.referenceId(), vector(model.dimension())))
                        .toList());
    }

    private static EmbeddingVector vector(int dimension) {
        return new EmbeddingVector(java.util.stream.IntStream.range(0, dimension)
                .mapToObj(index -> (float) index).toList());
    }

    private record Progress(long current, long total) {}

    private static final class FakeRepository implements EmbeddingJobRepository {
        private final List<EmbeddableChunk> chunks;
        private final Map<UUID, ChunkEmbedding> durable = new LinkedHashMap<>();
        private IndexGeneration generation;

        private FakeRepository(int chunkCount) {
            chunks = java.util.stream.IntStream.rangeClosed(1, chunkCount)
                    .mapToObj(index -> new EmbeddableChunk(
                            new UUID(0, index), index, "canonical-" + index))
                    .toList();
        }

        @Override
        public Optional<IndexGeneration> findPreferredGeneration() {
            return Optional.ofNullable(generation);
        }

        @Override
        public long countEligibleChunks(UUID materialVersionId) {
            return chunks.size();
        }

        @Override
        public long countDurableEmbeddings(UUID materialVersionId, UUID indexGenerationId) {
            return durable.size();
        }

        @Override
        public List<EmbeddableChunk> findMissingChunks(
                UUID materialVersionId, UUID indexGenerationId, int batchSize) {
            return chunks.stream().filter(chunk -> !durable.containsKey(chunk.id()))
                    .limit(batchSize).toList();
        }

        @Override
        public IndexGeneration getOrCreateInitialGeneration(
                EmbeddingModelMetadata model, String chunkingVersion) {
            if (generation == null) {
                generation = new IndexGeneration(
                        UUID.randomUUID(), model, chunkingVersion, IndexGeneration.Status.BUILDING);
            } else if (!generation.isCompatibleWith(model, chunkingVersion)) {
                throw new IllegalStateException("generation mismatch");
            }
            return generation;
        }

        @Override
        public void persistBatch(
                UUID materialVersionId, UUID indexGenerationId, List<ChunkEmbedding> embeddings) {
            embeddings.forEach(embedding -> durable.putIfAbsent(embedding.chunkId(), embedding));
        }
    }
}
