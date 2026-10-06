# Golden AI Dataset v3

Version 3 is based on v2 and is a narrow response-evaluation rubric overlay.
The historical v1 and v2 resources remain immutable. Explanation cases,
question-generation cases, and all v2 response-evaluation cases are inherited
unchanged. The overlay adds only two confirmed semantically equivalent
`requiredCorrectConceptGroups` alternatives:

- `P7-09-PARTIAL-001`: accepts an explicit wrist-extensor denervation statement
  whose causal clarification follows the anatomical relationship.
- `P7-09-UNCERTAIN-001`: accepts “muscles that extend or lift the wrist” as the
  demonstrated wrist-extensor concept.

The alternatives remain exact normalized phrases. They do not loosen the
matcher, change classification gates, permit supply alone as evidence of
denervation, or alter misconception and forbidden-output rules. Production
`RESPONSE_EVALUATION_V6` and production response-evaluation behavior are
unchanged.

The historical expected hashes remain unchanged. The integrity test
canonicalizes only CRLF/LF before SHA-256 so Windows and Unix checkouts verify
the same immutable v1/v2 Golden source content.
