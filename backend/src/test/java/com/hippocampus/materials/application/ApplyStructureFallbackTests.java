package com.hippocampus.materials.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.hippocampus.materials.domain.DetectedDocumentStructure;
import com.hippocampus.materials.domain.DetectedDocumentStructure.Node;
import com.hippocampus.materials.domain.DocumentNodeDetectionOrigin;
import com.hippocampus.materials.domain.DocumentNodeType;
import com.hippocampus.materials.domain.StructureFallbackPolicy;
import com.hippocampus.materials.domain.TextBlock;
import com.hippocampus.materials.domain.TextBlockExtractionMethod;
import com.hippocampus.materials.domain.TextBlockType;
import com.hippocampus.materials.port.DocumentStructureRepository;
import com.hippocampus.materials.port.StructureFallback;
import com.hippocampus.materials.port.StructureFallbackRequest;
import com.hippocampus.materials.port.StructureFallbackResponse;
import com.hippocampus.materials.port.StructureFallbackResponse.Heading;

class ApplyStructureFallbackTests {
    private static final UUID VERSION = UUID.randomUUID();
    private static final UUID ROOT = UUID.randomUUID();

    @Test
    void trustedAndAbsentHierarchyNeverInvokeFallbackOrReadContext() {
        var repository = mock(DocumentStructureRepository.class);
        var fallback = mock(StructureFallback.class);
        var application = new ApplyStructureFallback(repository, fallback);
        for (var structure : List.of(structure(), structure(node("HIGH")), structure(node("MEDIUM")))) {
            assertThat(application.execute(structure)).isSameAs(structure);
        }
        verifyNoInteractions(repository, fallback);
    }

    @Test
    void lowConfidenceUsesOnlyBoundedLocalPageTextAndConvertsValidRecovery() {
        var original = structure(node("LOW"));
        var result = new ApplyStructureFallback(repository(), request -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(request.headingPage()).isEqualTo(50);
            assertThat(request.ambiguousHeading()).isEqualTo("Ambiguous");
            assertThat(request.pages()).extracting(StructureFallbackRequest.PageText::pageNumber)
                    .containsExactly(49, 50, 51);
            assertThat(request.pages()).allSatisfy(page -> assertThat(page.text()).hasSize(2000));
            assertThat(request.contractVersion()).isEqualTo(StructureFallbackRequest.CONTRACT_VERSION);
            assertThat(request.promptVersion()).isEqualTo(StructureFallbackRequest.PROMPT_VERSION);
            return Optional.of(response(new Heading(DocumentNodeType.SECTION, "Recovered heading", 50)));
        }).execute(original);
        assertThat(result.nodes()).singleElement().satisfies(node -> {
            assertThat(node.detectionOrigin()).isEqualTo(DocumentNodeDetectionOrigin.AI_ASSISTED);
            assertThat(node.detectionConfidence()).isEqualTo("LOW");
            assertThat(node.nodeType()).isEqualTo(DocumentNodeType.SECTION);
            assertThat(node.title()).isEqualTo("Recovered heading");
            assertThat(node.startPage()).isEqualTo(50);
            assertThat(node.endPage()).isEqualTo(99);
        });
        assertThat(result.rootId()).isEqualTo(ROOT);
        assertThat(result.materialVersionId()).isEqualTo(VERSION);
    }

    @ParameterizedTest
    @MethodSource("invalidResponses")
    void rejectsMalformedVersionMismatchedUngroundedAndOutOfWindowResponses(StructureFallbackResponse response) {
        var original = structure(node("LOW"));
        assertThat(new ApplyStructureFallback(repository(), request -> Optional.of(response)).execute(original))
                .isSameAs(original);
    }

    static Stream<StructureFallbackResponse> invalidResponses() {
        var valid = new Heading(DocumentNodeType.SECTION, "Recovered heading", 50);
        return Stream.of(
                new StructureFallbackResponse("v0", StructureFallbackRequest.PROMPT_VERSION,
                        StructureFallbackRequest.SCHEMA_VERSION, valid),
                new StructureFallbackResponse(StructureFallbackRequest.CONTRACT_VERSION, "v0",
                        StructureFallbackRequest.SCHEMA_VERSION, valid),
                new StructureFallbackResponse(StructureFallbackRequest.CONTRACT_VERSION,
                        StructureFallbackRequest.PROMPT_VERSION, "v0", valid),
                new StructureFallbackResponse(null, null, null, valid), response(null),
                response(new Heading(null, "Recovered heading", 50)),
                response(new Heading(DocumentNodeType.DOCUMENT, "Recovered heading", 50)),
                response(new Heading(DocumentNodeType.SUBSECTION, "Recovered heading", 50)),
                response(new Heading(DocumentNodeType.SECTION, null, 50)),
                response(new Heading(DocumentNodeType.SECTION, "", 50)),
                response(new Heading(DocumentNodeType.SECTION, "invented", 50)),
                response(new Heading(DocumentNodeType.SECTION, "x".repeat(513), 50)),
                response(new Heading(DocumentNodeType.SECTION, "Recovered heading\n", 50)),
                response(new Heading(DocumentNodeType.SECTION, "Recovered heading", null)),
                response(new Heading(DocumentNodeType.SECTION, "Recovered heading", 49)),
                response(new Heading(DocumentNodeType.SECTION, "Recovered heading", 80)));
    }

    @Test
    void unavailableFailureConceptualTimeoutAndNullTransportPreserveDeterministicResult() {
        List<StructureFallback> fallbacks = List.of(request -> Optional.empty(), request -> null,
                request -> { throw new IllegalStateException("unavailable"); },
                request -> { throw new java.util.concurrent.CompletionException(new java.util.concurrent.TimeoutException()); });
        var original = structure(node("LOW"));
        for (var fallback : fallbacks) {
            assertThat(new ApplyStructureFallback(repository(), fallback).execute(original)).isSameAs(original);
        }
    }

    @Test
    void cannotChangeTrustedChildrenOrUnrelatedNodes() {
        var low = node("LOW");
        var child = new Node(0, DocumentNodeType.SECTION, "Trusted child", 1, 51, 99,
                DocumentNodeDetectionOrigin.NATIVE, "HIGH");
        var unrelated = new Node(null, DocumentNodeType.CHAPTER, "Other chapter", 2, 100, 100,
                DocumentNodeDetectionOrigin.HEURISTIC, "MEDIUM");
        var original = structure(low, child, unrelated);
        // A SECTION cannot replace a CHAPTER with a trusted SECTION child.
        assertThat(new ApplyStructureFallback(repository(), request -> Optional.of(response(
                new Heading(DocumentNodeType.SECTION, "Recovered heading", 50)))).execute(original)).isSameAs(original);
        var accepted = new ApplyStructureFallback(repository(), request -> Optional.of(response(
                new Heading(DocumentNodeType.CHAPTER, "Recovered heading", 50)))).execute(original);
        assertThat(accepted.nodes().get(1)).isSameAs(child);
        assertThat(accepted.nodes().get(2)).isSameAs(unrelated);
    }

    @Test
    void capsAttemptsAndRejectsCrossVersionContextBeforeCallingProvider() {
        var count = new AtomicInteger();
        var nodes = IntStream.range(0, 20).mapToObj(i -> node("LOW")).toArray(Node[]::new);
        new ApplyStructureFallback(repository(), request -> {
            count.incrementAndGet();
            return Optional.empty();
        }).execute(structure(nodes));
        assertThat(count).hasValue(StructureFallbackPolicy.MAX_REQUESTS);
        var repository = repository();
        when(repository.findTextBlocksByOrdinalRange(eq(VERSION), anyInt(), anyInt()))
                .thenReturn(List.of(block(UUID.randomUUID(), 49), block(VERSION, 50), block(VERSION, 51)));
        var fallback = mock(StructureFallback.class);
        var original = structure(node("LOW"));
        assertThat(new ApplyStructureFallback(repository, fallback).execute(original)).isSameAs(original);
        verifyNoInteractions(fallback);
    }

    @Test
    void rejectsTransactionalInvocationAndUnboundedRequest() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> new ApplyStructureFallback(repository(), request -> Optional.empty())
                    .execute(structure(node("LOW")))).isInstanceOf(IllegalStateException.class);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        assertThatThrownBy(() -> new StructureFallbackRequest.PageText(1, "x".repeat(2001)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StructureFallbackRequest(50, "Ambiguous",
                List.of(new StructureFallbackRequest.PageText(1, "text"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsRecoveryThatDuplicatesAnotherHeadingOnTheSamePage() {
        var trusted = new Node(null, DocumentNodeType.CHAPTER, "Recovered heading", 2, 50, 100,
                DocumentNodeDetectionOrigin.NATIVE, "HIGH");
        var original = structure(node("LOW"), trusted);
        assertThat(new ApplyStructureFallback(repository(), request -> Optional.of(response(
                new Heading(DocumentNodeType.CHAPTER, "Recovered heading", 50)))).execute(original))
                .isSameAs(original);
    }

    private static StructureFallbackResponse response(Heading heading) {
        return new StructureFallbackResponse(StructureFallbackRequest.CONTRACT_VERSION,
                StructureFallbackRequest.PROMPT_VERSION, StructureFallbackRequest.SCHEMA_VERSION, heading);
    }

    private static Node node(String confidence) {
        return new Node(null, DocumentNodeType.CHAPTER, "Ambiguous", 1, 50, 99,
                DocumentNodeDetectionOrigin.HEURISTIC, confidence);
    }

    private static DetectedDocumentStructure structure(Node... nodes) {
        return new DetectedDocumentStructure(VERSION, ROOT, 100, Arrays.asList(nodes));
    }

    private static DocumentStructureRepository repository() {
        var repository = mock(DocumentStructureRepository.class);
        when(repository.findTextBlocksByOrdinalRange(any(), anyInt(), anyInt())).thenAnswer(invocation -> {
            int first = invocation.getArgument(1);
            int last = invocation.getArgument(2);
            assertThat(last - first).isLessThan(3);
            return IntStream.rangeClosed(first, last).mapToObj(page -> block(VERSION, page)).toList();
        });
        return repository;
    }

    private static TextBlock block(UUID version, int page) {
        return new TextBlock(UUID.randomUUID(), version, ROOT, page, TextBlockType.PAGE_TEXT, page,
                "Recovered heading\n" + "body ".repeat(1000), TextBlockExtractionMethod.NATIVE, null, Instant.EPOCH);
    }
}
