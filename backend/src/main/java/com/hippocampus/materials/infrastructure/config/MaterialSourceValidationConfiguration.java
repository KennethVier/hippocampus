package com.hippocampus.materials.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

import com.hippocampus.materials.application.MaterialValidateStageHandler;
import com.hippocampus.materials.application.ProcessingStageHandler;
import com.hippocampus.materials.infrastructure.persistence.PersistentMaterialSourceValidator;
import com.hippocampus.materials.infrastructure.persistence.SpringDataMaterialRepository;
import com.hippocampus.materials.infrastructure.persistence.SpringDataMaterialVersionRepository;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.port.MaterialSourceValidator;
import com.hippocampus.materials.port.PdfSourceInspector;

@AutoConfiguration(afterName = {
        "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"
})
@ConditionalOnBean({
        SpringDataMaterialRepository.class,
        SpringDataMaterialVersionRepository.class,
        BinaryObjectStore.class,
        PdfSourceInspector.class
})
public class MaterialSourceValidationConfiguration {

    @Bean
    MaterialSourceValidator materialSourceValidator(
            SpringDataMaterialRepository materials,
            SpringDataMaterialVersionRepository versions,
            PdfSourceInspector pdfSourceInspector) {
        return new PersistentMaterialSourceValidator(materials, versions, pdfSourceInspector);
    }

    @Bean
    @ConditionalOnBean(MaterialSourceValidator.class)
    ProcessingStageHandler materialValidateStageHandler(MaterialSourceValidator sourceValidator) {
        return new MaterialValidateStageHandler(sourceValidator);
    }
}
