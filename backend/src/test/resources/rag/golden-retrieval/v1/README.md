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

MRR is plain MRR over the full observed ranked list; it is not truncated at K.
The `mrr` member retained in each K metric object therefore mirrors the channel's
authoritative `plainMrr` value, while Recall and Precision are calculated at K=1/3/5.

### expectedSectionHitRate
The proportion of cases where at least one chunk from the expected section is retrieved in the top primaryK results.

### explicitIrrelevantContextRate
The proportion of top primaryK results that are explicitly marked as irrelevant for the case.

## Implementation Details
- **Primary K**: 3
- **K Values**: [1, 3, 5]
- **Channels**: Lexical, Vector, Hybrid.
- **Synthetic Vectors**: Deterministic 4D vectors passed directly to `VectorSearchRepository`; no synthetic EmbeddingPort exists.
- **Primary-K Metrics**: Expected-section hit rate and explicit irrelevant-context rate are evaluated at primaryK=3.

## Limitations
- **Visual Relevance**: NOT MEASURED in v1. No production visual retrieval search path exists in the current scope.

## Canonical Baseline Verification
The committed `baseline.json` is used as exact thresholds per v1 baseline-as-threshold policy:
- `evaluateAndVerify()` compares observed metrics against baseline for every case/channel
- Case IDs, K values, all metric types must match baseline exactly
- Only tiny floating-point representation tolerance applied
- A stale baseline that fails CI will cause the test to fail

## Validation Rules
All golden fixtures must satisfy:
1. Blank query rejection
2. Query vector dimension == active synthetic generation dimension (4)
3. Query vector finite/non-zero
4. `MaterialVersion` references an existing Material
5. `DocumentNode` references an existing MaterialVersion
6. Enough authorized candidate chunks for every case to evaluate max K=5

## Thresholds
Thresholds equal exact observed baseline values per v1 baseline-as-threshold policy. Any deviation causes CI failure.
