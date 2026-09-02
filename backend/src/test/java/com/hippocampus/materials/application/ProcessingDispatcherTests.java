package com.hippocampus.materials.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import com.hippocampus.materials.domain.ClaimedProcessingJob;
import com.hippocampus.materials.domain.ProcessingJobType;

class ProcessingDispatcherTests {
    private static final UUID MATERIAL_VERSION_ID = UUID.randomUUID();

    @ParameterizedTest
    @MethodSource("routes")
    void dispatchesExactlyOneHandlerAndReturnsCentralNextStage(
            ProcessingJobType stage,
            ProcessingJobType nextStage) {
        List<ProcessingStageHandler> handlers = handlersForSupportedStages();
        ProcessingDispatcher dispatcher = new ProcessingDispatcher(handlers);
        ClaimedProcessingJob job = job(stage, MATERIAL_VERSION_ID);

        assertThat(dispatcher.dispatch(job)).isEqualTo(new ProcessingStageResult(stage, nextStage));

        for (ProcessingStageHandler handler : handlers) {
            verify(handler, handler.jobType() == stage ? org.mockito.Mockito.times(1) : never()).handle(job);
        }
    }

    @ParameterizedTest
    @EnumSource(value = ProcessingJobType.class, names = {
            "EMBED", "INDEX", "ACTIVATE", "REINDEX", "CLEANUP"
    })
    void rejectsUnsupportedStagesWithoutInvokingHandlers(ProcessingJobType stage) {
        ProcessingStageHandler handler = handler(ProcessingJobType.MATERIAL_VALIDATE);
        ProcessingDispatcher dispatcher = new ProcessingDispatcher(List.of(handler));

        assertThatThrownBy(() -> dispatcher.dispatch(job(stage, null)))
                .isInstanceOf(UnsupportedProcessingStageException.class);
        verify(handler, never()).handle(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsMissingHandler() {
        ProcessingDispatcher dispatcher = new ProcessingDispatcher(List.of());

        assertThatThrownBy(() -> dispatcher.dispatch(job(ProcessingJobType.MATERIAL_EXTRACT, MATERIAL_VERSION_ID)))
                .isInstanceOf(MissingProcessingStageHandlerException.class);
    }

    @Test
    void rejectsDuplicateHandlerRoutes() {
        ProcessingStageHandler first = handler(ProcessingJobType.MATERIAL_EXTRACT);
        ProcessingStageHandler second = handler(ProcessingJobType.MATERIAL_EXTRACT);

        assertThatThrownBy(() -> new ProcessingDispatcher(List.of(first, second)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    void rejectsHandlerAdvertisingUnsupportedOrNullRoute() {
        ProcessingStageHandler unsupported = handler(ProcessingJobType.EMBED);
        ProcessingStageHandler nullRoute = handler(null);

        assertThatThrownBy(() -> new ProcessingDispatcher(List.of(unsupported)))
                .isInstanceOf(UnsupportedProcessingStageException.class);
        assertThatThrownBy(() -> new ProcessingDispatcher(List.of(nullRoute)))
                .isInstanceOf(UnsupportedProcessingStageException.class);
    }

    @Test
    void rejectsNullHandlerAndNullJob() {
        assertThatThrownBy(() -> new ProcessingDispatcher(Arrays.asList((ProcessingStageHandler) null)))
                .isInstanceOf(IllegalArgumentException.class);

        ProcessingStageHandler handler = handler(ProcessingJobType.MATERIAL_VALIDATE);
        ProcessingDispatcher dispatcher = new ProcessingDispatcher(List.of(handler));
        assertThatThrownBy(() -> dispatcher.dispatch(null)).isInstanceOf(NullPointerException.class);
        verify(handler, never()).handle(org.mockito.ArgumentMatchers.any());
    }

    @ParameterizedTest
    @MethodSource("supportedStages")
    void rejectsNullMaterialVersionBeforeHandlerInvocation(ProcessingJobType stage) {
        ProcessingStageHandler handler = handler(stage);
        ProcessingDispatcher dispatcher = new ProcessingDispatcher(List.of(handler));

        assertThatThrownBy(() -> dispatcher.dispatch(job(stage, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Material version");
        verify(handler, never()).handle(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void propagatesHandlerFailureAndPassesProcessingVersionThroughUnchanged() {
        ProcessingStageHandler handler = handler(ProcessingJobType.MATERIAL_VALIDATE);
        RuntimeException failure = new RuntimeException("stage failed");
        ClaimedProcessingJob job = new ClaimedProcessingJob(
                UUID.randomUUID(), ProcessingJobType.MATERIAL_VALIDATE, MATERIAL_VERSION_ID, "processor-v17");
        org.mockito.Mockito.doThrow(failure).when(handler).handle(job);

        assertThatThrownBy(() -> new ProcessingDispatcher(List.of(handler)).dispatch(job)).isSameAs(failure);
        verify(handler).handle(job);
    }

    @Test
    void doesNotRecursivelyInvokeTheNextHandler() {
        ProcessingStageHandler chunk = handler(ProcessingJobType.CHUNK);
        ProcessingStageHandler embed = mock(ProcessingStageHandler.class);
        when(embed.jobType()).thenReturn(ProcessingJobType.EMBED);
        ProcessingDispatcher dispatcher = new ProcessingDispatcher(List.of(chunk));

        assertThat(dispatcher.dispatch(job(ProcessingJobType.CHUNK, MATERIAL_VERSION_ID)).nextStage())
                .isEqualTo(ProcessingJobType.EMBED);
        verify(chunk).handle(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(embed);
    }

    private static List<ProcessingStageHandler> handlersForSupportedStages() {
        return supportedStages().map(ProcessingDispatcherTests::handler).toList();
    }

    private static ProcessingStageHandler handler(ProcessingJobType stage) {
        ProcessingStageHandler handler = mock(ProcessingStageHandler.class);
        when(handler.jobType()).thenReturn(stage);
        return handler;
    }

    private static ClaimedProcessingJob job(ProcessingJobType stage, UUID materialVersionId) {
        return new ClaimedProcessingJob(UUID.randomUUID(), stage, materialVersionId, "processor-v1");
    }

    private static Stream<ProcessingJobType> supportedStages() {
        return Stream.of(
                ProcessingJobType.MATERIAL_VALIDATE,
                ProcessingJobType.MATERIAL_EXTRACT,
                ProcessingJobType.STRUCTURE_DETECT,
                ProcessingJobType.VISUAL_EXTRACT,
                ProcessingJobType.NORMALIZE,
                ProcessingJobType.CHUNK);
    }

    private static Stream<Arguments> routes() {
        return Stream.of(
                Arguments.of(ProcessingJobType.MATERIAL_VALIDATE, ProcessingJobType.MATERIAL_EXTRACT),
                Arguments.of(ProcessingJobType.MATERIAL_EXTRACT, ProcessingJobType.STRUCTURE_DETECT),
                Arguments.of(ProcessingJobType.STRUCTURE_DETECT, ProcessingJobType.VISUAL_EXTRACT),
                Arguments.of(ProcessingJobType.VISUAL_EXTRACT, ProcessingJobType.NORMALIZE),
                Arguments.of(ProcessingJobType.NORMALIZE, ProcessingJobType.CHUNK),
                Arguments.of(ProcessingJobType.CHUNK, ProcessingJobType.EMBED));
    }
}
