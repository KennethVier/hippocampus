package com.hippocampus.materials.infrastructure.config;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

import com.hippocampus.materials.application.DetectDocumentStructure;
import com.hippocampus.materials.application.PersistDetectedDocumentStructure;
import com.hippocampus.materials.application.ProcessingStageHandler;
import com.hippocampus.materials.application.StructureDetectStageHandler;
import com.hippocampus.materials.domain.DeterministicDocumentStructureDetector;
import com.hippocampus.materials.port.DetectedDocumentStructurePersistence;
import com.hippocampus.materials.port.DocumentStructureRepository;
import com.hippocampus.materials.port.PdfExtractionSourceRepository;
import com.hippocampus.materials.port.PdfStructureInspector;

class DetectedDocumentStructureConfigurationTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DetectedDocumentStructureConfiguration.class));

    @Test
    void registersExactlyOneRealStructureDetectHandlerWhenAllBoundariesExist() {
        runner.withUserConfiguration(RequiredBeans.class).run(context -> {
            assertThat(context).hasNotFailed()
                    .hasSingleBean(DetectedDocumentStructurePersistence.class)
                    .hasSingleBean(PersistDetectedDocumentStructure.class)
                    .hasSingleBean(DeterministicDocumentStructureDetector.class)
                    .hasSingleBean(DetectDocumentStructure.class)
                    .hasSingleBean(ProcessingStageHandler.class);
            assertThat(context.getBean(ProcessingStageHandler.class)).isInstanceOf(StructureDetectStageHandler.class);
            assertThat(context.getBean(com.hippocampus.materials.port.StructureFallback.class))
                    .isInstanceOf(UnavailableStructureFallback.class);
            assertThat(context.getBean(com.hippocampus.materials.port.StructureFallback.class).detect(
                    new com.hippocampus.materials.port.StructureFallbackRequest(1, "text", List.of(
                            new com.hippocampus.materials.port.StructureFallbackRequest.PageText(1, "text")))))
                    .isEmpty();
        });
    }

    @Test
    void doesNotRegisterPartialCapabilityWhenInspectorIsMissing() {
        runner.withUserConfiguration(MissingInspectorBeans.class).run(context -> assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean(ProcessingStageHandler.class)
                .doesNotHaveBean(DetectedDocumentStructurePersistence.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class MissingInspectorBeans {
        @Bean
        JdbcClient jdbcClient() {
            return mock(JdbcClient.class);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return mock(PlatformTransactionManager.class);
        }

        @Bean
        PdfExtractionSourceRepository sources() {
            return mock(PdfExtractionSourceRepository.class);
        }

        @Bean
        DocumentStructureRepository structures() {
            return mock(DocumentStructureRepository.class);
        }

        @Bean
        PdfStructureInspectionProperties properties() {
            return new PdfStructureInspectionProperties(10, 3, 100, 20, 50, 25);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class RequiredBeans extends MissingInspectorBeans {
        @Bean
        PdfStructureInspector inspector() {
            return mock(PdfStructureInspector.class);
        }
    }
}
