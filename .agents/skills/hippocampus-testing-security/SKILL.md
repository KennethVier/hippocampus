---
name: hippocampus-testing-security
description: Use when designing, implementing, or reviewing Hippocampus tests and CI quality gates, including authorization-negative tests, RAG isolation, file-ingestion safety, provider failure handling, deterministic test design, and release evidence. This skill builds security-relevant tests but does not replace the independent final vulnerability review.
---

# Testing & Secure Test Design

## Principle

Use the least expensive test that proves the behavior/risk, but never skip a required security or cross-layer test.

Tests are evidence of behavior, not a substitute for architecture or security review.

## Version Policy

Use the versions managed/approved by the project and Spring Boot BOM unless a tracker task explicitly authorizes an override.

Prefer current-generation testing practices (JUnit Jupiter 6 generation for the Java 25/Spring Boot 4 baseline) without chasing upstream versions ahead of the supported dependency platform merely because they are newer.

## Layers

### Pure Unit / Domain

Learning Engine, Review Policy, Evidence Projector, value objects, state transitions, normalization/ranking utilities, finite-result policies.

Do not start Spring for pure deterministic rules.

### Application

Use fake ports when they make orchestration tests simpler and more stable. Verify authorization decisions, state transitions, emitted results, external-call sequencing, and failure behavior.

### PostgreSQL Integration

Use Testcontainers when behavior depends on real PostgreSQL/Flyway semantics: migrations, constraints, pgvector, FTS/pg_trgm, locking, transactions, indexes, query behavior, and job claiming.

Do not replace database integration evidence with mocked repositories when SQL/schema behavior is the risk.

### API / Spring Security Integration

Verify authentication, authorization/ownership/IDOR resistance, CSRF, CORS where applicable, stable errors, validation, upload limits, session behavior, security headers/configuration, and fail-closed behavior.

### Frontend

Use Vitest + React Testing Library for student-visible behavior, accessibility-relevant interaction, error/loading/empty states, and boundary behavior.

Test contracts/behavior rather than component internals or hook implementation details.

### E2E

Use E2E only for critical cross-layer journeys where lower layers cannot provide sufficient confidence. Introduce/expand tooling only when authorized by project scope.

## Gold-Standard Test Practices

- Prefer Arrange/Act/Assert clarity without ritualistic comments.
- Use descriptive behavior-oriented test names.
- Use parameterized tests when cases vary systematically.
- Inject `Clock` for deterministic temporal behavior.
- Never use `Thread.sleep` to stabilize tests.
- Do not test private methods directly.
- Do not mock value objects or trivial data carriers.
- Do not mock every dependency by default.
- Prefer fakes for application ports when state/behavior is clearer than interaction-heavy mocking.
- Use Mockito for collaboration/interaction behavior when a fake would be more complex or misleading.
- Assert externally meaningful state/result/error contracts.
- Add regression tests for every material defect/security finding fixed.

## Mandatory Negative Security Themes

When relevant, prove denial/failure paths as well as success:

- unauthenticated access denied;
- authenticated cross-user access denied;
- resource IDs cannot bypass ownership (IDOR/BOLA);
- role/privilege escalation denied;
- request-body ownership/state manipulation denied;
- RAG scope enforced before ranking;
- source-reference forgery rejected;
- deleted/inactive material excluded;
- CSRF/session assumptions enforced;
- malicious/oversized uploads rejected safely;
- XSS/generated Markdown rendered safely;
- prompt-injection content cannot override server-owned authorization/state;
- secrets/provider credentials never reach client/error/log contracts;
- provider failures preserve safe fallback/failure contracts;
- exceptional conditions fail closed where security is uncertain.

Cross-user leakage tolerance is zero.

## AI / RAG Tests

Validate schema, source references, grounding, alternative correct responses, fallback contract preservation, STRICT_SOURCE insufficiency, authorization-before-retrieval/ranking, and zero cross-user retrieval.

Treat retrieved/uploaded content as untrusted data, not trusted instructions.

## File Ingestion Tests

When file ingestion is in scope, test applicable limits and controls: supported type/content checks, filename/path safety, bounded size/memory, decompression/archive protections where relevant, parser failures, private storage/ownership, restart/resume/idempotency, cleanup, and no duplicate chunks/embeddings.

## Large Files

For 600+ pages or other high-volume cases, validate bounded memory, batching, progress, restart/resume, idempotency, and no duplicate chunks/embeddings when those requirements are in scope.

## CI / Security Tooling

Tooling must be selected for project value and compatibility, not installed as checklist decoration.

Potential layers include:

- SAST: CodeQL and/or Semgrep/Java-specific analysis;
- SCA/dependency review: Dependabot, GitHub dependency review, OWASP Dependency-Check, package-manager signals;
- secrets: GitHub secret scanning where available and/or Gitleaks;
- container scanning: Trivy when container images become a real delivery artifact;
- DAST: OWASP ZAP when a runnable test environment and threat model justify it.

Never suppress findings broadly just to make CI green. Suppress only a proven false positive with narrow documented justification.

## Independent Security Gate

After implementation tests and general code/architecture review pass, run `hippocampus-security-vulnerability-review`.

A green test suite does not grant `SECURITY PASS`.

## Completion Evidence

Accept useful evidence such as CI URL, test output, scan report, benchmark/evaluation report, migration number, PR/commit, or UX screenshot/video.

A plain statement that "tests passed" is not sufficient tracker evidence.
