---
ADR: ADR-0009
Title: Explicit Application Level Selection
Status: ACCEPTED
Date: 2026-10-01
Decision Owners: Project Hippocampus Team
Categories: DOMAIN, BACKEND, AI, EDUCATION
Affected Documents: 11, 18, 27
---

# Context

The Learning Engine emits a `LearningDifficulty`, while the existing contextual
application AI contract requires an `ApplicationLevel`. Inferring that level in
the bootstrap adapter, prompt, provider adapter, or model would move a
pedagogical decision outside the deterministic Learning Engine.

# Decision

The learning domain owns `ApplicationActivityLevel` with these values:

```text
DIRECT
GUIDED
MECHANISM_TO_FINDING
SHORT_CASE
```

`LearningActionConstraints` carries nullable `applicationActivityLevel`. It is
present exactly for an AI-backed `APPLY` action and null for other actions.
Constraint transformations preserve it unless the resulting action is not an
AI-backed `APPLY`.

The initial v1 selection policy is deterministic:

```text
LearningDifficulty.FOUNDATIONAL -> ApplicationActivityLevel.DIRECT
LearningDifficulty.INTERMEDIATE -> ApplicationActivityLevel.GUIDED
LearningDifficulty.APPLIED      -> ApplicationActivityLevel.MECHANISM_TO_FINDING
```

`SHORT_CASE` remains representable but is not automatically selected. Current
learning evidence does not establish enough additional readiness information
to escalate every `APPLIED` action into a clinical short case.

AI integration maps the learning-owned value exactly to the identically named
AI-domain `ApplicationLevel`. The adapter does not randomize, infer from the
subject or generated text, default a missing value, or delegate selection to a
provider/model.

Validated output difficulty is derived from the Learning Engine request:

```text
LearningDifficulty.FOUNDATIONAL -> ApplicationDifficulty.FOUNDATIONAL_APPLIED
LearningDifficulty.INTERMEDIATE -> ApplicationDifficulty.INTERMEDIATE_APPLIED
LearningDifficulty.APPLIED      -> ApplicationDifficulty.INTERMEDIATE_APPLIED
```

An incompatible generated difficulty fails closed.

# Rationale

Explicit learning-domain ownership keeps application complexity deterministic,
testable, provider-neutral, and aligned with learner readiness.

# Consequences

Learning policies that create AI-backed `APPLY` actions must select an
application level. AI integration must carry the exact mapped level into the
canonical contextual-application request and reject incompatible output
difficulty.

# Persistence Impact

No migration or persistent column is required. The field is a transient
Learning Engine/application contract, and the complete generated contextual
application remains in the generated artifact payload.

# Security & Privacy Impact

No provider, retrieval, authorization, or clinical decision-support authority
is added. Provider output remains untrusted. Contextual applications remain
educational and must not provide patient-specific diagnosis or treatment advice
or imply clinical competence.

# Educational/Product Impact

Application remains gated by the existing `ApplicationPolicy`. The initial
policy provides direct, guided, or mechanism-to-finding scaffolding without
automatically escalating to short cases or structured case reasoning.

# Testing Impact

Validation covers deterministic level selection, exact learning-to-AI mapping,
constraint invariants and transformations, contextual-application routing,
complete payload preservation, and fail-closed output validation. Medical
plausibility and learner appropriateness remain part of the semantic evaluation
gate.

# Documentation Changes Required

Documents 11, 18, and 27 are aligned with this accepted decision.

# Approval

**Status:** ACCEPTED

**Approved By:** Project Hippocampus Team
