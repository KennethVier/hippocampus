# Golden AI Dataset v4

Version 4 is based on the immutable v2 source fixtures and supersedes the v3
qualification overlay without modifying historical v1-v3 resources.

It preserves the two v3 response-evaluation alternatives and adds only the
narrow P7-09 behavior confirmed by the Gemini live qualification run:

- `P7-09-PARTIAL-001` accepts the model's canonicalized correct-concept wording
  `radial nerve innervates the wrist extensors` for the fixture whose learner
  explicitly stated wrist-extensor denervation.
- The same case accepts the observed guided-retrieval feedback question
  `what happens to the movement of the wrist when these extensors are denervated`
  as identifying the missing wrist-extension consequence.

These are case-local exact normalized alternatives. The matcher is unchanged.
The regression suite still rejects the generic `radial nerve supplies the wrist
extensors` wording as sufficient evidence for this denervation fixture, so the
overlay does not globally turn nerve supply into demonstrated denervation.

Production `RESPONSE_EVALUATION_V6`, structured-output validation, repair
behavior, classification gates, misconception rules, and forbidden-output rules
are unchanged.
