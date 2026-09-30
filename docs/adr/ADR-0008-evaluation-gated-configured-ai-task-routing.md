---
ADR: ADR-0008
Title: Evaluation-Gated Configured AI Task Routing
Status: ACCEPTED
Date: 2026-09-30
Decision Owners: Project Hippocampus Team
Categories: ARCHITECTURE, BACKEND, AI, OPERATIONS
Affected Documents: 10, 15, 17, 27
---

# Context

The Provider Router already selects only eligible provider/model candidates,
but P7 activity execution had no authoritative production source for task
budgets, routing preferences, or candidates. Hard-coding model identifiers or a
universal provider preference would bypass evaluation governance and make a
deployment choice a permanent architecture constant.

# Decision

Concrete AI chat model identifiers and routing candidates are deployment/runtime
configuration. Runtime execution configuration is task-specific for
`EXPLANATION`, `QUESTION_GENERATION`, `RESPONSE_EVALUATION`,
`CONCEPT_CONNECTION`, and `CONTEXTUAL_APPLICATION`. Each task
supplies a prompt token budget, a `ProviderRoutingPreference`, and one or more
existing `ProviderRoutingCandidate` values.

Each candidate explicitly supplies provider/model identity, supported tasks,
evaluation-approved tasks, current availability, quota availability, rate-limit
availability, cost rank, latency rank, and routing priority. Configuration does
not imply evaluation approval. A candidate is eligible only when the existing
Provider Router confirms all task, evaluation, and operational availability
conditions.

Runtime configuration has this shape:

```yaml
hippocampus:
  ai:
    task-execution:
      tasks:
        EXPLANATION:
          max-context-tokens: <required>
          reserved-output-tokens: <required>
          routing-preference: <required>
          candidates:
            - provider-id: <required>
              model-id: <required deployment value>
              supported-tasks: [<required>]
              evaluation-approved-tasks: [<explicitly approved tasks>]
              available: <required>
              quota-available: <required>
              rate-limit-available: <required>
              cost-rank: <required>
              latency-rank: <required>
              routing-priority: <required>
```

The other required tasks use the same task-specific shape. No model identifier,
routing preference, provider priority, or token budget receives an application
default.

When AI runtime is enabled, missing/incomplete task configuration, absence of
an eligible evaluation-approved route, or an eligible route referencing an
unavailable provider adapter fails configuration closed. A single eligible
candidate is valid and has no cross-provider fallback. With multiple eligible
candidates, the existing Provider Router selects the primary and at most one
alternate from a different provider.

Provider adapter activation and credentials remain owned by
`hippocampus.ai.providers.gemini` and
`hippocampus.ai.providers.ollama-cloud`. Provider transport retry and transient
execution failure handling remain owned by `AiRequestManager`.

For v1 prompt preflight budgeting, the provider-neutral estimator is the UTF-8
byte length of rendered text. It is not authoritative provider token usage and
must not replace provider-reported usage in diagnostics or evaluation.

# Rationale

Task-specific configuration preserves provider neutrality while allowing
evaluated models, budgets, and routing priorities to change by deployment. The
existing router remains the single selection algorithm and its evaluation gate
remains fail closed.

# Consequences

- Runtime deployments must configure all five implemented P7 task policies explicitly.
- A configured model is not routable until its task is explicitly
  evaluation-approved and all availability flags permit routing.
- Model and routing changes are configuration and evaluation changes, not
  Learning Engine decisions.
- Request priorities remain latency/queue semantics and do not select models.
- Actual provider usage remains authoritative for diagnostics.

# Security & Privacy Impact

Credentials remain server-side in existing provider configuration. No
unapproved or operationally unavailable candidate may route. Grounding,
EvidencePackage, prompt/output contracts, validation, safety behavior, and
source authorization remain unchanged across fallback.

# Approval

**Status:** ACCEPTED

**Approved By:** Project Hippocampus Team
