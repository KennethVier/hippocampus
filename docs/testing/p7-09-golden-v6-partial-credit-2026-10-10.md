# P7-09 Golden v6 proportional partial credit — 2026-10-10

The product owner approved PARTIAL for Case 5: “Median nerve injury paralyzes
the wrist extensors” demonstrates the wrist-extensor paralysis component while
contradicting the essential radial-nerve identity. The median-nerve attribution
must be identified as a misconception, and feedback must correct it clearly.
CORRECT is forbidden. PARTIAL is not automatic for an answer that merely uses
an incorrect medical term; credit is confined to independent student knowledge.

Golden v6 derives from v5 and changes only the versioned Case 5 rubric. Golden
v5 continues to require INCORRECT for that case. The test runner still defaults
to v5 and production still uses `RESPONSE_EVALUATION_V6`. Explicit v6 selection
uses `HIPPOCAMPUS_LIVE_AI_GOLDEN_DATASET_VERSION=v6`; Gemini
`RESPONSE_EVALUATION_V8` remains explicitly selectable in the test runner.
Qualification identity binds the rubric version and v6 resource hash. Historical
reports and human reviews cannot be relabeled as v6 evidence.

Independent review still needs to resolve Case 3's possible overcrediting of
radial-nerve identification and Case 4's duplicated missing concepts. Neither
concern is adjudicated here. Complete version-bound ADR-0011 evidence, medical
semantic review, release acceptance, general review, and security review remain
outstanding; this decision grants no Golden PASS or tracker completion.
