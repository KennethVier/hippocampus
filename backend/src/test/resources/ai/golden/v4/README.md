# Golden AI Dataset v4

Version 4 is based on the immutable v2 source fixtures and supersedes the v3
qualification overlay without modifying historical v1-v3 resources.

It preserves the v3 response-evaluation alternatives and adds only the narrow
P7-09 wording confirmed by two Gemini live qualification attempts:

- `P7-09-PARTIAL-001` accepts the production aggregation wording
  `radial nerve innervates the wrist extensors` and
  `radial nerve supplies the wrist extensors` for this fixed fixture, whose
  learner response explicitly states wrist-extensor denervation in answer to
  the radial-nerve injury question.
- The same case accepts the observed guided-retrieval feedback question
  `what happens to the movement of the wrist when these extensors are denervated`
  as identifying the missing wrist-extension consequence.
- `P7-09-UNCERTAIN-001` accepts the observed parenthetical synonym wording
  `a nerve supplies the muscles that lift (extend) the wrist`.

These are case-local exact normalized alternatives. The matcher is unchanged.
The regression suite still rejects a bare nerve/stem reference as sufficient
evidence for the partial denervation fixture; the overlay does not globally
award nerve identity or nerve supply as demonstrated knowledge.

The repeated `P7-09-WRONG-REASONING-001` malformed-output failure is addressed
separately by constraining Gemini `RESPONSE_EVALUATION_V4/V5/V6` generations to
the atomic response-evaluation schema. Legacy response-evaluation prompt
versions and bounded repair semantics remain unchanged.
