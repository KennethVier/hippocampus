package com.hippocampus.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hippocampus.learning.ai.evidence-retrieval")
public final class ActivityEvidenceRetrievalProperties {

    private Integer lexicalLimit;
    private Integer vectorLimit;
    private Integer hybridLimit;
    private Integer maxQueryLength;
    private Integer maxChunks;
    private Integer maxVisuals;

    public Integer getLexicalLimit() { return lexicalLimit; }
    public void setLexicalLimit(Integer value) { this.lexicalLimit = value; }
    public Integer getVectorLimit() { return vectorLimit; }
    public void setVectorLimit(Integer value) { this.vectorLimit = value; }
    public Integer getHybridLimit() { return hybridLimit; }
    public void setHybridLimit(Integer value) { this.hybridLimit = value; }
    public Integer getMaxQueryLength() { return maxQueryLength; }
    public void setMaxQueryLength(Integer value) { this.maxQueryLength = value; }
    public Integer getMaxChunks() { return maxChunks; }
    public void setMaxChunks(Integer value) { this.maxChunks = value; }
    public Integer getMaxVisuals() { return maxVisuals; }
    public void setMaxVisuals(Integer value) { this.maxVisuals = value; }

    ActivityEvidenceRetrievalOptions toOptions() {
        if (lexicalLimit == null || vectorLimit == null || hybridLimit == null
                || maxQueryLength == null || maxChunks == null || maxVisuals == null) {
            throw new IllegalStateException(
                    "hippocampus.learning.ai.evidence-retrieval configuration is incomplete");
        }
        return new ActivityEvidenceRetrievalOptions(
                lexicalLimit, vectorLimit, hybridLimit, maxQueryLength, maxChunks, maxVisuals);
    }
}
