# Golden Retrieval Dataset v1

This dataset provides a deterministic, offline baseline for measuring the quality of the RAG retrieval pipeline.

## Metrics Definitions

### Recall@K
The proportion of expected chunks retrieved in the top K results.
$$\text{Recall@K} = \frac{|\text{Top-K} \cap \text{Expected}|}{|\text{Expected}|}$$

### Precision@K
The proportion of top-K results that are either expected or acceptable alternatives.
$$\text{Precision@K} = \frac{|\text{Top-K} \cap (\text{Expected} \cup \text{Acceptable})|}{K}$$

### MRR (Mean Reciprocal Rank)
The reciprocal rank of the first expected chunk found in the results.
$$\text{MRR} = \frac{1}{\text{Rank of first expected chunk}}$$

### expectedSectionHitRate
The proportion of cases where at least one chunk from the expected section is retrieved in the top primaryK results.

### explicitIrrelevantContextRate
The proportion of top primaryK results that are explicitly marked as irrelevant for the case.

## Implementation Details
- **Primary K**: 3
- **K Values**: [1, 3, 5]
- **Channels**: Lexical, Vector, Hybrid.
- **Synthetic Vectors**: Uses a fixed embedding dimension of 4.

## Limitations
- **Visual Relevance**: NOT MEASURED in v1. No production visual retrieval search path exists in the current scope.

## Thresholds
Thresholds are static regression floors. Any relaxation requires explicit reviewed evidence.
