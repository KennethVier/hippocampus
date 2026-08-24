---
Audience: Architecture, backend, frontend, AI/RAG, DevOps, security, QA,
  and product contributors.
Authors: Project Hippocampus Team
Created: 2026-08-24
Document ID: 24
Last Updated: 2026-08-24
Owner: Project Hippocampus Team
Prerequisites:
- 08 - Non-Functional Requirements
- 10 - AI Architecture v1.1+
- 13 - RAG Architecture
- 16 - System Architecture v1.1+
- 18 - Domain Model & Database Design
- 19 - Backend Architecture
- 21 - File Processing & Ingestion Architecture
- 22 - Security & Privacy Architecture
- 23 - Deployment & Infrastructure
Purpose: Define how Hippocampus v1 is observed, operated, diagnosed, and
  recovered during the free-first controlled pilot, including
  application health, logs, metrics, AI/RAG behavior, ingestion jobs,
  infrastructure quotas, security signals, vulnerability monitoring,
  incidents, backups, and operational runbooks.
Related Documents:
- 25 - Testing Strategy
- 26 - Development Roadmap & Implementation Phases
- 27 - Decision Log / ADR Index
Scope: Health/readiness, structured logging, correlation, application
  metrics, AI/provider telemetry, RAG quality telemetry, ingestion
  monitoring, database/storage/hosting quotas, security observability,
  vulnerability monitoring, alerting, error taxonomy, dashboards,
  incidents, provider degradation, backup verification, operational
  runbooks, support diagnostics, data minimization, and upgrade
  triggers.
Status: Final
Title: Observability & Operations
Version: 1.0.0
---

# 24 - Observability & Operations

## 1. Purpose

This document defines how the Hippocampus team knows:

> **Is the system healthy, what failed, why did it fail, is learning
> quality being degraded, are security problems emerging, and are
> free-tier resources approaching their limits?**

Observability is not limited to server uptime.

For Hippocampus, operational health includes:

``` text
Application Health
+
Data Health
+
Ingestion Health
+
Retrieval Health
+
AI Provider Health
+
Learning-Flow Health
+
Security Health
+
Infrastructure Capacity
```

------------------------------------------------------------------------

# 2. Locked Observability Principle

> **Observe the learning system, not only the web server.**

A backend returning HTTP 200 does not mean Hippocampus is healthy if:

-   retrieval returns irrelevant evidence;
-   embeddings fail;
-   source references are invalid;
-   PDF jobs are stuck;
-   AI providers are rate-limited;
-   review records are not created;
-   another user's source appears in retrieval;
-   free database storage is nearly exhausted.

Operational monitoring must reflect the architecture that actually
creates the learning experience.

------------------------------------------------------------------------

# 3. Second Locked Principle

> **Observability must help diagnose problems without becoming another
> source of private student-data leakage.**

Logs and metrics should primarily contain:

``` text
IDs
counts
durations
states
provider/model
task types
error codes
resource usage
```

not:

``` text
full PDFs
source chunks
student answers
full prompts
API keys
cookies
session IDs
```

------------------------------------------------------------------------

# 4. Observability Layers

``` text
Layer 1 — Infrastructure
Layer 2 — Application
Layer 3 — Processing
Layer 4 — Retrieval / RAG
Layer 5 — AI Providers
Layer 6 — Learning Engine
Layer 7 — Security
Layer 8 — Product/Pilot Operations
```

------------------------------------------------------------------------

# 5. PILOT-FREE Philosophy

For approximately 40 invited users, avoid introducing an expensive
observability platform before it is needed.

Start with:

``` text
Spring Boot Actuator
+
Micrometer
+
Structured Application Logs
+
Provider Dashboards
+
GitHub Security/Dependency Signals
+
Database Operational Queries
+
Simple Internal Operational Views
```

Add dedicated telemetry platforms only when justified.

------------------------------------------------------------------------

# 6. No Premature Observability Infrastructure

Do not introduce by default:

-   Elasticsearch cluster
-   self-hosted Grafana stack
-   Kafka
-   distributed tracing infrastructure
-   dedicated SIEM
-   complex log pipelines

for the initial pilot.

The architecture must permit adding them later.

------------------------------------------------------------------------

# 7. Health Endpoints

Baseline:

``` text
/actuator/health/liveness
/actuator/health/readiness
```

## Liveness

Answers:

> Is the application process alive?

## Readiness

Answers:

> Can the application serve core requests?

------------------------------------------------------------------------

# 8. Readiness Dependencies

Readiness should include critical dependencies such as:

``` text
PostgreSQL
```

Object storage may be represented separately depending on feature
impact.

Do not make readiness fail merely because:

``` text
Gemini unavailable
```

if Ollama or deterministic features remain available.

------------------------------------------------------------------------

# 9. Capability Health

Hippocampus should distinguish:

``` text
SYSTEM_UP
AI_DEGRADED
INGESTION_DEGRADED
SOURCE_STORAGE_DEGRADED
SYSTEM_DOWN
```

or equivalent internal status.

This supports graceful degradation.

------------------------------------------------------------------------

# 10. Health Does Not Equal AI Availability

Example:

``` text
PostgreSQL = UP
R2 = UP
Gemini = DOWN
Ollama = UP
```

System:

``` text
UP / AI_PARTIALLY_DEGRADED
```

not:

``` text
DOWN
```

------------------------------------------------------------------------

# 11. Structured Logging

Backend logs should be structured.

Recommended fields:

``` text
timestamp
level
service
environment
correlationId
userOpaqueId
requestPath
method
status
durationMs
domain
operation
errorCode
```

Fields are included only when useful and safe.

------------------------------------------------------------------------

# 12. Correlation IDs

Every inbound request should have a correlation ID.

If client supplies one, validate its format or generate a new one.

The same ID should propagate through:

``` text
HTTP Request
→ Domain Operation
→ RAG
→ AI Provider
→ Processing Job
```

where applicable.

------------------------------------------------------------------------

# 13. Correlation Privacy

Correlation IDs must not encode:

-   email
-   user name
-   material title
-   source text
-   provider secret

Use random opaque identifiers.

------------------------------------------------------------------------

# 14. User Identifier in Logs

If user correlation is operationally needed, prefer an opaque internal
identifier.

Do not routinely log student email.

------------------------------------------------------------------------

# 15. Log Levels

## ERROR

Unexpected failure requiring investigation.

## WARN

Degraded behavior, retries, quota pressure, suspicious activity.

## INFO

Important lifecycle events.

## DEBUG

Development diagnostics; restricted/disabled appropriately in pilot.

Never use DEBUG as justification for logging secrets/private content.

------------------------------------------------------------------------

# 16. Error Taxonomy

Errors should be categorized rather than relying on arbitrary exception
messages.

High-level categories:

``` text
AUTH
AUTHORIZATION
VALIDATION
DATABASE
STORAGE
INGESTION
RETRIEVAL
AI_PROVIDER
LEARNING_ENGINE
RATE_LIMIT
SECURITY
INTERNAL
```

------------------------------------------------------------------------

# 17. Stable Error Codes

Examples:

``` text
AUTH_SESSION_EXPIRED
AUTH_FORBIDDEN_RESOURCE
FILE_UNSUPPORTED
FILE_PROCESSING_TIMEOUT
RAG_NO_EVIDENCE
RAG_REFERENCE_INVALID
AI_PROVIDER_TIMEOUT
AI_PROVIDER_RATE_LIMIT
AI_OUTPUT_INVALID
DB_UNAVAILABLE
STORAGE_UNAVAILABLE
SECURITY_SCOPE_VIOLATION
```

These support frontend handling and operational aggregation.

------------------------------------------------------------------------

# 18. Stack Traces

Stack traces:

-   may appear in protected server logs;
-   never appear directly to students;
-   should not expose secrets.

Provider errors should be sanitized before logging.

------------------------------------------------------------------------

# 19. Core Application Metrics

Track:

``` text
http_requests_total
http_request_duration
http_5xx_total
active_sessions
rate_limit_events
```

Avoid unnecessary high-cardinality labels.

------------------------------------------------------------------------

# 20. High-Cardinality Rule

Do not use:

``` text
userId
materialId
missionId
```

as Prometheus-style metric labels.

Use logs/traces for individual-resource investigation.

Metrics aggregate.

------------------------------------------------------------------------

# 21. Database Observability

Monitor:

``` text
connection pool usage
query latency
slow queries
database errors
storage usage
vector/index size
active connections
migration status
```

For PILOT-FREE, storage pressure is especially important.

------------------------------------------------------------------------

# 22. Neon Free Capacity Monitoring

Current deployment architecture recognizes the Neon Free database limit
as a likely first bottleneck.

Operational checks should track:

``` text
database size
chunk count
embedding count
material count
active version count
```

and compare them with the current provider quota.

------------------------------------------------------------------------

# 23. Database Capacity Thresholds

Conceptual thresholds:

``` text
NORMAL
WARNING
CRITICAL
```

Exact percentages are configuration.

At WARNING:

-   inspect growth;
-   stop unnecessary duplication;
-   forecast exhaustion.

At CRITICAL:

-   restrict new heavy ingestion if necessary;
-   prepare upgrade;
-   protect existing student data.

------------------------------------------------------------------------

# 24. Storage Observability

For R2 track:

``` text
stored bytes
object count
upload failures
download failures
delete failures
operation usage
```

Use Cloudflare's current account/provider metrics where available.

------------------------------------------------------------------------

# 25. Render Observability

Track:

``` text
deploy status
service restarts
cold starts
memory pressure
CPU behavior where visible
instance-hour usage
outbound behavior
```

Provider dashboard remains a valid operational source during PILOT-FREE.

------------------------------------------------------------------------

# 26. Cold-Start Monitoring

Track backend cold-start symptoms.

Examples:

``` text
initial request latency
health transition
frontend retry success
```

If cold starts materially damage Study Mission experience, this becomes
an upgrade signal.

------------------------------------------------------------------------

# 27. Vercel Observability

Track:

``` text
deployment failures
build failures
frontend errors
bandwidth/usage quotas where applicable
```

Do not collect private learning content in frontend analytics.

------------------------------------------------------------------------

# 28. Ingestion Observability

Each ProcessingJob should expose:

``` text
jobId
materialVersionId
stage
state
progress
retryCount
startedAt
completedAt
duration
errorCode
```

------------------------------------------------------------------------

# 29. Ingestion Metrics

Track:

``` text
processing_jobs_total
processing_jobs_failed
processing_jobs_retried
processing_duration
processing_queue_depth
pages_processed
ocr_pages
chunks_generated
visuals_extracted
embedding_batches
```

------------------------------------------------------------------------

# 30. Stuck Job Detection

A job is potentially stuck when:

``` text
RUNNING
+
no heartbeat/progress update
+
stage timeout exceeded
```

Operations should be able to:

``` text
inspect
retry
fail
cancel
```

without editing database rows manually where practical.

------------------------------------------------------------------------

# 31. Job Heartbeat

Long-running processing stages should periodically persist:

``` text
lastHeartbeatAt
progress
```

This distinguishes:

``` text
slow
```

from:

``` text
dead
```

------------------------------------------------------------------------

# 32. Large PDF Monitoring

For large materials track:

``` text
pages total
pages extracted
failed pages
OCR pages
chunks created
visuals created
embedding completion
```

This allows precise support:

``` text
"Embedding failed at 82%; extraction is intact."
```

rather than:

``` text
"PDF failed."
```

------------------------------------------------------------------------

# 33. RAG Observability

RAG must be observable as a pipeline.

Track:

``` text
retrieval mode
query rewrite used
lexical candidate count
vector candidate count
reranked candidate count
selected evidence count
source quality
retrieval latency
```

------------------------------------------------------------------------

# 34. RAG Quality Signals

Track aggregate signals such as:

``` text
NO_EVIDENCE
LIMITED_EVIDENCE
STRONG_EVIDENCE
```

and:

``` text
citation/reference validation failure
retrieval empty rate
retrieval fallback rate
```

These are more meaningful than token count alone.

------------------------------------------------------------------------

# 35. RAG Security Signal

Any cross-user scope validation failure is:

``` text
SECURITY_CRITICAL
```

not merely a retrieval-quality issue.

Expected production/pilot tolerance:

``` text
0
```

------------------------------------------------------------------------

# 36. Retrieval Debugging

Protected diagnostics may record:

``` text
chunk IDs
scores
materialVersion IDs
heading paths
quality classifications
```

Avoid storing full chunk text in general logs.

------------------------------------------------------------------------

# 37. Retrieval Evaluation vs Runtime Monitoring

Runtime monitoring asks:

> Is retrieval behaving unusually?

Document 25 evaluation asks:

> Is retrieval objectively good enough?

These are related but distinct.

------------------------------------------------------------------------

# 38. AI Provider Observability

For every provider request track:

``` text
provider
model
taskType
promptVersion
inputTokenCount
outputTokenCount
latency
status
retryCount
fallbackUsed
errorCode
```

when provider APIs expose the required values.

------------------------------------------------------------------------

# 39. AI Content Logging Rule

Do not log full:

``` text
prompt
source context
student response
model response
```

by default.

For debugging exceptional cases, use explicit protected diagnostic
mechanisms with retention controls rather than general logs.

------------------------------------------------------------------------

# 40. Provider Metrics

Track:

``` text
ai_requests_total
ai_success_rate
ai_latency
ai_rate_limits
ai_timeouts
ai_invalid_outputs
ai_fallbacks
ai_token_usage
```

per provider/model/task type.

------------------------------------------------------------------------

# 41. Gemini / Ollama Comparison

Observability should allow comparison of:

``` text
reliability
latency
structured-output success
fallback frequency
token consumption
evaluation quality
```

without automatically deciding educational quality from latency.

------------------------------------------------------------------------

# 42. Provider Failure Classification

``` text
TIMEOUT
RATE_LIMIT
AUTH_FAILURE
INVALID_RESPONSE
OUTPUT_SCHEMA_FAILURE
CONTENT_REJECTION
NETWORK_FAILURE
PROVIDER_5XX
```

------------------------------------------------------------------------

# 43. Provider Authentication Failure

An authentication failure likely indicates:

-   invalid/expired key;
-   secret misconfiguration;
-   provider account issue.

This should generate an operational alert because retries will usually
not fix it.

------------------------------------------------------------------------

# 44. Provider Rate Limit

Rate limit:

``` text
WARN
→ respect Retry-After
→ fallback if permitted
→ protect provider quota
```

Repeated rate limits may indicate that free capacity is insufficient.

------------------------------------------------------------------------

# 45. AI Quality Is Not Inferred From Success Status

A successful `200` response can still be:

-   malformed;
-   unsupported;
-   poorly grounded;
-   pedagogically weak.

Therefore runtime validation metrics are required.

------------------------------------------------------------------------

# 46. AI Output Validation Metrics

Track:

``` text
schema_validation_failure
source_reference_validation_failure
unsupported_claim_detection where implemented
fallback_generation
```

Detailed AI evaluation belongs to Document 15 and Document 25.

------------------------------------------------------------------------

# 47. Learning Engine Observability

Track state transitions such as:

``` text
mission_created
activity_started
attempt_submitted
feedback_generated
evidence_recorded
review_scheduled
mission_completed
```

These are operational lifecycle events, not advertising analytics.

------------------------------------------------------------------------

# 48. Learning-Flow Failure Signals

Examples:

``` text
attempt persisted but evidence missing
mission completed but review record missing
activity generated without valid source references
review due but inaccessible
```

These indicate domain consistency problems.

------------------------------------------------------------------------

# 49. Educational Integrity Monitoring

Operational invariants should detect impossible states.

Example:

``` text
LearningEvidence exists
but Attempt does not exist
```

or:

``` text
STRICT_SOURCE activity
has no valid SourceReference
```

These should be surfaced to operations.

------------------------------------------------------------------------

# 50. Privacy-Safe Product Metrics

Useful pilot metrics:

``` text
active users/day
missions started
missions completed
materials uploaded
materials ready
reviews completed
processing failures
```

Do not attach raw answers/material content.

------------------------------------------------------------------------

# 51. Security Observability

Security signals should include:

``` text
failed login
repeated authentication failure
forbidden resource access
CSRF rejection
rate-limit trigger
upload rejection
source-reference validation failure
cross-user scope attempt
unexpected provider authentication failure
```

------------------------------------------------------------------------

# 52. Security Event Severity

## INFO

Expected security lifecycle event.

## WARNING

Potential abuse/misconfiguration.

## HIGH

Likely vulnerability or active abuse requiring prompt investigation.

## CRITICAL

Possible confidentiality/integrity breach.

------------------------------------------------------------------------

# 53. Critical Security Signals

Examples:

``` text
successful cross-user data access
provider key exposure
authorization bypass
retrieval scope leakage
stored XSS execution
deleted material still retrievable
```

These require immediate response.

------------------------------------------------------------------------

# 54. Vulnerability Monitoring

Vulnerability checking spans three documents:

``` text
22 — Security requirements
24 — Continuous vulnerability/security monitoring
25 — Active vulnerability testing and CI release gates
```

Document 24 owns the operational monitoring component.

------------------------------------------------------------------------

# 55. Dependency Vulnerability Monitoring

Monitor vulnerability advisories for:

``` text
Maven dependencies
npm dependencies
Docker/base images
GitHub repository dependencies
```

Prefer free/native capabilities during PILOT-FREE where sufficient.

------------------------------------------------------------------------

# 56. GitHub Security Signals

Where available/configured, use repository security features such as:

``` text
Dependabot alerts
dependency graph
security advisories
secret scanning capabilities
```

Availability may depend on repository visibility/account plan and should
be verified at implementation time.

------------------------------------------------------------------------

# 57. Vulnerability Lifecycle

``` mermaid
flowchart TD

A[Vulnerability Detected]
--> B[Validate Finding]

B --> C[Classify Severity]
C --> D{Exploitable / Relevant?}

D -->|No| E[Document / Accept / Monitor]
D -->|Yes| F[Create Remediation]

F --> G[Patch / Mitigate]
G --> H[Security Tests]
H --> I[Deploy]
I --> J[Verify]
J --> K[Close]
```

------------------------------------------------------------------------

# 58. Vulnerability Record

Track at minimum:

``` text
findingId
source
component
package/image
CVE/advisory if applicable
severity
affectedVersion
status
detectedAt
remediatedAt
resolution
```

Do not store exploit secrets or private user data unnecessarily.

------------------------------------------------------------------------

# 59. Vulnerability Severity

Use a standard severity basis such as:

``` text
LOW
MEDIUM
HIGH
CRITICAL
```

CVSS can inform severity where available.

Application context still matters.

Example:

A vulnerable library not reachable in Hippocampus may have different
operational priority than an exploitable authentication flaw.

------------------------------------------------------------------------

# 60. Release Blocking

Document 25 will define exact CI gates.

Operational rule:

> **Known exploitable Critical vulnerabilities must block release until
> resolved or explicitly risk-accepted through a documented security
> decision.**

High vulnerabilities require review and disposition before release.

------------------------------------------------------------------------

# 61. Secret Exposure Monitoring

Watch for accidental exposure in:

``` text
Git commits
CI logs
application logs
frontend bundles
error responses
```

If a provider secret is exposed:

``` text
revoke
↓
rotate
↓
investigate
↓
verify no continued use
```

Do not merely delete it from Git history and keep using the same key.

------------------------------------------------------------------------

# 62. Prompt-Injection Monitoring

Runtime signals may include:

``` text
source-reference validation failures
unexpected output schema
attempted instruction-like source patterns where safely detectable
scope escalation attempts
```

Do not store full malicious source text in ordinary logs.

------------------------------------------------------------------------

# 63. Abuse Monitoring

Watch for:

``` text
many large uploads
rapid AI requests
repeated failed authorization
repeated invalid source references
unusual processing queue consumption
```

Use rate limits before expensive operations.

------------------------------------------------------------------------

# 64. Alert Philosophy

PILOT-FREE should avoid noisy alerting.

Alert only when action may be required.

Bad:

``` text
every 404 sends alert
```

Good:

``` text
database near capacity
provider auth failing
processing failure rate spikes
critical security signal
backup failed
```

------------------------------------------------------------------------

# 65. Alert Levels

## P1 --- Critical

Potential data/security loss or complete outage.

## P2 --- High

Major capability unavailable/degraded.

## P3 --- Medium

Operational degradation requiring investigation.

## P4 --- Low

Informational/trend.

------------------------------------------------------------------------

# 66. Example P1

``` text
cross-user data leak
database corruption
provider secret leaked
active source deletion failure causing unauthorized retrieval
```

------------------------------------------------------------------------

# 67. Example P2

``` text
database unavailable
all AI providers unavailable for prolonged period
R2 inaccessible
processing queue completely stuck
database storage critical
```

------------------------------------------------------------------------

# 68. Example P3

``` text
one AI provider down but fallback works
high ingestion failure rate
cold starts harming UX
repeated rate limits
```

------------------------------------------------------------------------

# 69. Alert Channels

For the initial small team, use simple channels:

``` text
email
GitHub issue/security alert
provider dashboard notification
```

A dedicated paging platform is not required initially.

------------------------------------------------------------------------

# 70. Operational Dashboard

A simple internal/admin operational view should eventually show:

``` text
System status
Database status
R2 status
Gemini status
Ollama status
Processing queue
Failed jobs
AI requests
Provider fallbacks
Database storage
Object storage
Security warnings
Recent deployments
```

This is an operational interface, not a student feature.

------------------------------------------------------------------------

# 71. Dashboard Privacy

Do not display:

-   raw student answers;
-   full source text;
-   private PDFs;
-   API keys.

Operational drill-down requires explicit authorization.

------------------------------------------------------------------------

# 72. Free-Tier Quota Dashboard

Track current provider limits separately from application configuration.

Important resources:

``` text
Render instance hours
Neon CU-hours
Neon DB storage
R2 storage/operations
Vercel usage
Gemini quota
Ollama quota
```

Provider limits can change.

------------------------------------------------------------------------

# 73. Quota Configuration

Do not hard-code provider free quotas into business logic.

Store operational thresholds in configuration.

Reverify provider limits periodically.

------------------------------------------------------------------------

# 74. Quota Warning

Conceptually:

``` text
usage < warning
→ NORMAL

usage >= warning
→ WARN

usage >= critical
→ CRITICAL
```

At critical, protect existing learning data before accepting expensive
new work.

------------------------------------------------------------------------

# 75. Capacity Forecasting

For pilot, simple trend calculation is sufficient:

``` text
current usage
+
average daily growth
→ estimated exhaustion date
```

No ML forecasting required.

------------------------------------------------------------------------

# 76. Upgrade Trigger

Operations should be able to answer:

> At the current rate, when will the free tier stop being sufficient?

This is more useful than discovering limits after service failure.

------------------------------------------------------------------------

# 77. Incident Lifecycle

``` mermaid
flowchart TD

A[Detect]
--> B[Classify]
B --> C[Contain]
C --> D[Diagnose]
D --> E[Recover]
E --> F[Verify]
F --> G[Document]
G --> H[Prevent Recurrence]
```

------------------------------------------------------------------------

# 78. Incident Record

Capture:

``` text
incidentId
startedAt
detectedAt
severity
affectedCapabilities
rootCause
userImpact
containment
resolution
followUp
```

Avoid copying private content into incident records unless absolutely
required.

------------------------------------------------------------------------

# 79. Incident Communication

For a controlled pilot:

-   acknowledge meaningful outages;
-   describe affected capability;
-   avoid speculation;
-   communicate recovery when confirmed.

Do not expose internal security details that create additional risk.

------------------------------------------------------------------------

# 80. Provider Outage Runbook

``` text
Provider fails
↓
Classify error
↓
Check provider status/config
↓
Use compatible fallback if allowed
↓
If no fallback:
  degrade AI-dependent feature
↓
Preserve deterministic learning state
↓
Retry safely
```

------------------------------------------------------------------------

# 81. Database Outage Runbook

``` text
Stop state-changing operations
↓
Report degraded/unavailable
↓
Verify Neon status
↓
Do not write elsewhere as fake fallback
↓
Restore DB connectivity
↓
Verify migrations/data
↓
Resume
```

------------------------------------------------------------------------

# 82. R2 Outage Runbook

``` text
Source binary unavailable
↓
Do not delete DB metadata
↓
Pause source-dependent processing
↓
Preserve existing learning state
↓
Restore storage
↓
Retry jobs
```

------------------------------------------------------------------------

# 83. Ingestion Failure Runbook

``` text
Inspect ProcessingJob
↓
Identify stage
↓
Classify fatal/transient/partial
↓
Retry if transient
↓
Resume from durable intermediate stage
↓
Mark PARTIALLY_READY or FAILED when appropriate
```

------------------------------------------------------------------------

# 84. Stuck Queue Runbook

Check:

``` text
worker alive?
job heartbeat?
DB locking?
provider rate limit?
storage failure?
memory/resource exhaustion?
```

Then recover without blindly deleting job state.

------------------------------------------------------------------------

# 85. AI Quality Incident

If AI output quality suddenly degrades:

``` text
check prompt version
check provider/model
check retrieval evidence
check output validation
check provider changes
compare fallback provider
```

Do not immediately blame the model without inspecting retrieval and
prompt contracts.

------------------------------------------------------------------------

# 86. RAG Quality Incident

If source-grounded answers become poor:

``` text
check material READY state
check chunk/index generation
check embedding model/version
check lexical/vector retrieval
check source quality
check reranking
check source-reference validation
```

------------------------------------------------------------------------

# 87. Security Incident Runbook

If suspected data exposure:

``` text
Contain
↓
Disable affected capability if necessary
↓
Revoke/rotate credentials
↓
Preserve minimal forensic evidence
↓
Identify scope
↓
Patch
↓
Verify
↓
Communicate appropriately
```

Detailed legal obligations are outside this technical document.

------------------------------------------------------------------------

# 88. Backup Monitoring

A backup is not successful merely because a file exists.

Track:

``` text
last successful backup
backup size
backup failure
restore verification date
```

------------------------------------------------------------------------

# 89. Restore Testing

Periodically verify that a database backup can actually restore into an
isolated environment.

Never test restore by overwriting PILOT.

------------------------------------------------------------------------

# 90. Backup Failure

Backup failure should produce an operational warning/high alert
depending on duration and current recovery guarantees.

------------------------------------------------------------------------

# 91. Deployment Monitoring

After each deployment verify:

``` text
application starts
Flyway succeeds
readiness passes
database works
R2 works
basic authenticated request works
RAG smoke test
AI provider smoke test where quota permits
```

------------------------------------------------------------------------

# 92. Deployment Marker

Logs/metrics should identify:

``` text
applicationVersion
gitCommit
deploymentTime
environment
```

This helps correlate regressions with releases.

------------------------------------------------------------------------

# 93. Rollback Decision

Rollback application when:

-   new release causes severe regression;
-   security issue introduced;
-   startup/readiness consistently fails.

Do not automatically roll back database migrations unless
designed/tested.

------------------------------------------------------------------------

# 94. Operational Feature Flags

High-risk/dependent capabilities should support kill switches:

``` text
uploads
OCR
Gemini
Ollama
visual AI
large-file processing
```

This allows partial recovery without total shutdown.

------------------------------------------------------------------------

# 95. Operational Support Diagnostics

For a student's reported issue, operations should be able to locate:

``` text
correlationId
material processing state
mission/activity state
provider status
error code
```

without reading private content by default.

------------------------------------------------------------------------

# 96. Student-Facing Error Messages

Translate internal failures.

Example:

Internal:

``` text
AI_PROVIDER_RATE_LIMIT
```

Student:

``` text
AI feedback is temporarily busy. Your answer has been saved. Please try again shortly.
```

------------------------------------------------------------------------

# 97. Preserve Student Work During Failure

Whenever possible:

``` text
persist student input first
↓
perform external AI operation
```

so provider failure does not erase the student's work.

------------------------------------------------------------------------

# 98. Retry UX

Retries must not create:

-   duplicate Attempts;
-   duplicate EvidenceEvents;
-   duplicate jobs;
-   duplicate embeddings.

Operations depends on idempotency defined in prior documents.

------------------------------------------------------------------------

# 99. Operational Data Retention

Logs should have bounded retention.

Long-term retention is not automatically better.

Security and debugging needs must be balanced against student privacy.

------------------------------------------------------------------------

# 100. Local Development Observability

LOCAL should provide:

-   readable console logs;
-   Actuator health;
-   processing-job diagnostics;
-   provider response metadata;
-   SQL diagnostics when deliberately enabled.

Do not require production observability services to develop locally.

------------------------------------------------------------------------

# 101. Pilot Daily Checks

During active pilot periods, quickly review:

``` text
system availability
failed processing jobs
AI provider failures/rate limits
database storage
queue depth
critical security alerts
backup status
```

This may initially be manual.

------------------------------------------------------------------------

# 102. Pilot Weekly Checks

Review:

``` text
quota growth
database growth
R2 growth
AI usage
cold-start impact
processing performance
dependency vulnerabilities
security events
backup restore confidence
```

------------------------------------------------------------------------

# 103. Monthly Infrastructure Review

Ask:

1.  Are free tiers still sufficient?
2.  Are provider terms/quotas unchanged?
3.  Which component is closest to its limit?
4.  Are cold starts harming learning?
5.  Are vulnerability alerts being resolved?
6.  Are backups restorable?
7.  Is observability itself leaking unnecessary data?
8.  Does the 40-user target remain supported by evidence?

------------------------------------------------------------------------

# 104. SLO Boundary

Formal enterprise SLOs are not required for PILOT-FREE.

Still measure practical service objectives:

``` text
successful Study Mission requests
processing success
AI feedback availability
retrieval success
```

Formal SLAs/SLOs may be introduced after validation.

------------------------------------------------------------------------

# 105. What 24 Does Not Replace

This document does not replace:

## Document 15

AI educational evaluation.

## Document 22

Security architecture.

## Document 25

Testing, including active vulnerability testing.

Observability tells us what happens in operation.

Testing deliberately challenges the system before release.

------------------------------------------------------------------------

# 106. MVP Operational Exit Criteria

Before the controlled pilot:

1.  Liveness/readiness endpoints work.
2.  Logs are structured and privacy-safe.
3.  Correlation IDs propagate.
4.  Error codes are stable.
5.  Database storage can be measured.
6.  R2 usage can be monitored.
7.  Render cold starts are understood.
8.  Processing jobs expose state/progress/heartbeat.
9.  Stuck jobs are detectable.
10. AI provider latency/errors/fallback are measured.
11. RAG evidence state is measurable.
12. Source-reference validation failures are observable.
13. Learning-flow consistency failures are detectable.
14. Security events have severity.
15. Dependency vulnerability alerts are monitored.
16. Secret exposure has a rotation procedure.
17. Free-tier quotas have warning thresholds.
18. Backups are monitored.
19. Restore has been tested.
20. Provider/database/storage runbooks exist.
21. Security incident runbook exists.
22. Deployment smoke checks exist.
23. Operational kill switches exist.
24. Student work survives external-provider failure where feasible.
25. Operations can diagnose failures without routinely reading private
    content.

------------------------------------------------------------------------

# 107. Locked v1 Observability & Operations Decisions

The following are approved:

1.  Hippocampus observes learning-system health, not only server uptime.
2.  Observability must remain privacy-minimized.
3.  Spring Boot Actuator + Micrometer form the backend baseline.
4.  Structured application logs are required.
5.  Correlation IDs are required.
6.  Correlation IDs contain no personal/source data.
7.  Stable error taxonomy/codes are required.
8.  Stack traces never reach student-facing responses.
9.  Metrics avoid high-cardinality user/resource labels.
10. Database storage/growth is a first-class PILOT-FREE metric.
11. R2 storage/operation usage is monitored.
12. Render cold starts and service behavior are monitored.
13. Ingestion jobs expose stage, progress, heartbeat, retry, and
    failure.
14. Stuck jobs are detectable.
15. Large-PDF processing progress is observable by stage.
16. RAG retrieval behavior is observable without routinely logging
    source text.
17. Cross-user retrieval anomalies are critical security events.
18. AI calls record provider/model/task/prompt version, latency, status,
    token usage where available, and fallback.
19. Full prompts, source chunks, answers, and provider responses are not
    default log content.
20. Provider `200` responses do not automatically imply successful AI
    behavior.
21. Learning Engine domain invariants are operationally observable.
22. Pilot analytics remain educationally relevant and privacy-safe.
23. Security events have explicit severity.
24. Continuous vulnerability monitoring belongs in Document 24.
25. Active SAST/SCA/DAST/security testing and release gates belong in
    Document 25.
26. Maven/npm/container vulnerability advisories are monitored.
27. GitHub-native/free security tooling is preferred during PILOT-FREE
    when sufficient.
28. Known exploitable Critical vulnerabilities block release unless
    formally risk-accepted.
29. Secret exposure triggers immediate revocation/rotation.
30. Prompt-injection/security validation failures are observable.
31. Alerting prioritizes actionable signals over noise.
32. Simple email/provider/GitHub alerts are sufficient initially.
33. Free-tier quotas are configuration/operational concerns, not
    hard-coded business rules.
34. Capacity exhaustion should be forecast before it occurs.
35. Operational incidents follow detect → classify → contain → diagnose
    → recover → verify → document.
36. Provider outages degrade safely.
37. Database outages do not create alternative fake persistence.
38. R2 outages do not delete source metadata.
39. Durable ingestion state enables stage-level recovery.
40. AI-quality incidents investigate prompt + retrieval + provider
    together.
41. Backup success includes restore verification.
42. Deployment versions/commits are identifiable operationally.
43. High-risk features have kill switches.
44. Student-facing errors are translated from internal error codes.
45. External-provider failures should not erase already-entered student
    work.
46. Operational logs have bounded retention.
47. Daily/weekly pilot checks may initially be manual.
48. Formal enterprise observability infrastructure is deferred.
49. Dedicated SIEM/APM/distributed tracing may be added later without
    redesigning core domains.
50. Observability decisions remain subordinate to Documents 00--23.

------------------------------------------------------------------------

# 108. Out of Scope

This document does not lock:

-   exact external APM vendor
-   exact log aggregation vendor
-   exact SIEM
-   exact alert percentages
-   exact log retention duration
-   exact dashboard implementation
-   formal SLA
-   enterprise on-call rotation
-   formal compliance monitoring
-   exact CVSS acceptance policy
-   SAST/SCA/DAST toolchain
-   penetration-testing vendor

The security testing toolchain is defined in Document 25.

------------------------------------------------------------------------

# 109. Next Document

**25 - Testing Strategy**

The next document should unify:

``` text
Unit Testing
Integration Testing
Architecture Testing
Database Testing
RAG Evaluation
AI Evaluation
Learning Engine Testing
File Ingestion Testing
Frontend Testing
E2E Testing
Performance Testing
Security Testing
Vulnerability Checking
Regression Testing
Release Gates
```

Security testing should explicitly include:

``` text
SAST
SCA / dependency scanning
secret scanning
container/image scanning
DAST/API security testing
IDOR/cross-user tests
CSRF/CORS
XSS
upload attacks
prompt injection
RAG isolation
source-reference forgery
```

It should answer:

> **What evidence must we have before we trust a Hippocampus release
> with real medical students?**

------------------------------------------------------------------------

# 110. Revision History

  ------------------------------------------------------------------------
  Version           Date              Author            Changes
  ----------------- ----------------- ----------------- ------------------
  1.0.0             2026-08-24        Project           Initial finalized
                                      Hippocampus Team  Observability &
                                                        Operations
                                                        architecture
                                                        defining health,
                                                        structured logs,
                                                        metrics,
                                                        RAG/AI/ingestion
                                                        observability,
                                                        free-tier quota
                                                        monitoring,
                                                        security and
                                                        vulnerability
                                                        monitoring,
                                                        incidents,
                                                        backups, runbooks,
                                                        capacity
                                                        forecasting, and
                                                        operational
                                                        release readiness

  ------------------------------------------------------------------------

------------------------------------------------------------------------

# 111. Approval

**Status:** Final

**Reviewed By:** Project Hippocampus Team

**Approved By:** Project Hippocampus Team
