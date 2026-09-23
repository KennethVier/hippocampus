# Hippocampus Agent Context & Bootstrap

## Purpose

This document gives a new coding/review agent enough durable context to work safely in Hippocampus without reading the entire repository or relying on conversational memory.

It is orientation, not implementation authority. When any statement here conflicts with the numbered Source of Truth, accepted ADRs, `docs/IMPLEMENTATION-TRACKER.md`, or root `AGENTS.md`, follow the repository authority order in `AGENTS.md`.

Do not treat this file as a cached progress tracker. Current task/phase state must always be resolved from `docs/IMPLEMENTATION-TRACKER.md` at the start of task work.

---

## 1. What Hippocampus Is

Hippocampus is a medical-learning platform whose primary user is a medical student.

Its core learning experience is **Study Missions**: structured, source-grounded learning sequences that help a student understand, retrieve, connect, apply, reflect, and later review knowledge.

Hippocampus is deliberately **not** primarily:

- upload-and-chat;
- PDF summarization;
- generic quiz generation;
- generic flashcard generation;
- an autonomous LLM tutor that owns educational state.

Permanent product principle:

> AI generates bounded educational content. Hippocampus decides learning state and sequencing.

The Learning Engine owns pedagogical sequencing. Persistent educational state belongs to the application/database, not to an LLM conversation.

---

## 2. Authority Model

Never implement from code alone when higher-authority documentation exists.

Authority order is:

1. `docs/00-*`–`docs/15-*` — product and educational authority
2. `docs/16-*`–`docs/25-*` — technical authority
3. `docs/26-development-roadmap-and-implementation-phases.md` — implementation order
4. `docs/27-decision-log-and-adr-index.md` — decision governance
5. accepted ADRs under `docs/adr/`
6. `docs/IMPLEMENTATION-TRACKER.md` — exact implementation task/status/evidence
7. `docs/design/DESIGN.md` — visual authority for approved UI work
8. `docs/design/references/` — layout/density/hierarchy intent only
9. code

Code never silently overrides higher authority.

When a significant unresolved decision appears, stop and follow Document 27 rather than inventing an architecture.

---

## 3. Current-State Protocol

Do **not** rely on a remembered task number, phase, branch, PR, or completion state from this document or from a previous agent conversation.

At the start of task work:

1. identify the exact requested tracker task;
2. read that task entry in `docs/IMPLEMENTATION-TRACKER.md`;
3. confirm dependency/task status and current phase boundary;
4. read only the authority documents the task actually requires;
5. check an accepted ADR only when it directly constrains the work;
6. inspect only the implementation files relevant to the task.

The tracker Markdown file is the operational progress source. Historical PRs/commits/CI runs are evidence, not competing status authorities.

Never pull a later tracker task forward just because its implementation would be convenient.

---

## 4. Core Domain/Product Boundaries

Keep these rules in working memory:

- Primary persona: medical student.
- Study Missions are core.
- Material is not Topic.
- Learning evidence must correspond to real student activity.
- Learning Engine owns deterministic sequencing/policy.
- AI output is bounded and untrusted.
- Provenance/source references must survive retrieval and generation.
- Authorization/ownership scope occurs before RAG retrieval ranking.
- Cross-user leakage tolerance is zero.
- PostgreSQL is authoritative persistence.
- pgvector is the vector retrieval foundation.
- Uploaded binaries belong in private object storage, not PostgreSQL blobs.
- Do not silently add product features because a model/tool makes them easy.

---

## 5. Architecture

### Backend

Spring Boot modular monolith.

Approved modules:

```text
identity
learning
progress
review
materials
rag
ai
shared
bootstrap
```

Feature internals where useful:

```text
api
application
domain
port
infrastructure
```

Dependency direction:

```text
api -> application -> domain/ports <- infrastructure
```

Domain code must not depend on Spring MVC, JPA repositories, provider SDKs, HTTP clients, or framework infrastructure.

### Java / Spring baseline

- Java 25
- Spring Boot 4.1.x according to Document 17
- Maven
- constructor injection
- cohesive application/use-case responsibilities
- composition over inheritance
- typed configuration properties for coherent configuration
- thin controllers
- authorization enforceable below transport
- transactions around application use cases
- no unnecessary DB transaction across network/AI calls
- no JPA entities exposed as API DTOs
- Flyway owns schema evolution
- production Hibernate does not auto-update schema
- provider DTOs stay inside provider adapters

Do not introduce technology merely because Spring supports it.

### Frontend

Approved baseline includes React, TypeScript, Vite and the frontend stack governed by Documents 17 and 20.

Product/security/domain behavior comes from higher authority. `docs/design/DESIGN.md` controls visual language when higher authority is silent. Reference screenshots communicate layout/density/hierarchy intent and do not create new product requirements.

Do not independently redesign Hippocampus unless the owning tracker task explicitly requires it.

---

## 6. Runtime AI / RAG Architecture

Development tooling and Hippocampus runtime AI are separate concerns.

Approved runtime providers are:

```text
remote Ollama API
Google Gemini API
```

Both exist behind application-owned provider abstraction:

```text
Typed AI Task
    ↓
AI Orchestrator
    ↓
Provider Router
   /          \
Ollama      Gemini
   \          /
Output validation
    ↓
Application decision
```

Permanent rules:

- application depends on internal AI contracts, not provider-specific behavior;
- provider/model may not own identity, authorization, grades, mastery, evidence truth, mission state, review scheduling, or deterministic sequencing;
- provider credentials remain server-side;
- provider-specific DTOs/SDK details remain inside adapters;
- model output is untrusted and must pass schema/grounding/safety/application validation;
- fallback may occur only when the alternate provider preserves the same task/grounding/evidence/output/safety contract;
- authorization/scope happens before retrieval/ranking;
- student/retrieved/uploaded content is untrusted and cannot override server-owned policy through prompt injection;
- source-grounded tasks preserve provenance/source references;
- runtime provider/model selection must follow the owning tracker task and AI evaluation authority; do not hard-code a provider preference merely because the development workflow uses Gemini.

Do not treat “we use Gemini/Antigravity/Jules to build Hippocampus” as permission to bypass the Provider Router or couple domain/application code directly to Gemini.

---

## 7. Security Baseline

Security is a design concern and a separate completion gate.

Primary verification baseline:

- OWASP ASVS 5.0.0
- OWASP Top 10:2025 threat lens
- OWASP API Security Top 10 threat lens

Important recurring concerns:

- server-side authentication/authorization;
- zero cross-user leakage;
- session/CSRF/CORS correctness;
- fail-closed ownership/resource access;
- upload/document parser/resource limits;
- path/command/SQL/XSS/URL/log/prompt injection;
- secrets/configuration exposure;
- provider error/credential leakage;
- RAG authorization before ranking;
- prompt injection and untrusted model output;
- dependency/supply-chain risk;
- exceptional-condition/failure behavior.

Critical/High security findings block completion. Medium findings normally block unless a human explicitly documents risk acceptance. If a material control cannot be verified, return `MANUAL SECURITY REVIEW REQUIRED`.

Never claim universal security or broad OWASP compliance from a scoped review.

---

## 8. Engineering Principles

Prioritize:

1. correctness;
2. security;
3. domain clarity;
4. cohesion;
5. maintainability;
6. simplicity.

Use SRP/SOLID as design guides, not mechanical fragmentation rules.

Prefer:

- one cohesive responsibility;
- explicit domain/use-case names;
- composition;
- existing project patterns;
- existing platform/dependency capability;
- minimal reviewable changes.

Avoid:

- speculative abstractions;
- generic `Service`/`Manager`/`Processor`/`Util`/`Helper` names when a domain/use-case name is available;
- unrelated refactors;
- future-task extension points;
- dependency proliferation;
- framework novelty without task pressure.

Do not introduce without an approved decision:

- microservices;
- CQRS;
- event sourcing;
- Redis;
- Kafka;
- Kubernetes;
- GraphQL;
- reactive/WebFlux architecture;
- JWT replacing approved session auth;
- a dedicated vector database replacing PostgreSQL + pgvector;
- undocumented external services;
- undocumented MVP features.

---

## 9. Agent Workflow

The workflow is role-based:

```text
PLAN
Human + Gemini
        ↓
approved implementation packet
        ↓
IMPLEMENT
Antigravity local OR Jules cloud
        ↓
IMPLEMENTED — USER VALIDATION REQUIRED
        ↓
VALIDATE
user / GitHub Actions
        ↓
REVIEW
independent implementation reviewer
        ↓
SECURITY
independent security review
        ↓
publication / merge / tracker completion evidence
```

### Plan

Resolve the task once and produce a compact implementation packet containing:

```text
TASK
GOAL
AUTHORITY
IMPLEMENT
DO NOT
EXPECTED FILES
RELEVANT SKILLS
AGENT TEST AUTHORIZATION
USER VALIDATION
STOP CONDITIONS
PUBLICATION
```

### Implement

Default local executor: Antigravity.

Optional cloud executor: Jules.

Use only one normal executor per task. The executor implements the approved packet directly and does not re-plan unless a concrete contradiction or blocker appears.

### Validate

Broad validation belongs to the user or CI. The executor runs only a cheap test/test method it created or modified, unless the user explicitly authorizes more.

### Review

Review actual diff + tracker/authority + tests + user/CI evidence. Do not trust the implementation report alone.

General review and security review are separate. The implementation context cannot approve itself.

### Complete

Code existing or tests passing is not `Done`. Final tracker completion requires all repository completion facts/evidence.

See `docs/agent-orchestration-workflow.md` for the full contract.

---

## 10. Command / Context Efficiency

Default implementation command policy is **run nothing**, except an eligible cheap test created/modified by the implementation.

Do not run broad builds, suites, Docker/Testcontainers, application startup, E2E, lint/typecheck, architecture suites, or Git ceremony unless explicitly authorized. Put required broader commands in `USER VALIDATION`.

Context policy:

- use targeted reads/searches;
- do not read all numbered docs;
- do not enumerate every skill;
- do not scan repository history/branches/PRs without a task reason;
- do not spawn subagents by default;
- do not reread established context;
- expand only for a concrete blocker, ambiguity, failure, architecture issue, or security requirement.

Single-agent execution is the default within each role.

---

## 11. Skill Map

Use repository skills only when relevant:

- `hippocampus-onboard-agent` — one-time orientation for a fresh agent/session
- `hippocampus-source-of-truth` — minimum authority resolution
- `hippocampus-plan-task` — optional in-repository planning
- `hippocampus-implement-task` — normal task execution
- `hippocampus-java-spring-engineering` — Java/domain detail
- `hippocampus-spring-boot-engineering` — Spring behavior
- `hippocampus-react-typescript-engineering` — frontend engineering
- `hippocampus-architecture-patterns` — non-trivial design pressure
- `hippocampus-testing-security` — specialized test/security-test design
- `hippocampus-review-implementation` — independent general review
- `hippocampus-security-vulnerability-review` — mandatory independent security gate

Do not load all skills “just in case.”

---

## 12. Fresh Antigravity Bootstrap Prompt

Use this once in a new Antigravity workspace/session when you want the agent oriented before giving it a tracker packet:

```text
You are working on Project Hippocampus.

First read root AGENTS.md and use /hippocampus-onboard-agent.
Do not modify code, run commands, change tracker state, commit, push, or create a PR during onboarding.

Your goal is only to establish the repository operating context:
- product purpose and hard boundaries;
- authority order and tracker-first workflow;
- architecture/module dependency rules;
- runtime AI/RAG/provider boundaries;
- implementation command policy;
- validation/review/security/completion gates;
- available skill routing;
- Antigravity’s role as the default local executor and Jules as the optional cloud executor.

Do not read every numbered document. Do not assume a current tracker task unless I provide one.
When finished, return only:
ONBOARDING READY
- authority understood
- architecture understood
- workflow understood
- security/completion gates understood
- waiting for exact tracker task or approved implementation packet
```

After that, normal task execution should use a much smaller prompt:

```text
Implement the approved Hippocampus packet below.
Use /hippocampus-implement-task.
Do not re-plan unless you find a concrete contradiction or blocker.

<APPROVED IMPLEMENTATION PACKET>
```

---

## 13. Fresh Jules Bootstrap / Task Prefix

Jules automatically reads root `AGENTS.md`, but use this prefix when assigning an approved Hippocampus task:

```text
This is a Project Hippocampus tracker implementation.

Before execution, follow:
- AGENTS.md
- .agents/skills/hippocampus-implement-task/SKILL.md
- the exact tracker task named in the approved packet below

The approved implementation packet is controlling.
Your Jules-generated plan may restate the packet for execution, but it must not expand scope, redesign architecture, introduce later tracker work, add unapproved dependencies/infrastructure, or change runtime AI/security/product decisions.

If repository reality contradicts the packet or a significant unresolved decision appears, stop and report the blocker instead of improvising.

Do not mark the task Done and do not self-approve the implementation.

<APPROVED IMPLEMENTATION PACKET>
```

---

## 14. What a New Agent Must Never Assume

Never assume:

- remembered tracker status is current;
- code is more authoritative than docs;
- a green test means task completion;
- a provider/model may own learning policy;
- Gemini usage in development means Gemini-specific runtime coupling is allowed;
- Jules/Antigravity planning can override an approved external packet;
- frontend hiding equals authorization;
- AI output is trusted;
- retrieved/uploaded text is trusted instruction;
- later tracker behavior may be implemented because it is nearby;
- a security risk may be silently accepted;
- a review may approve a superseded diff/PR head.

When uncertain, prefer the smallest safe action and escalate only the concrete unresolved point.
