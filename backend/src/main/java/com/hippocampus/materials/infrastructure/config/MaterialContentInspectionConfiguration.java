package com.hippocampus.materials.infrastructure.config;

import org.apache.tika.Tika;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import com.hippocampus.materials.infrastructure.inspection.TikaMaterialContentInspector;
import com.hippocampus.materials.port.MaterialContentInspector;

@AutoConfiguration
public class MaterialContentInspectionConfiguration {
    @Bean
    @ConditionalOnMissingBean
    MaterialContentInspector materialContentInspector() {
        return new TikaMaterialContentInspector(new Tika());
    }
}
