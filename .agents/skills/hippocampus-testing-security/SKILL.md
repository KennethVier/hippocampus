---
name: hippocampus-testing-security
description: Use when designing, implementing, or reviewing tests, CI quality gates, vulnerability checks, authorization, RAG isolation, file-ingestion security, provider failure handling, or release readiness for Hippocampus.
---

# Testing & Security Workflow

## Principle

Use the least expensive test that proves the risk, but never skip a required security or cross-layer test.

## Layers

### Pure Unit
Learning Engine, Review Policy, Evidence Projector, state transitions, normalization/ranking utilities.

### Application
Use fake ports for use-case orchestration and deterministic policies.

### PostgreSQL Integration
Use Testcontainers for Flyway, constraints, pgvector, FTS/pg_trgm, locking, job claiming, and transactions.

### API / Security Integration
Authentication, ownership/IDOR, CSRF, CORS, stable errors, upload limits, session behavior.

### Frontend / E2E
Test student-visible behavior rather than implementation internals.

## Mandatory Security Themes

When relevant:
- cross-user access;
- RAG scope before ranking;
- source-reference forgery;
- prompt injection;
- XSS/generated Markdown;
- malicious upload/path traversal;
- secret leakage;
- provider keys server-only;
- rate limiting;
- deleted/inactive material retrieval.

Cross-user leakage tolerance is zero.

## Vulnerability Pipeline

Free-first candidates:
- SAST: CodeQL and/or Semgrep/FindSecBugs;
- SCA: Dependabot + OWASP Dependency-Check + package manager signals;
- secrets: GitHub secret scanning where available + Gitleaks;
- container: Trivy;
- DAST: OWASP ZAP.

Never suppress findings broadly just to make CI green.

## AI/RAG

Validate schema, source references, grounding, alternative correct responses, fallback contract preservation, STRICT_SOURCE insufficiency, and zero cross-user retrieval.

## Large Files

For 600+ pages validate bounded memory, batching, progress, restart/resume, idempotency, and no duplicate chunks/embeddings.

## Completion Evidence

Accept useful evidence such as CI URL, test output, scan report, benchmark/evaluation report, migration number, PR/commit, or UX screenshot/video.

A plain statement that "tests passed" is not sufficient tracker evidence.
