package com.hippocampus.rag.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Validated
@ConfigurationProperties("hippocampus.rag.inspector")
public class RetrievalInspectorProperties {
    private boolean enabled;
    @Min(1) @Max(100) private int lexicalLimit = 20;
    @Min(1) @Max(100) private int vectorLimit = 20;
    @Min(1) @Max(100) private int hybridLimit = 20;
    @Min(1) @Max(1000) private int maxQueryLength = 1000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getLexicalLimit() { return lexicalLimit; }
    public void setLexicalLimit(int lexicalLimit) { this.lexicalLimit = lexicalLimit; }
    public int getVectorLimit() { return vectorLimit; }
    public void setVectorLimit(int vectorLimit) { this.vectorLimit = vectorLimit; }
    public int getHybridLimit() { return hybridLimit; }
    public void setHybridLimit(int hybridLimit) { this.hybridLimit = hybridLimit; }
    public int getMaxQueryLength() { return maxQueryLength; }
    public void setMaxQueryLength(int maxQueryLength) { this.maxQueryLength = maxQueryLength; }
}
