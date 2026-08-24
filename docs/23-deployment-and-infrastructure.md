---
Audience: Architecture, DevOps, backend, frontend, AI/RAG, security, QA,
  and product contributors.
Authors: Project Hippocampus Team
Created: 2026-08-24
Document ID: 23
Last Updated: 2026-08-24
Owner: Project Hippocampus Team
Prerequisites:
- 08 - Non-Functional Requirements
- 10 - AI Architecture v1.1+
- 13 - RAG Architecture
- 16 - System Architecture v1.1+
- 17 - Technology Stack & ADR Baseline
- 18 - Domain Model & Database Design
- 19 - Backend Architecture
- 20 - Frontend Architecture
- 21 - File Processing & Ingestion Architecture
- 22 - Security & Privacy Architecture
Purpose: Define the free-first deployment and infrastructure
  architecture for Hippocampus v1, including frontend hosting, backend
  runtime, PostgreSQL/pgvector, object storage, AI-provider networking,
  environments, secrets, TLS, CI/CD, migrations, backups, scaling, and
  upgrade triggers.
Related Documents:
- 24 - Observability & Operations
- 25 - Testing Strategy
- 26 - Development Roadmap & Implementation Phases
- 27 - Decision Log / ADR Index
Scope: Vercel frontend deployment, Render Spring Boot deployment, Neon
  PostgreSQL/pgvector, Cloudflare R2 object storage, outbound
  Gemini/Ollama APIs, environment separation, GitHub Actions, Docker,
  secrets, health checks, cold starts, background workers, migrations,
  backup strategy, capacity, free-tier constraints, cost controls, and
  transition to paid infrastructure.
Status: Final
Title: Deployment & Infrastructure
Version: 1.0.0
---

# 23 - Deployment & Infrastructure

## 1. Purpose

This document defines how Hippocampus v1 should be deployed with a
**free-first / lowest-cost practical infrastructure strategy**.

It answers:

> **How can Hippocampus support an initial controlled pilot of
> approximately 40 medical students while spending as little as possible
> and preserving a clean upgrade path when free tiers become
> insufficient?**

The deployment model must remain compatible with the architecture
defined in Documents 16--22.

------------------------------------------------------------------------

# 2. Locked Deployment Principle

> **Free tier is the starting deployment profile, not the reliability
> promise.**

Hippocampus should use free or included quotas where practical during
development and early pilot validation.

However:

-   provider limits may change;
-   free services may sleep;
-   free databases may have small storage limits;
-   free products may explicitly prohibit production/commercial
    workloads;
-   quotas may be exhausted.

The architecture must therefore make upgrading individual components
possible without redesigning the application.

------------------------------------------------------------------------

# 3. Recommended v1 Free-First Deployment

The recommended initial deployment is:

``` text
Frontend
Vercel Hobby
      │
      │ HTTPS
      ▼
Backend
Render Free Web Service
      │
      ├──────────────► Neon PostgreSQL 18 + pgvector
      │
      ├──────────────► Cloudflare R2 Private Bucket
      │
      ├──────────────► Gemini API
      │
      └──────────────► Ollama API
```

CI/CD:

``` text
GitHub
  ↓
GitHub Actions
  ├── test
  ├── build
  └── deploy / provider auto-deploy
```

------------------------------------------------------------------------

# 4. Deployment Classification

This infrastructure profile should be called:

> **PILOT-FREE**

It is appropriate for:

-   development;
-   demos;
-   internal testing;
-   controlled educational pilot;
-   product validation;
-   approximately 40 invited users with bounded usage.

It is **not** the long-term production architecture.

------------------------------------------------------------------------

# 5. Why Not Call Free Tier Production?

Current free-tier limitations make that misleading.

Render states that its Free instances are intended for testing, hobby
projects, and platform preview rather than production applications.

Vercel Hobby is positioned for personal/non-commercial use.

Therefore:

> **A public commercial release must trigger an infrastructure review
> even if usage technically remains within free quotas.**

------------------------------------------------------------------------

# 6. Infrastructure Components

  -----------------------------------------------------------------------
  Concern                             PILOT-FREE Choice
  ----------------------------------- -----------------------------------
  Frontend                            Vercel Hobby

  Backend                             Render Free Web Service

  Database                            Neon Free PostgreSQL 18

  Vector Search                       pgvector on Neon

  Lexical Search                      PostgreSQL FTS + `pg_trgm`

  Object Storage                      Cloudflare R2 Standard

  AI                                  Gemini API + Ollama API

  CI                                  GitHub Actions

  Source Control                      GitHub

  TLS                                 Managed by hosting providers

  Domain                              Provider subdomains initially;
                                      custom domain optional

  Background Jobs                     Same Render service initially

  Secrets                             Provider
                                      environment-variable/secret
                                      facilities

  Migrations                          Flyway at controlled backend deploy

  Monitoring                          Provider dashboards + application
                                      observability baseline
  -----------------------------------------------------------------------

------------------------------------------------------------------------

# 7. Frontend --- Vercel Hobby

## Decision

Deploy the React/Vite static frontend to **Vercel Hobby** for the
initial non-commercial pilot.

## Why

-   \$0 Hobby tier
-   Git integration
-   Managed HTTPS
-   CDN delivery
-   Simple Vite deployment
-   Custom domain support
-   No application server required for the React SPA

## Boundary

The frontend contains no:

-   database credentials;
-   Gemini key;
-   Ollama key;
-   R2 secret;
-   privileged backend secret.

Only public frontend configuration may be embedded at build time.

------------------------------------------------------------------------

# 8. Vercel Hobby Limitation

Current Vercel documentation identifies Hobby as a free plan, and Vercel
pricing positions it for personal/non-commercial use.

Therefore:

``` text
PILOT-FREE
→ acceptable for private/non-commercial validation

COMMERCIAL / FORMAL PRODUCTION
→ review Vercel Pro or another hosting target
```

Do not silently convert a commercial application onto Hobby without
reviewing current terms.

------------------------------------------------------------------------

# 9. Frontend Deployment Flow

``` mermaid
flowchart LR

A[GitHub main]
--> B[Vercel Build]

B --> C[Vite Build]
C --> D[Static Assets]
D --> E[Vercel CDN]
E --> F[Student Browser]
```

------------------------------------------------------------------------

# 10. Frontend Environment Variables

Frontend may receive only public values such as:

``` text
VITE_API_BASE_URL
VITE_APP_ENV
```

Never:

``` text
GEMINI_API_KEY
OLLAMA_API_KEY
DATABASE_URL
R2_SECRET_ACCESS_KEY
```

------------------------------------------------------------------------

# 11. Backend --- Render Free Web Service

## Decision

Deploy Spring Boot as a **Render Free Web Service** using Docker.

Render supports applications in virtually any language through Docker
images, which allows Java/Spring Boot deployment.

------------------------------------------------------------------------

# 12. Backend Container

Spring Boot should be packaged as a Docker image.

Conceptual multi-stage build:

``` text
Build Stage
Java 25 + Maven
      ↓
JAR
      ↓
Runtime Stage
Java 25 runtime
      ↓
Spring Boot application
```

Production container should run as a non-root user where practical.

------------------------------------------------------------------------

# 13. Render Port Binding

The application must bind to:

``` text
0.0.0.0
```

and use Render's provided:

``` text
PORT
```

environment variable.

Spring configuration should map accordingly.

------------------------------------------------------------------------

# 14. Render Free Cold Start

Render Free web services currently spin down after approximately 15
minutes without incoming traffic.

The next request triggers spin-up, which Render states can take about
one minute.

Therefore the pilot must expect:

``` text
First request after inactivity
→ potentially slow
```

The UI should handle this gracefully.

------------------------------------------------------------------------

# 15. Cold-Start UX

The frontend should not immediately present:

``` text
Server error
```

during a legitimate cold start.

Use:

``` text
Connecting to Hippocampus…
```

with a bounded retry strategy.

If the backend remains unavailable after the expected recovery window,
show the normal availability error.

------------------------------------------------------------------------

# 16. No Fake Keep-Alive Traffic

Do not intentionally send synthetic requests only to bypass free-service
sleep rules unless the provider explicitly permits that behavior.

The pilot accepts cold starts as a free-tier tradeoff.

------------------------------------------------------------------------

# 17. Render Free Instance Hours

Render currently provides 750 Free instance hours per workspace per
calendar month.

A single actively running service can therefore consume most/all of that
monthly allowance.

Do not deploy multiple unnecessary free backend services.

------------------------------------------------------------------------

# 18. Render Ephemeral Filesystem

Render Free web service filesystem changes are ephemeral.

Therefore:

> **Never store uploaded PDFs, extracted images, PostgreSQL data, or
> durable processing state on the Render filesystem.**

Temporary processing files are allowed only as temporary workspace and
must be recoverable from authoritative storage.

------------------------------------------------------------------------

# 19. Render External-Traffic Constraint

Render notes that a Free web service may be suspended for unusually high
service-initiated public internet traffic.

Hippocampus performs outbound traffic to:

-   Neon;
-   R2;
-   Gemini;
-   Ollama.

Therefore PILOT-FREE must:

-   batch provider calls;
-   avoid unnecessary repeated downloads;
-   avoid entire-file transfers per learning request;
-   reuse embeddings;
-   cache safely;
-   monitor outbound behavior.

If external traffic becomes material, backend hosting is an early
upgrade candidate.

------------------------------------------------------------------------

# 20. Backend Health Endpoints

Expose:

``` text
/actuator/health/liveness
/actuator/health/readiness
```

or equivalent secured/public-safe health endpoints.

Do not expose privileged actuator endpoints publicly.

------------------------------------------------------------------------

# 21. Database --- Neon Free

## Decision

Use **Neon Free** for PostgreSQL during PILOT-FREE.

Neon currently supports PostgreSQL 18 and the pgvector extension.

This preserves the technology choices locked in Document 17.

------------------------------------------------------------------------

# 22. Neon Free Current Constraints

Current Neon documentation describes the Free plan as including:

``` text
100 CU-hours / month
0.5 GB storage per project
```

Compute scales according to usage.

The 0.5 GB project-storage limit is the most important RAG constraint.

------------------------------------------------------------------------

# 23. Why Neon Instead of Render Free Postgres?

Render Free Postgres currently:

-   provides 1 GB;
-   expires after 30 days;
-   has no backups;
-   is explicitly free-preview infrastructure.

That makes it unsuitable as the durable pilot database.

Neon's persistent free project model is a better fit despite its smaller
per-project storage limit.

------------------------------------------------------------------------

# 24. Neon PostgreSQL Version

Create the pilot database using:

``` text
PostgreSQL 18
```

to remain aligned with Document 17.

------------------------------------------------------------------------

# 25. Neon Extensions

Enable:

``` sql
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
```

subject to Neon-supported extension availability.

PostgreSQL built-in full-text search requires no separate search
service.

------------------------------------------------------------------------

# 26. Database Connection Strategy

The backend should use Neon connection pooling where appropriate.

Spring/Hikari pool size should remain small on the free database.

Example design principle:

``` text
small bounded Hikari pool
+
managed/pooler endpoint
```

Exact pool values are benchmarked.

------------------------------------------------------------------------

# 27. Database Storage Is the Main Free-Tier Constraint

The relational metadata itself should remain small.

The largest database footprint is likely to be:

``` text
chunks
+
embeddings
+
indexes
```

particularly for multiple large textbooks.

------------------------------------------------------------------------

# 28. Vector Storage Efficiency

To stretch free storage:

-   use evaluated reduced embedding dimensionality where supported;
-   use `halfvec` if retrieval evaluation confirms acceptable quality;
-   avoid duplicate embeddings;
-   index only active material versions;
-   remove old derived embeddings after safe version transition;
-   prevent duplicated chunks;
-   do not embed useless headers/footers.

pgvector supports half-precision vector representation, which can reduce
vector storage relative to full precision.

------------------------------------------------------------------------

# 29. Free Database Capacity Is Not Guaranteed for 40 Heavy Users

Forty users may fit relationally.

Forty users each uploading several large textbooks may not fit within
0.5 GB of database storage.

Therefore:

> **The 40-user target is an application-user target, not a promise that
> unlimited user uploads fit inside Neon Free.**

Pilot quotas are required.

------------------------------------------------------------------------

# 30. Pilot Database Quotas

PILOT-FREE should initially limit:

``` text
materials per user
large materials per user
processed pages per user
indexed chunks per user
```

Exact values are set after ingestion/storage benchmarking.

These limits are operational, not educational restrictions.

------------------------------------------------------------------------

# 31. Object Storage --- Cloudflare R2

## Decision

Use **Cloudflare R2 Standard** for:

-   original PDFs;
-   uploaded images;
-   extracted figures;
-   derived visual assets.

R2 provides an S3-compatible API, matching the abstraction selected in
Document 17.

------------------------------------------------------------------------

# 32. R2 Current Free Usage

Current R2 Standard free-tier allowances include approximately:

``` text
10 GB-month storage / month
1 million Class A operations / month
10 million Class B operations / month
free internet egress
```

This is materially more appropriate for large PDF storage than placing
binaries in the free database.

------------------------------------------------------------------------

# 33. R2 Billing Account Caveat

R2 requires enabling an R2 subscription/checkout flow even though
included free monthly usage is available.

Therefore:

> **PILOT-FREE means expected \$0 usage within included quotas, not
> necessarily "no billing account/payment method ever configured."**

Set account/billing alerts where available.

------------------------------------------------------------------------

# 34. Private R2 Bucket

The bucket must remain private.

Do not use public `r2.dev` URLs for private student materials.

Access through:

-   backend-authorized streaming; or
-   short-lived signed URLs.

------------------------------------------------------------------------

# 35. R2 Bucket Layout

Conceptual:

``` text
hippocampus-materials/
  users/
    {userId}/
      materials/
        {materialId}/
          versions/
            {versionId}/
              original
              visuals/
              derived/
```

IDs are opaque application identifiers.

Original filenames are metadata, not storage paths.

------------------------------------------------------------------------

# 36. Storage Lifecycle

``` text
Upload
↓
R2 Original Object
↓
Processing
↓
Derived Visuals
↓
Authorized Study Access
↓
Deletion
↓
R2 Cleanup
```

No Render-local file is authoritative.

------------------------------------------------------------------------

# 37. Temporary Processing Files

Render temporary filesystem may hold working files only while a job is
active.

Requirements:

-   unique temp directories;
-   cleanup after job;
-   safe file names;
-   bounded size;
-   original always recoverable from R2.

------------------------------------------------------------------------

# 38. AI Providers

Both external providers remain outbound dependencies:

``` text
Gemini API
Ollama API
```

Render backend is the only caller.

------------------------------------------------------------------------

# 39. Provider Network Flow

``` text
Spring Boot
    ↓
AI Provider Router
   /               \
Gemini API      Ollama API
```

No inbound provider connection to Hippocampus is required.

------------------------------------------------------------------------

# 40. Provider Quota Protection

Because AI provider free quotas may change:

-   provider quotas are configuration;
-   not business rules;
-   rate-limit responses are normalized;
-   fallback is allowed only when contract-compatible;
-   usage is recorded.

------------------------------------------------------------------------

# 41. Environment Strategy

v1 should define:

``` text
LOCAL
PILOT
```

A separate full STAGING environment is optional while resources are
free-tier constrained.

------------------------------------------------------------------------

# 42. LOCAL Environment

Runs:

``` text
React locally
Spring Boot locally
Docker PostgreSQL + pgvector
Local filesystem / local S3-compatible adapter
External Gemini/Ollama development API credentials
```

LOCAL does not depend on cloud DB/storage for everyday development.

------------------------------------------------------------------------

# 43. PILOT Environment

Runs:

``` text
Frontend → Vercel
Backend  → Render
Database → Neon
Files    → Cloudflare R2
AI       → Gemini + Ollama APIs
```

PILOT contains invited-user data.

------------------------------------------------------------------------

# 44. Environment Isolation

Never point local automated tests directly at PILOT data.

Separate:

-   DB credentials
-   object buckets/prefixes
-   provider keys where practical
-   frontend API URLs
-   session secrets

------------------------------------------------------------------------

# 45. Git Branch Strategy

A simple baseline:

``` text
feature/*
   ↓
pull request
   ↓
main
   ↓
PILOT deploy
```

If direct main-to-pilot deployment becomes risky, introduce a
`develop`/release workflow later.

Do not overcomplicate branching initially.

------------------------------------------------------------------------

# 46. CI Pipeline

GitHub Actions baseline:

``` mermaid
flowchart TD

A[Pull Request]
--> B[Backend Compile/Test]
B --> C[Frontend Lint/Typecheck/Test]
C --> D[Integration Tests]
D --> E[Architecture Tests]
E --> F[Build Backend Image]
F --> G[Build Frontend]

G --> H{Main Branch?}
H -->|No| I[PR Complete]
H -->|Yes| J[Deploy]
```

------------------------------------------------------------------------

# 47. Deployment Responsibility

Recommended:

## Vercel

Git-based frontend auto-deploy.

## Render

Git-based or Docker-based backend deploy.

## Database

Flyway migration runs as part of controlled backend release.

## R2

No application artifact deployment required.

------------------------------------------------------------------------

# 48. Database Migration Deployment

Preferred flow:

``` text
Build passes
↓
Deploy backend revision
↓
Flyway migration
↓
Application starts
↓
Health check
```

For destructive/large migrations:

``` text
separate controlled migration step
```

may be required.

------------------------------------------------------------------------

# 49. Migration Failure

If Flyway fails:

-   backend should not start against a partially incompatible schema;
-   deployment should fail;
-   prior healthy version remains preferred where platform behavior
    allows;
-   manual repair follows Flyway rules.

------------------------------------------------------------------------

# 50. Backups --- Free-Tier Reality

PILOT-FREE cannot assume strong managed backup guarantees across every
service.

Therefore database backup strategy should supplement free hosting
limitations.

------------------------------------------------------------------------

# 51. Neon Backup / Restore Boundary

Use Neon-provided recovery features available to the current plan, but
do not assume they replace a deliberate backup policy.

Before pilot:

-   verify current restore window;
-   test restore;
-   document limitations.

------------------------------------------------------------------------

# 52. Application-Level Database Backup

For controlled pilot, periodically export:

``` text
pg_dump
```

to a protected backup location where practical.

Because private student data is involved, backups must remain
access-controlled.

------------------------------------------------------------------------

# 53. Backup Destination

Possible:

``` text
private R2 backup prefix
```

with strong retention/access policy.

Database dumps should not be publicly accessible.

------------------------------------------------------------------------

# 54. Object Storage Backup

R2 original files are the primary binary store.

Full duplication to another provider is not mandatory for initial free
pilot.

Before formal production, durability/backup requirements must be
revisited.

------------------------------------------------------------------------

# 55. Free-Tier Backup Limitation

> **PILOT-FREE optimizes for validation cost, not enterprise disaster
> recovery.**

This limitation must be understood before inviting students.

------------------------------------------------------------------------

# 56. Secrets by Platform

## Vercel

Only public frontend variables.

## Render

Stores:

``` text
DATABASE_URL
GEMINI_API_KEY
OLLAMA_API_KEY
R2_ENDPOINT
R2_ACCESS_KEY_ID
R2_SECRET_ACCESS_KEY
R2_BUCKET
SESSION/security secrets
```

## GitHub

CI secrets only when necessary.

Never duplicate secrets without reason.

------------------------------------------------------------------------

# 57. Secret Rotation

Architecture must support changing:

-   Gemini key;
-   Ollama key;
-   database password;
-   R2 credentials;
-   session secret/config;

without code changes.

------------------------------------------------------------------------

# 58. TLS

Use provider-managed HTTPS for:

-   Vercel frontend;
-   Render backend.

Backend calls:

-   Neon using TLS;
-   R2 using HTTPS;
-   Gemini using HTTPS;
-   Ollama using HTTPS.

------------------------------------------------------------------------

# 59. CORS

Backend CORS allows only:

``` text
PILOT Vercel origin
approved local development origins
approved custom domain later
```

No wildcard credentialed CORS.

------------------------------------------------------------------------

# 60. Session Cookie Across Domains

If frontend and backend use separate provider subdomains:

``` text
frontend.vercel.app
backend.onrender.com
```

cross-site cookie behavior can become cumbersome.

Preferred production-like pilot configuration:

``` text
app.example.com
api.example.com
```

under one registrable custom domain when feasible.

------------------------------------------------------------------------

# 61. Custom Domain Recommendation

A custom domain is optional for earliest testing but strongly
recommended before broader pilot.

Benefits:

-   cleaner cookie/SameSite behavior;
-   stable API origin;
-   provider migration without changing user-facing URLs;
-   professional pilot experience.

Domain cost is outside free hosting.

------------------------------------------------------------------------

# 62. If No Custom Domain

If provider domains are used:

-   carefully configure SameSite/Secure cookie behavior;
-   configure CORS credentials;
-   test Safari/Chrome/Firefox behavior.

If cross-site cookies become unreliable, use a
backend-for-frontend/domain proxy strategy rather than weakening cookie
security.

------------------------------------------------------------------------

# 63. Background Worker Deployment

PILOT-FREE initially runs background workers inside the same Render
service.

``` text
Spring Boot
├── HTTP
└── Processing Worker
```

This avoids a second paid/free service.

------------------------------------------------------------------------

# 64. Single-Service Worker Tradeoff

Benefits:

-   zero additional infrastructure;
-   shared code/config;
-   easy deployment.

Risks:

-   large processing competes with HTTP;
-   backend restart interrupts jobs;
-   Render resource limits apply.

Durable ProcessingJob state makes restart safe.

------------------------------------------------------------------------

# 65. Worker Concurrency

For free backend compute:

``` text
1 large ingestion job at a time
```

is a reasonable initial default until benchmarked.

Embedding batches may overlap only if provider/backend capacity allows.

Interactive AI requests retain priority.

------------------------------------------------------------------------

# 66. Large PDF on Render Free

A 600-page PDF can be processed, but processing must be:

-   batch-based;
-   memory-bounded;
-   resumable;
-   stored in R2/Neon;
-   not dependent on local disk persistence.

If large PDFs repeatedly hit Render resource/time constraints:

> backend/worker compute is the upgrade trigger.

------------------------------------------------------------------------

# 67. Processing During Backend Sleep

A free Render service can spin down when idle.

A running active job may produce service-initiated traffic without
inbound activity; free-platform behavior must be monitored.

Do not assume background processing is equivalent to an always-on
dedicated worker.

For critical long jobs, paid worker/backend infrastructure may
eventually be required.

------------------------------------------------------------------------

# 68. No Cron Dependency for Core Learning

Core review scheduling should not require a continuously running cron
job.

Review eligibility can be computed/queryable from:

``` text
eligible_at
```

when the student opens the app.

Periodic cleanup tasks may be best-effort during PILOT-FREE.

------------------------------------------------------------------------

# 69. Health and Readiness

Backend readiness should verify:

-   application boot;
-   database connectivity.

Do not make readiness depend on Gemini/Ollama always being available.

Provider availability is a degraded capability, not necessarily total
backend failure.

------------------------------------------------------------------------

# 70. Dependency Health

Expose internal diagnostics for:

``` text
database
object storage
Gemini
Ollama
processing queue
```

Sensitive details remain protected.

------------------------------------------------------------------------

# 71. Frontend Availability

Vercel static frontend may remain available when Render is
sleeping/unavailable.

The UI should distinguish:

``` text
App loaded
Backend connecting
```

rather than showing a blank page.

------------------------------------------------------------------------

# 72. Free-Tier Capacity Model

Approximately 40 invited users is feasible only with bounded use.

Most UI interactions are cheap.

Main constrained resources:

``` text
Render backend compute
Neon database storage
Neon compute hours
AI provider quotas
R2 storage
```

------------------------------------------------------------------------

# 73. Likely First Bottleneck

For Hippocampus, the most likely PILOT-FREE constraint is:

> **Neon database storage for chunks + embeddings.**

The next likely constraints are:

-   AI provider quotas;
-   Render cold starts/compute;
-   large-file background processing.

R2's 10 GB free storage is comparatively generous for the initial pilot.

------------------------------------------------------------------------

# 74. Pilot Quota Strategy

Before inviting \~40 users, configure:

``` text
max materials/user
max file size
max pages/material
max active indexed pages/user
max AI requests/minute
max concurrent ingestion jobs/user
```

These limits must be communicated if user-visible.

------------------------------------------------------------------------

# 75. Capacity Benchmark Before 40 Users

Benchmark using representative data:

``` text
10 users
20 users
40 users
```

with:

-   large PDFs;
-   multiple Study Missions;
-   embeddings;
-   concurrent AI;
-   processing jobs.

Do not infer 40-user capacity solely from provider marketing quotas.

------------------------------------------------------------------------

# 76. Free-Tier Upgrade Triggers

Upgrade a component when one or more thresholds occur.

## Frontend

Upgrade Vercel when:

-   commercial use begins;
-   Hobby terms no longer fit;
-   bandwidth/build limits become material.

## Backend

Upgrade Render when:

-   cold starts harm pilot;
-   background processing becomes unreliable;
-   outbound traffic limits become risky;
-   memory/CPU insufficient;
-   always-on availability is required.

## Database

Upgrade Neon when:

-   0.5 GB becomes restrictive;
-   compute quota approaches limit;
-   stronger backup/recovery is required.

## Storage

Upgrade R2 usage naturally when:

-   10 GB average Standard storage;

-   operations exceed free allowance.

------------------------------------------------------------------------

# 77. Upgrade Order Recommendation

Most likely:

``` text
1. Database capacity
2. Backend always-on compute
3. AI provider paid capacity
4. Object storage
5. Frontend
```

Actual order follows observed metrics.

------------------------------------------------------------------------

# 78. Paid Upgrade Must Not Require Redesign

Free → paid transitions should preserve:

``` text
same PostgreSQL schema
same R2/S3 adapter
same backend container
same React build
same provider adapters
```

Infrastructure plan changes should primarily be configuration.

------------------------------------------------------------------------

# 79. Alternative Database --- Supabase

Supabase is an acceptable fallback because its Free plan currently
provides:

-   PostgreSQL;
-   500 MB database quota;
-   pgvector;
-   1 GB file storage.

However, the free database size is similar to Neon and free Storage
limits individual file size to 50 MB, making it less attractive as the
primary file store for large medical PDFs.

Therefore Neon + R2 remains preferred.

------------------------------------------------------------------------

# 80. Why Not Render Free PostgreSQL

Rejected for PILOT durable state because current Free Render Postgres:

``` text
1 GB
30-day expiration
no backups
```

The expiration alone is disqualifying for persistent student learning
data.

------------------------------------------------------------------------

# 81. Why Not Store Files on Render

Rejected because Render Free filesystem is ephemeral.

------------------------------------------------------------------------

# 82. Why Not Store Files in PostgreSQL

Rejected because:

-   consumes scarce free DB storage;
-   complicates backups;
-   embeddings already compete for DB space;
-   R2 provides a larger storage allowance.

------------------------------------------------------------------------

# 83. Why Not Put Spring Boot on Vercel

Rejected baseline because Hippocampus requires:

-   persistent Spring runtime behavior;
-   background job execution;
-   long material processing;
-   database connection orchestration;
-   controlled SSE/provider interactions.

A standard Spring Boot container host is a better fit.

------------------------------------------------------------------------

# 84. Why Not Kubernetes

The pilot does not justify it.

------------------------------------------------------------------------

# 85. Infrastructure Architecture Diagram

``` mermaid
flowchart TB

Student[Medical Student]

subgraph Vercel
    FE[React / Vite Frontend]
end

subgraph Render
    BE[Spring Boot Backend]
    Worker[In-Process Background Worker]
end

subgraph Neon
    DB[(PostgreSQL 18 + pgvector)]
end

subgraph Cloudflare
    R2[(Private R2 Bucket)]
end

Gemini[Gemini API]
Ollama[Ollama API]
GitHub[GitHub / Actions]

Student --> FE
FE -->|HTTPS| BE

BE --> DB
BE --> R2
BE --> Gemini
BE --> Ollama
BE --> Worker

Worker --> DB
Worker --> R2
Worker --> Gemini
Worker --> Ollama

GitHub --> FE
GitHub --> BE
```

------------------------------------------------------------------------

# 86. Network Trust Diagram

``` text
PUBLIC INTERNET
│
├── Vercel Frontend
│       ↓
└── Render HTTPS Backend
        │
        ├── TLS → Neon
        ├── TLS → Cloudflare R2
        ├── TLS → Gemini
        └── TLS → Ollama
```

Database and object storage are never directly exposed as anonymous
application APIs.

------------------------------------------------------------------------

# 87. Deployment Sequence

``` mermaid
sequenceDiagram
    participant Dev as Developer
    participant GH as GitHub
    participant CI as GitHub Actions
    participant Neon as Neon
    participant Render as Render
    participant Vercel as Vercel

    Dev->>GH: Merge to main
    GH->>CI: Trigger CI

    CI->>CI: Backend tests
    CI->>CI: Frontend tests
    CI->>CI: Integration tests

    CI-->>Render: Trigger/backend deploy
    Render->>Neon: Flyway migrations
    Render->>Render: Start + health check

    CI-->>Vercel: Frontend deployment
    Vercel-->>Dev: Deployment ready
```

Provider-native Git deployment may replace explicit CI deploy triggers
while CI remains a required quality gate.

------------------------------------------------------------------------

# 88. Rollback

## Frontend

Use Vercel deployment history.

## Backend

Use Render rollback/deployment history where available.

## Database

Database schema rollback is **not** assumed automatic.

Migrations should be forward-fix oriented unless a tested rollback is
explicitly designed.

------------------------------------------------------------------------

# 89. Deployment Compatibility

Prefer expand/contract migrations when schema changes could overlap
deployments.

Example:

``` text
Add nullable column
↓
deploy application using it
↓
backfill
↓
enforce constraint later
```

This avoids fragile rollbacks.

------------------------------------------------------------------------

# 90. Configuration

Runtime config includes:

``` text
SPRING_PROFILES_ACTIVE=pilot
DATABASE_URL
FRONTEND_ORIGIN
R2_*
GEMINI_*
OLLAMA_*
AI concurrency
processing concurrency
file limits
rate limits
```

No environment-specific values hard-coded in source.

------------------------------------------------------------------------

# 91. Pilot Domain Layout

Preferred when custom domain exists:

``` text
app.hippocampus-domain.tld
api.hippocampus-domain.tld
```

Exact product name/domain remains changeable.

------------------------------------------------------------------------

# 92. DNS / TLS

Use managed DNS/provider TLS.

Certificate renewal should be automatic.

------------------------------------------------------------------------

# 93. Storage Region Considerations

When selecting regions, minimize unnecessary latency and cross-region
data movement.

Ideal:

``` text
Render region
Neon region
R2 location hint
```

should be geographically sensible for Philippine users where provider
choices permit.

Exact regions are selected at deployment time based on current provider
availability.

------------------------------------------------------------------------

# 94. Philippines Latency

Because initial users are expected in the Philippines, prefer
Asia-Pacific regions when available.

However:

-   free plans may restrict regions;
-   provider availability changes.

Measure actual latency rather than assuming nearest label always
performs best.

------------------------------------------------------------------------

# 95. Data Residency

v1 does not promise Philippine-only data residency.

External infrastructure and AI providers may process/store data in other
regions.

This must be consistent with the privacy disclosure from Document 22.

------------------------------------------------------------------------

# 96. Disaster Scenarios

PILOT-FREE should define behavior for:

## Render unavailable

Frontend loads; backend-dependent actions unavailable.

## Neon unavailable

Backend reports service unavailable; no learning-state writes.

## R2 unavailable

Existing DB state remains, source files temporarily inaccessible.

## Gemini unavailable

Provider Router may use approved Ollama fallback.

## Ollama unavailable

Provider Router may use approved Gemini fallback.

## Both AI providers unavailable

Deterministic product remains usable where possible.

------------------------------------------------------------------------

# 97. Recovery Priority

``` text
1. Protect data integrity
2. Restore database access
3. Restore backend
4. Restore source files
5. Restore AI capabilities
6. Resume background processing
```

AI availability must not outrank persistent user data.

------------------------------------------------------------------------

# 98. No Vendor Lock-In Assumption

Adapters preserve migration options.

Potential future replacements:

``` text
Vercel → Cloudflare Pages / Netlify / static CDN
Render → paid Render / Railway / Cloud Run / VPS
Neon → Supabase / Render Postgres / managed PostgreSQL
R2 → S3 / compatible object storage
Gemini/Ollama → alternate approved AI providers
```

A replacement requires an ADR if materially architectural.

------------------------------------------------------------------------

# 99. PILOT-FREE Cost Target

Target:

``` text
Hosting spend ≈ $0/month
```

while inside all included quotas.

Possible unavoidable/non-hosting cost:

-   domain;
-   payment-method verification;
-   AI quota overage if consciously enabled.

Spend caps/alerts should be configured where providers support them.

------------------------------------------------------------------------

# 100. No Automatic Surprise Billing

Where possible:

-   avoid auto-overage;
-   enable spend limits;
-   configure usage alerts;
-   monitor free quota.

If a provider cannot hard-stop usage, operational alerts become
mandatory.

------------------------------------------------------------------------

# 101. Pilot Readiness Checklist

Before inviting users:

1.  Vercel frontend deployed.
2.  Render backend deployed.
3.  Neon PostgreSQL 18 created.
4.  `vector` and `pg_trgm` enabled.
5.  Flyway migrations applied.
6.  R2 private bucket configured.
7.  Gemini/Ollama keys configured server-side.
8.  HTTPS verified.
9.  CORS/CSRF/session verified.
10. Material upload works end-to-end.
11. Large PDF job survives backend restart.
12. Deleted material becomes retrieval-ineligible.
13. RAG query works against Neon pgvector.
14. AI provider fallback tested.
15. Free-tier quota dashboards reviewed.
16. Backup/restore procedure tested.
17. Cold-start UX tested.
18. File/user quotas configured.
19. Security tests from Document 22 pass.
20. 40-user load simulation completed before full pilot.

------------------------------------------------------------------------

# 102. Upgrade Readiness Checklist

Move beyond PILOT-FREE when:

-   real production reliability is required;
-   monetization begins;
-   Vercel Hobby terms no longer fit;
-   cold starts are unacceptable;
-   database approaches storage quota;
-   AI quotas become unreliable;
-   background jobs exceed free compute;
-   backup/recovery requirements strengthen;
-   student count/use exceeds validated capacity.

------------------------------------------------------------------------

# 103. Locked v1 Deployment Decisions

The following are approved:

1.  Hippocampus uses a free-first `PILOT-FREE` deployment profile.
2.  PILOT-FREE is for controlled, non-commercial product validation
    rather than long-term production.
3.  Vercel Hobby hosts the React/Vite frontend.
4.  Spring Boot runs as a Dockerized Render Free Web Service.
5.  Render free-service cold starts are accepted as a pilot tradeoff.
6.  The frontend must handle backend cold starts gracefully.
7.  Render filesystem is never authoritative.
8.  Neon Free hosts PostgreSQL 18.
9.  Neon pgvector stores v1 embeddings.
10. PostgreSQL FTS + pg_trgm provide lexical retrieval.
11. Neon Free's 0.5 GB DB limit is recognized as a likely pilot
    constraint.
12. Per-user/indexing quotas are required to protect free database
    capacity.
13. Render Free Postgres is rejected for durable pilot state because its
    free database expires after 30 days.
14. Cloudflare R2 Standard stores original and derived binary materials.
15. R2 private buckets are mandatory.
16. R2's current included free Standard storage is used before paid
    storage.
17. R2 signed URLs, if used, are short-lived and authorized.
18. Gemini and Ollama remain external outbound provider dependencies.
19. Provider keys exist only on the backend.
20. LOCAL and PILOT are the initial environments.
21. Local development uses local PostgreSQL/pgvector and local file/S3
    adapter rather than consuming cloud quotas unnecessarily.
22. GitHub Actions is the CI quality gate.
23. Vercel/Render Git deployment may perform deployment after CI.
24. Flyway owns database migration.
25. Large/destructive migrations require controlled execution.
26. Background workers initially run inside the same Render backend.
27. Processing jobs remain durable in PostgreSQL so backend restarts are
    recoverable.
28. Worker concurrency is intentionally low on free compute.
29. Core review scheduling does not require an always-on cron.
30. Health/readiness does not fail merely because one AI provider is
    unavailable.
31. TLS is mandatory for production/pilot traffic.
32. Custom app/api subdomains are recommended before broader pilot.
33. Region selection should favor sensible APAC latency where free plans
    permit.
34. v1 does not promise Philippines-only data residency.
35. Free-tier quota and storage monitoring are mandatory.
36. Free tier is not assumed permanent.
37. Infrastructure upgrades are expected to be component-wise, not
    architectural rewrites.
38. Database and backend compute are the most likely first
    infrastructure upgrades.
39. Commercial/public production requires a fresh
    hosting/terms/reliability review.
40. Deployment choices must remain subordinate to the security,
    learning, RAG, and data architectures in Documents 00--22.

------------------------------------------------------------------------

# 104. Current Free-Tier Verification

Verified on **2026-08-24**.

## Vercel

-   Hobby plan remains free.
-   Hobby is positioned for personal/non-commercial use.

References:

-   https://vercel.com/docs/plans/hobby
-   https://vercel.com/pricing

## Render

Free web services currently:

-   spin down after 15 minutes without inbound traffic;
-   may take about one minute to restart;
-   receive 750 free instance hours per workspace/month;
-   use ephemeral local filesystems;
-   are described by Render as unsuitable for production;
-   may be suspended for unusually high service-initiated public
    traffic.

Free Render Postgres:

-   1 GB;
-   expires after 30 days;
-   no backups.

References:

-   https://render.com/docs/free
-   https://render.com/docs/web-services
-   https://render.com/docs/faq

## Neon

Current Free plan documentation includes:

-   100 CU-hours/month;
-   0.5 GB project storage.

Neon supports PostgreSQL 18 and pgvector.

References:

-   https://neon.com/pricing
-   https://neon.com/docs/introduction/plans
-   https://neon.com/docs/introduction/cost-optimization
-   https://neon.com/docs/reference/compatibility
-   https://neon.com/docs/extensions/pgvector

## Cloudflare R2

Current Standard free monthly usage includes:

-   10 GB-month storage;
-   1 million Class A operations;
-   10 million Class B operations;
-   free egress.

References:

-   https://developers.cloudflare.com/r2/pricing/
-   https://developers.cloudflare.com/r2/get-started/

Free-tier terms and quotas must be rechecked before actual deployment
because providers may change them.

------------------------------------------------------------------------

# 105. Out of Scope

This document does not lock:

-   final custom domain
-   exact Render region
-   exact Neon region
-   exact R2 location hint
-   exact Hikari pool size
-   exact user storage/page quota
-   exact AI provider quota
-   final paid upgrade plans
-   formal production SLA
-   enterprise backup
-   multi-region architecture
-   Kubernetes
-   dedicated worker infrastructure

These are benchmark/operations-driven.

------------------------------------------------------------------------

# 106. Next Document

**24 - Observability & Operations**

The next document should define:

-   logs
-   metrics
-   traces/correlation
-   AI/provider monitoring
-   RAG monitoring
-   processing-job monitoring
-   quota dashboards
-   free-tier usage alerts
-   error taxonomy
-   operational dashboards
-   incident handling
-   provider outages
-   backup verification
-   health checks
-   operational runbooks
-   pilot support procedures

It should answer:

> **How do we know Hippocampus is healthy, why something failed, and
> when free-tier infrastructure is approaching its limits?**

------------------------------------------------------------------------

# 107. Revision History

  -----------------------------------------------------------------------
  Version           Date              Author            Changes
  ----------------- ----------------- ----------------- -----------------
  1.0.0             2026-08-24        Project           Initial finalized
                                      Hippocampus Team  free-first
                                                        Deployment &
                                                        Infrastructure
                                                        architecture
                                                        selecting Vercel
                                                        Hobby, Render
                                                        Free, Neon
                                                        PostgreSQL 18 +
                                                        pgvector,
                                                        Cloudflare R2,
                                                        GitHub Actions,
                                                        controlled pilot
                                                        quotas,
                                                        cold-start
                                                        behavior, backup
                                                        boundaries, and
                                                        explicit upgrade
                                                        triggers

  -----------------------------------------------------------------------

------------------------------------------------------------------------

# 108. Approval

**Status:** Final

**Reviewed By:** Project Hippocampus Team

**Approved By:** Project Hippocampus Team
