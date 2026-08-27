---
Document ID: README
Title: Hippocampus Documentation Guide
Version: 1.1.0
Status: Final
Owner: Project Hippocampus Team
Created: 2026-08-23
Last Updated: 2026-08-24
Purpose: Define the reading order, authority hierarchy, document index, governance rules, and implementation handoff for the complete Hippocampus v1 Source of Truth.
---

# Project Hippocampus Documentation

> **Documentation is the first implementation.**

This directory contains the approved **Hippocampus v1.0 Source of Truth**.

The documentation defines the product, educational philosophy, MVP boundary, AI/RAG architecture, data model, backend/frontend architecture, ingestion, security, deployment, operations, testing, implementation roadmap, and decision governance.

# Documentation Principles

1. Every document answers one primary question.
2. Every important decision must be traceable.
3. Research and educational evidence precede feature expansion.
4. Every feature must solve a documented learner problem.
5. Every document should make downstream implementation easier to reason about.
6. Code must implement the Source of Truth rather than silently redefine it.
7. Significant post-freeze architectural changes require the ADR process in Document 27.

# Authority Hierarchy

```text
00–15
Product + Educational Authority
        ↓
16–25
Technical Authority
        ↓
26
Implementation Order
        ↓
27
Decision Governance
        ↓
Accepted ADRs
        ↓
Code
```

If documents appear inconsistent, resolve the conflict according to this authority hierarchy, document version, and accepted ADRs. Do not choose whichever interpretation is easiest to implement.

# Reading Order

## Product & Educational Foundation

- **00 – Project Vision**
- **01 – Guiding Principles**
- **02 – Problem Statement**
- **03 – Educational Foundation**
- **04 – Product Requirements**
- **05 – User Personas**
- **06 – User Journey & Learning Flow**
- **07 – Feature Specifications**
- **08 – Non-Functional Requirements**
- **09 – MVP Scope & Roadmap**

## AI & Knowledge Architecture

- **10 – AI Architecture**
- **11 – AI Learning Engine**
- **12 – Prompt Engineering Strategy**
- **13 – RAG Architecture**
- **14 – Knowledge Base Design**
- **15 – AI Evaluation Strategy**

## Technical Architecture

- **16 – System Architecture**
- **17 – Technology Stack & ADR Baseline**
- **18 – Domain Model & Database Design**
- **19 – Backend Architecture**
- **20 – Frontend Architecture**
- **21 – File Processing & Ingestion Architecture**
- **22 – Security & Privacy Architecture**
- **23 – Deployment & Infrastructure**
- **24 – Observability & Operations**
- **25 – Testing Strategy**

## Implementation & Governance

- **26 – Development Roadmap & Implementation Phases**
- **27 – Decision Log / ADR Index**
- **adr/README.md – ADR usage guide**
- **IMPLEMENTATION-TRACKER.md – live implementation status and completion evidence**

# Locked v1 Product Boundary

Hippocampus v1 is a student-centered medical learning application that transforms supported learning materials into guided **Study Missions** where students understand concepts, actively retrieve knowledge, connect related ideas, apply knowledge through appropriately scaffolded medical scenarios, receive formative feedback, build learning evidence, and revisit weak knowledge over time.

The MVP is not defined as an upload-and-chat application, PDF summarizer, Anki replacement, or quiz generator.

# Locked v1 AI Boundary

Hippocampus uses a provider-abstracted backend AI layer with two external API providers:

```text
Ollama API
Google Gemini API
```

Neither provider owns educational state or authorization.

The Learning Engine, grounding rules, RAG scope, evidence, review, and persistent learning state remain application-owned.

# Locked v1 Deployment Boundary

The initial controlled pilot follows the **PILOT-FREE** profile defined in Document 23:

```text
Vercel
Render
Neon PostgreSQL + pgvector
Cloudflare R2
Gemini API
Ollama API
```

Free tiers are a validation strategy, not a long-term production reliability guarantee.

# Implementation Rule

Before implementing any phase:

```text
Read authoritative documents
↓
Identify phase scope
↓
Plan
↓
Implement
↓
Test
↓
Review against Source of Truth
↓
Update tracker
↓
Commit
```

# Change Governance

After the v1 freeze:

- Ordinary implementation choices may proceed within approved boundaries.
- Significant architecture changes require an ADR.
- Product/MVP or educational-policy changes require the relevant Source-of-Truth documents to be reviewed and patched; an ADR alone cannot silently override them.
- All accepted ADRs that change existing decisions must identify and patch affected documents.

# Operational Implementation Handoff

Operational implementation status and completion evidence are maintained in `docs/IMPLEMENTATION-TRACKER.md`, below the frozen Source-of-Truth authority. `docs/Hippocampus-v1-Implementation-Tracker.xlsx` is a frozen planning/export/reference artifact and is not the live status source.

# Status

**Hippocampus v1.0 Source of Truth — Final after consistency audit on 2026-08-24.**
