# Golden AI dataset v7

Golden v7 resolves the v2 base cases, v4 rubric alternatives, v5 response
input contract, and v6 Case 5 rubric, then replaces only Case 3's resolved
rubric through `response-evaluation-rubric-overrides.json`. It retains the
exact nine case inputs, authorized synthetic sources, and the v6 Case 5
PARTIAL-credit standard. Case 3 credits independently stated wrist-extensor
denervation without borrowing radial-nerve identification from its question;
the missing nerve relationship and wrist-drop mechanism require corrective
feedback. No historical resource or report is rewritten.

Golden **dataset** v7 is separate from any `RESPONSE_EVALUATION_V7` prompt.
The legacy live runner remains a single-pass runner and cannot qualify the
current two-pass production adapter. An identity-bound current-adapter live
run and independent semantic review remain separate requirements.
