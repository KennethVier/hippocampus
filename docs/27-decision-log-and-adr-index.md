---
Audience: Product, architecture, backend, frontend, AI/RAG, DevOps,
  security, QA, and coding-agent contributors.
Authors: Project Hippocampus Team
Created: 2026-08-24
Document ID: 27
Last Updated: 2026-08-24
Owner: Project Hippocampus Team
Prerequisites:
- README
- 00 through 26 approved Hippocampus v1 documents
Purpose: Provide the governance layer for architectural and product
  decisions in Hippocampus v1, index the major decisions already
  established by the Source-of-Truth documents, define when Architecture
  Decision Records are required, and prevent implementation from
  silently changing approved product, educational, AI, security, data,
  or infrastructure architecture.
Related Documents:
- All Hippocampus v1 Source-of-Truth documents
Scope: Decision authority, decision categories, ADR lifecycle, ADR
  format, decision status, supersession, implementation deviations,
  decision indexing, v1 baseline decisions, deferred decisions, conflict
  resolution, documentation patching, coding-agent governance, and
  Source-of-Truth freeze procedure.
Status: Final
Title: Decision Log / ADR Index
Version: 1.0.0
---

# 27 - Decision Log / ADR Index

## 1. Purpose

Hippocampus now has a substantial set of approved product, educational,
AI, architecture, security, infrastructure, and testing decisions.

This document provides the governance mechanism for those decisions.

It answers:

> **When implementation requires a choice, how do we know whether the
> choice is already decided, whether an ADR is required, and which
> document has authority?**

The purpose of ADRs is not bureaucracy.

The purpose is to prevent this:

``` text
Approved Architecture
        ↓
Implementation Convenience
        ↓
Silent Architectural Change
        ↓
Documentation and Code Diverge
        ↓
Nobody Knows Which Is Correct
```

------------------------------------------------------------------------

# 2. Locked Governance Principle

> **Important decisions must be explicit, traceable, and reversible
> through a documented process---not hidden inside implementation.**

------------------------------------------------------------------------

# 3. Source-of-Truth Authority

The primary authority hierarchy is:

``` text
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
Approved ADRs
        ↓
Code
```

An ADR does not casually override higher-level product or educational
intent.

If an ADR changes an approved Source-of-Truth decision, affected
documents must be patched.

------------------------------------------------------------------------

# 4. Visual Authority

Where a dedicated approved design-system or visual specification
document defines visual behavior, that document remains authoritative
for visual implementation.

An implementation library or component default does not override
approved UX/design requirements.

------------------------------------------------------------------------

# 5. What an ADR Is

An Architecture Decision Record is a small permanent document recording:

``` text
Context
↓
Decision
↓
Alternatives
↓
Rationale
↓
Consequences
↓
Affected Documents
```

An ADR explains **why a meaningful decision was made**.

------------------------------------------------------------------------

# 6. What an ADR Is Not

An ADR is not:

-   a task;
-   a Jira ticket;
-   a commit message;
-   a bug report;
-   a meeting transcript;
-   a general implementation note;
-   documentation for every trivial coding choice.

------------------------------------------------------------------------

# 7. When an ADR Is Required

Create an ADR when a decision:

1.  changes an approved architectural boundary;
2.  introduces/replaces a major technology;
3.  changes persistence strategy;
4.  changes AI-provider architecture;
5.  changes RAG strategy;
6.  changes security architecture;
7.  changes authentication/session strategy;
8.  changes deployment architecture;
9.  introduces a new external infrastructure dependency;
10. changes file-processing architecture;
11. changes learning-policy ownership;
12. changes an important domain model;
13. changes an approved MVP boundary;
14. materially changes operational cost/capacity;
15. deliberately accepts a significant technical/security risk.

------------------------------------------------------------------------

# 8. When an ADR Is Usually Not Required

Examples:

-   rename local variable;
-   extract helper method;
-   choose ordinary component filename;
-   add test fixture;
-   minor CSS adjustment within design rules;
-   refactor implementation without changing contracts;
-   bug fix restoring documented behavior.

------------------------------------------------------------------------

# 9. Decision Classification

Each ADR should be classified as one of:

``` text
PRODUCT
EDUCATION
ARCHITECTURE
DOMAIN
DATA
AI
RAG
SECURITY
FRONTEND
BACKEND
INGESTION
INFRASTRUCTURE
OPERATIONS
TESTING
```

Multiple categories may apply.

------------------------------------------------------------------------

# 10. ADR Status

Allowed statuses:

``` text
PROPOSED
ACCEPTED
SUPERSEDED
REJECTED
DEPRECATED
```

## PROPOSED

Under review.

## ACCEPTED

Approved and authoritative.

## SUPERSEDED

Replaced by another ADR.

## REJECTED

Considered but not adopted.

## DEPRECATED

No longer recommended but retained for historical traceability.

------------------------------------------------------------------------

# 11. ADR Numbering

Use:

``` text
ADR-0001
ADR-0002
ADR-0003
...
```

Numbers are never reused.

------------------------------------------------------------------------

# 12. ADR Filename

Recommended:

``` text
adr/ADR-0001-short-decision-title.md
```

Example:

``` text
adr/ADR-0001-change-vector-embedding-model.md
```

------------------------------------------------------------------------

# 13. ADR Template

``` markdown
---
ADR: ADR-XXXX
Title:
Status: PROPOSED
Date:
Decision Owners:
Categories:
Affected Documents:
Supersedes:
Superseded By:
---

# Context

What problem or architectural pressure requires a decision?

# Decision

What exactly are we deciding?

# Rationale

Why is this the preferred decision?

# Alternatives Considered

## Alternative A

Why rejected?

## Alternative B

Why rejected?

# Consequences

## Positive

## Negative / Tradeoffs

# Security & Privacy Impact

# Educational/Product Impact

# Cost / Infrastructure Impact

# Migration Impact

# Testing Impact

# Documentation Changes Required

# Approval
```

Sections that genuinely do not apply may state:

``` text
Not applicable.
```

------------------------------------------------------------------------

# 14. ADR Decision Quality

A good ADR should answer:

``` text
What changed?
Why?
What alternatives existed?
What do we gain?
What do we give up?
What else must change?
```

It should not merely say:

> We chose X because X is better.

------------------------------------------------------------------------

# 15. ADR Workflow

``` mermaid
flowchart TD

A[Decision Needed]
--> B{Already Defined<br/>by Source of Truth?}

B -->|Yes| C[Follow Existing Decision]
B -->|No| D{Architecturally Significant?}

D -->|No| E[Implement Normally]
D -->|Yes| F[Create PROPOSED ADR]

F --> G[Assess Alternatives + Impact]
G --> H{Approved?}

H -->|No| I[REJECTED]
H -->|Yes| J[ACCEPTED]

J --> K[Patch Affected Source-of-Truth Docs]
K --> L[Implement]
L --> M[Test]
M --> N[Verify Documentation + Code Alignment]
```

------------------------------------------------------------------------

# 16. No Retroactive ADR Rationalization

Do not:

``` text
implement first
↓
write ADR afterward to justify it
```

for planned architectural changes.

Emergency fixes may require retrospective documentation, but this should
be exceptional.

------------------------------------------------------------------------

# 17. Coding-Agent Rule

Coding agents must first determine:

> **Is this decision already defined?**

If yes:

``` text
follow documentation
```

If no and the decision is significant:

``` text
stop
surface decision
propose ADR
```

The coding agent must not silently choose.

------------------------------------------------------------------------

# 18. Conflict Resolution

If documents appear inconsistent:

``` text
Identify conflict
↓
Determine authority level
↓
Check document versions
↓
Check accepted ADRs
↓
Resolve intended decision
↓
Patch affected documents
↓
Continue implementation
```

Do not resolve conflicts by whichever document is easiest to implement.

------------------------------------------------------------------------

# 19. ADR Supersession

When a decision changes:

``` text
Old ADR
Status: SUPERSEDED
Superseded By: ADR-XXXX
```

The new ADR references the old one.

Never delete accepted historical ADRs merely because the architecture
evolved.

------------------------------------------------------------------------

# 20. Rejected Decisions

Rejected ADRs may be retained because they answer:

> Why didn't we use this approach?

This prevents the team from repeatedly reconsidering the same rejected
alternative without new evidence.

------------------------------------------------------------------------

# 21. Decision Log vs ADR

This document contains a **baseline decision index**.

Not every existing decision requires a retroactively created ADR.

The approved v1 documents already provide detailed rationale.

ADRs become especially important for **changes after the v1
Source-of-Truth freeze**.

------------------------------------------------------------------------

# 22. Baseline Decision Index

The following major decisions are already established by Documents
00--26 and form the Hippocampus v1 baseline.

------------------------------------------------------------------------

# 23. Product Decisions

  -----------------------------------------------------------------------
  ID                      Decision                Authority
  ----------------------- ----------------------- -----------------------
  BASE-PROD-001           Hippocampus is          00--05
                          student-centered and    
                          primarily designed for  
                          medical students.       

  BASE-PROD-002           Study Missions are the  04, 06, 07, 09
                          core learning           
                          experience.             

  BASE-PROD-003           Hippocampus is not      09
                          defined as              
                          upload-and-chat, PDF    
                          summarization, Anki     
                          replacement, or quiz    
                          generation.             

  BASE-PROD-004           MVP must become         09, 26
                          genuinely useful to     
                          medical students before 
                          post-v1 expansion.      

  BASE-PROD-005           MVP scope is protected  09, 26
                          from undocumented       
                          expansion during        
                          implementation.         
  -----------------------------------------------------------------------

------------------------------------------------------------------------

# 24. Educational Decisions

  -----------------------------------------------------------------------
  ID                      Decision                Authority
  ----------------------- ----------------------- -----------------------
  BASE-EDU-001            Learning design is      01, 03
                          evidence-informed.      

  BASE-EDU-002            Active retrieval is a   03
                          core learning           
                          mechanism.              

  BASE-EDU-003            Spacing/review over     03, 11
                          time is part of the     
                          learning architecture.  

  BASE-EDU-004            Students should connect 03, 11
                          concepts rather than    
                          memorize isolated       
                          facts.                  

  BASE-EDU-005            Knowledge should be     03, 07, 11
                          applied through         
                          appropriately           
                          scaffolded medical      
                          scenarios.              

  BASE-EDU-006            Early medical students  03
                          can receive             
                          practical/contextual    
                          application before      
                          clerkship without       
                          pretending to provide   
                          real clinical practice. 

  BASE-EDU-007            Feedback is formative   03, 07, 11
                          and should support      
                          learning rather than    
                          merely score answers.   

  BASE-EDU-008            Learning evidence       11, 18, 26
                          should influence later  
                          review and learning     
                          activities.             
  -----------------------------------------------------------------------

------------------------------------------------------------------------

# 25. Learning Engine Decisions

  -----------------------------------------------------------------------
  ID                      Decision                Authority
  ----------------------- ----------------------- -----------------------
  BASE-LE-001             Pedagogical state       11, 16, 19
                          transitions are         
                          deterministic           
                          application/domain      
                          logic.                  

  BASE-LE-002             LLMs do not own         10, 11, 16
                          mastery, review         
                          scheduling, mission     
                          completion, or learning 
                          truth.                  

  BASE-LE-003             Learning evidence is    11, 18
                          multi-dimensional       
                          rather than one opaque  
                          mastery score.          

  BASE-LE-004             Misconceptions require  11, 18
                          evidence rather than    
                          one arbitrary incorrect 
                          response.               

  BASE-LE-005             Anti-repetition and     11
                          scaffolding are         
                          explicit policies.      
  -----------------------------------------------------------------------

------------------------------------------------------------------------

# 26. AI Decisions

  -----------------------------------------------------------------------
  ID                      Decision                Authority
  ----------------------- ----------------------- -----------------------
  BASE-AI-001             AI supports the         10, 11
                          learning system; it     
                          does not define the     
                          product.                

  BASE-AI-002             Hippocampus uses        10, 16, 19
                          provider abstraction.   

  BASE-AI-003             Gemini and Ollama are   10, 16, 17, 23
                          supported remote API    
                          providers for v1.       

  BASE-AI-004             Ollama is not assumed   10, 16, 23
                          to run locally in the   
                          deployed architecture.  

  BASE-AI-005             Prompt templates are    12
                          versioned.              

  BASE-AI-006             AI tasks use structured 10, 12, 19
                          contracts and           
                          validation.             

  BASE-AI-007             Provider fallback must  10, 24, 25
                          preserve task,          
                          grounding, and          
                          validation contracts.   

  BASE-AI-008             Live provider use       25, 26
                          during tests is         
                          quota-aware.            
  -----------------------------------------------------------------------

------------------------------------------------------------------------

# 27. RAG Decisions

  -----------------------------------------------------------------------
  ID                      Decision                Authority
  ----------------------- ----------------------- -----------------------
  BASE-RAG-001            RAG is source-grounded  13, 14
                          and preserves           
                          provenance.             

  BASE-RAG-002            Retrieval uses hybrid   13, 17, 19
                          lexical + vector        
                          techniques.             

  BASE-RAG-003            PostgreSQL + pgvector   13, 17, 18
                          is the v1 vector        
                          foundation.             

  BASE-RAG-004            PostgreSQL FTS and      13, 17
                          pg_trgm supplement      
                          semantic retrieval.     

  BASE-RAG-005            Ownership scoping       13, 22
                          occurs before semantic  
                          ranking.                

  BASE-RAG-006            Cross-user retrieval    22, 25
                          leakage tolerance is    
                          zero.                   

  BASE-RAG-007            Source references are   13, 22, 25
                          validated and cannot be 
                          trusted merely because  
                          an LLM emitted them.    

  BASE-RAG-008            RAG must be             26
                          inspectable/testable    
                          before AI generation is 
                          introduced.             
  -----------------------------------------------------------------------

------------------------------------------------------------------------

# 28. Knowledge Base Decisions

  -----------------------------------------------------------------------
  ID                      Decision                Authority
  ----------------------- ----------------------- -----------------------
  BASE-KB-001             Large documents are     13, 14, 21
                          represented             
                          hierarchically rather   
                          than as one flat text   
                          blob.                   

  BASE-KB-002             Material versions       14, 18, 21
                          preserve                
                          processing/retrieval    
                          provenance.             

  BASE-KB-003             Text chunks retain      13, 14, 18
                          source hierarchy/page   
                          relationships.          

  BASE-KB-004             Visual assets retain    14, 18, 21
                          provenance and          
                          contextual              
                          relationships.          

  BASE-KB-005             Inactive/deleted        13, 22, 25
                          material versions are   
                          excluded from           
                          retrieval.              
  -----------------------------------------------------------------------

------------------------------------------------------------------------

# 29. Ingestion Decisions

  -----------------------------------------------------------------------
  ID                      Decision                Authority
  ----------------------- ----------------------- -----------------------
  BASE-ING-001            Ingestion is            21
                          asynchronous and        
                          stage-based.            

  BASE-ING-002            Large 600+ page PDFs    13, 21
                          are within the v1       
                          architecture.           

  BASE-ING-003            Processing uses bounded 21
                          memory and batching.    

  BASE-ING-004            Durable intermediate    21, 24
                          state supports          
                          resume/retry.           

  BASE-ING-005            Processing jobs expose  21, 24
                          progress and heartbeat. 

  BASE-ING-006            Mixed text/image PDFs   21
                          and scanned documents   
                          are supported within    
                          defined MVP             
                          capabilities.           

  BASE-ING-007            Visual extraction is    21
                          part of the ingestion   
                          architecture.           

  BASE-ING-008            Original binaries are   18, 21, 23
                          not stored in           
                          PostgreSQL.             
  -----------------------------------------------------------------------

------------------------------------------------------------------------

# 30. Backend Decisions

  -----------------------------------------------------------------------------------
  ID                      Decision                            Authority
  ----------------------- ----------------------------------- -----------------------
  BASE-BE-001             Backend uses Java + Spring Boot.    17, 19

  BASE-BE-002             Domain/application/infrastructure   16, 19
                          boundaries are explicit.            

  BASE-BE-003             Controllers do not directly own     19
                          domain/persistence logic.           

  BASE-BE-004             Flyway owns schema migration.       18, 19

  BASE-BE-005             Hibernate schema auto-update is not 18, 25
                          the production schema strategy.     

  BASE-BE-006             External providers are accessed     16, 19
                          through ports/adapters.             
  -----------------------------------------------------------------------------------

------------------------------------------------------------------------

# 31. Frontend Decisions

  -----------------------------------------------------------------------
  ID                      Decision                Authority
  ----------------------- ----------------------- -----------------------
  BASE-FE-001             Frontend uses React +   17, 20
                          Vite + TypeScript.      

  BASE-FE-002             Server state uses       20
                          TanStack Query.         

  BASE-FE-003             Zustand is used only    20
                          where appropriate for   
                          client/UI state.        

  BASE-FE-004             React Hook Form + Zod   17, 20
                          handle form/validation  
                          patterns.               

  BASE-FE-005             Student-facing AI       20, 22
                          output is treated as    
                          untrusted content and   
                          safely rendered.        

  BASE-FE-006             Core learning flows     08, 20, 25
                          target accessible       
                          responsive behavior.    
  -----------------------------------------------------------------------

------------------------------------------------------------------------

# 32. Data Decisions

  -----------------------------------------------------------------------
  ID                      Decision                Authority
  ----------------------- ----------------------- -----------------------
  BASE-DATA-001           PostgreSQL is the       17, 18
                          relational source of    
                          truth.                  

  BASE-DATA-002           pgvector resides in the 17, 18
                          same PostgreSQL         
                          foundation for v1.      

  BASE-DATA-003           Persistent schema is    18
                          explicitly designed and 
                          migrated.               

  BASE-DATA-004           Learning evidence is    11, 18
                          traceable to underlying 
                          events/attempts.        

  BASE-DATA-005           Optimistic locking      18, 25
                          protects relevant       
                          concurrent learning     
                          state.                  

  BASE-DATA-006           Object binaries are     18, 23
                          separated from          
                          relational metadata.    
  -----------------------------------------------------------------------

------------------------------------------------------------------------

# 33. Security Decisions

  --------------------------------------------------------------------------------
  ID                      Decision                         Authority
  ----------------------- -------------------------------- -----------------------
  BASE-SEC-001            Student learning materials are   22
                          private by default.              

  BASE-SEC-002            Authorization is server-side and 22
                          ownership-aware.                 

  BASE-SEC-003            Server-side session              22
                          authentication is used for v1.   

  BASE-SEC-004            Cookie security, CSRF, and       22
                          restricted CORS are required.    

  BASE-SEC-005            API/provider secrets never reach 22
                          frontend bundles.                

  BASE-SEC-006            Uploaded/source/AI-generated     22
                          content is untrusted.            

  BASE-SEC-007            Prompt injection does not grant  22
                          authority.                       

  BASE-SEC-008            Security testing includes        25
                          SAST/SCA/secret/container/DAST   
                          and authorization testing.       

  BASE-SEC-009            Exploitable Critical             24, 25
                          vulnerabilities block release    
                          unless formally risk-accepted.   
  --------------------------------------------------------------------------------

------------------------------------------------------------------------

# 34. Infrastructure Decisions

  -----------------------------------------------------------------------
  ID                      Decision                Authority
  ----------------------- ----------------------- -----------------------
  BASE-INFRA-001          v1 pilot follows a      23
                          free-first deployment   
                          strategy.               

  BASE-INFRA-002          Deployment profile is   23
                          named PILOT-FREE.       

  BASE-INFRA-003          Vercel hosts the        23
                          frontend under the      
                          approved pilot          
                          constraints.            

  BASE-INFRA-004          Render hosts the Spring 23
                          Boot backend under the  
                          approved pilot          
                          constraints.            

  BASE-INFRA-005          Neon hosts PostgreSQL + 23
                          pgvector.               

  BASE-INFRA-006          Cloudflare R2 stores    23
                          source/derived binary   
                          objects.                

  BASE-INFRA-007          Free-tier capacity is   23, 24, 25
                          measured, not assumed.  

  BASE-INFRA-008          The controlled pilot    23, 26
                          targets approximately   
                          up to 40 invited        
                          medical students        
                          subject to validated    
                          capacity/quotas.        
  -----------------------------------------------------------------------

------------------------------------------------------------------------

# 35. Observability Decisions

  -----------------------------------------------------------------------
  ID                      Decision                Authority
  ----------------------- ----------------------- -----------------------
  BASE-OBS-001            Observe the learning    24
                          system, not only server 
                          uptime.                 

  BASE-OBS-002            Structured              24
                          privacy-minimized logs  
                          are required.           

  BASE-OBS-003            Correlation IDs are     24
                          required.               

  BASE-OBS-004            Ingestion, RAG, AI,     24
                          learning flow,          
                          security, and           
                          infrastructure quotas   
                          are observable.         

  BASE-OBS-005            Full prompts/source     24
                          chunks/student answers  
                          are not default log     
                          content.                

  BASE-OBS-006            Vulnerability           24
                          monitoring is           
                          operationally           
                          continuous.             
  -----------------------------------------------------------------------

------------------------------------------------------------------------

# 36. Testing Decisions

  -----------------------------------------------------------------------
  ID                      Decision                Authority
  ----------------------- ----------------------- -----------------------
  BASE-TEST-001           Fast deterministic      25
                          tests form the majority 
                          of the suite.           

  BASE-TEST-002           PostgreSQL + pgvector   25
                          integration tests use   
                          Testcontainers.         

  BASE-TEST-003           Architecture boundaries 25
                          are automatically       
                          tested.                 

  BASE-TEST-004           RAG has golden          15, 25
                          retrieval evaluation.   

  BASE-TEST-005           AI tasks have versioned 15, 25
                          evaluation              
                          datasets/rubrics.       

  BASE-TEST-006           Large PDF processing is 25
                          tested before pilot.    

  BASE-TEST-007           Cross-user leakage      25
                          tests must show zero    
                          leakage.                

  BASE-TEST-008           Load testing includes   25
                          realistic 10/20/40-user 
                          profiles.               

  BASE-TEST-009           Backup restore is       24, 25
                          tested, not merely      
                          backup creation.        
  -----------------------------------------------------------------------

------------------------------------------------------------------------

# 37. Roadmap Decisions

  --------------------------------------------------------------------------------
  ID                      Decision                         Authority
  ----------------------- -------------------------------- -----------------------
  BASE-ROAD-001           Implementation follows Phases    26
                          0--12.                           

  BASE-ROAD-002           RAG is implemented before        26
                          generative AI integration.       

  BASE-ROAD-003           Learning Engine precedes         26
                          complete Study Mission           
                          orchestration.                   

  BASE-ROAD-004           Study Missions become the first  26
                          complete product milestone at    
                          M3.                              

  BASE-ROAD-005           Security/testing/observability   26
                          are continuous across phases.    

  BASE-ROAD-006           Controlled pilot begins only     26
                          after Document 25 gates pass.    
  --------------------------------------------------------------------------------

------------------------------------------------------------------------

# 38. Baseline Decisions Are Not ADR Files

The `BASE-*` entries above are an index of already approved v1
decisions.

They do not need individual retrospective ADR files because the
reasoning exists in Documents 00--26.

After documentation freeze, significant changes should normally use
ADRs.

------------------------------------------------------------------------

# 39. Deferred Decisions Register

The following categories intentionally remain open until implementation
evidence requires them:

``` text
exact embedding model/version
exact reranker implementation
exact OCR library/provider
exact PDF parser implementation
exact AI routing thresholds
exact observability vendor
exact SAST/DAST tooling combination
exact numeric load/SLO thresholds
exact log retention duration
exact production paid-tier migration timing
```

These are not missing architecture.

They are intentionally deferred implementation decisions.

------------------------------------------------------------------------

# 40. Deferred Decision Rule

A deferred decision may be made without changing product scope if it
stays inside existing architectural boundaries.

Example:

``` text
Choose PDF parser A vs B
```

may be implementation-level.

But:

``` text
Move all document processing to an external proprietary ingestion SaaS
```

is architectural and requires ADR review.

------------------------------------------------------------------------

# 41. Evidence-Driven Decisions

When possible, use benchmarks/evaluation.

Examples:

## Embedding Model

Compare:

``` text
retrieval quality
storage
latency
cost/quota
```

## OCR

Compare:

``` text
medical-text accuracy
layout preservation
speed
memory
```

## Provider Routing

Compare:

``` text
quality
latency
quota
schema reliability
```

------------------------------------------------------------------------

# 42. Security Risk Acceptance

If a significant vulnerability cannot immediately be fixed, formal risk
acceptance must record:

``` text
finding
severity
exploitability
affected capability
mitigation
reason for acceptance
expiry/review date
owner
```

Critical exploitable vulnerabilities remain release blockers unless an
exceptional formally approved decision is made.

------------------------------------------------------------------------

# 43. Cost Decision Trigger

Create an ADR when moving from PILOT-FREE to paid infrastructure if the
change materially affects architecture or provider strategy.

Simple same-service tier upgrades may only require an
operational/deployment update if architecture remains unchanged.

------------------------------------------------------------------------

# 44. Provider Change Trigger

Adding/replacing an AI provider requires an ADR if it changes:

-   provider abstraction;
-   privacy posture;
-   data residency;
-   task contracts;
-   cost model;
-   fallback architecture.

A provider that simply implements the existing contract may require a
smaller decision depending on impact.

------------------------------------------------------------------------

# 45. Database Change Trigger

Moving from:

``` text
PostgreSQL + pgvector
```

to a dedicated vector database requires ADR.

Changing a PostgreSQL index configuration generally does not unless
architectural impact is significant.

------------------------------------------------------------------------

# 46. Authentication Change Trigger

Changing:

``` text
server-side sessions
```

to:

``` text
JWT-based stateless authentication
```

requires ADR and patches to security/backend/frontend architecture.

------------------------------------------------------------------------

# 47. Learning Policy Change Trigger

Changing deterministic Learning Engine ownership to LLM-directed
sequencing would be a major product/educational architecture change.

It cannot be made as an ordinary engineering ADR alone.

It requires review against:

``` text
01
03
09
11
16
```

and corresponding patches if approved.

------------------------------------------------------------------------

# 48. MVP Scope Change Trigger

Adding a significant new feature to v1 requires:

``` text
Problem alignment
Educational alignment
Product requirement update
MVP scope update
Architecture impact review
Roadmap update
```

An ADR alone cannot silently expand MVP.

------------------------------------------------------------------------

# 49. Documentation Patch Rule

When an accepted ADR changes an existing decision:

1.  Update ADR status to ACCEPTED.
2.  Identify affected documents.
3.  Patch those documents.
4.  Increment document versions appropriately.
5.  Add revision-history entries.
6.  Update this Decision Log index if the baseline decision materially
    changes.
7.  Implement.
8.  Test.

------------------------------------------------------------------------

# 50. Code-to-Decision Traceability

Where useful, code comments may reference ADR numbers.

Example:

``` java
// ADR-0007: retrieval ownership filtering occurs before vector ranking.
```

Do this only for non-obvious architectural decisions.

Do not litter ordinary code with documentation references.

------------------------------------------------------------------------

# 51. Pull Request Decision Check

PR review should ask:

``` text
Does this change alter an approved decision?
```

If yes:

``` text
Where is the ADR/document update?
```

------------------------------------------------------------------------

# 52. Definition of Architectural Drift

Architectural drift occurs when code:

-   violates module boundaries;
-   introduces an unapproved dependency;
-   bypasses domain policy;
-   changes persistence semantics;
-   bypasses ownership checks;
-   changes AI authority;
-   expands product scope;
-   changes infrastructure assumptions;

without corresponding approved documentation.

Architectural drift is a defect.

------------------------------------------------------------------------

# 53. Decision Review Cadence

No recurring architecture meeting is required for the small v1 team.

Review decisions when:

-   implementation reaches a genuine unresolved choice;
-   a baseline assumption proves false;
-   provider limits change;
-   pilot evidence challenges architecture;
-   security requires change.

------------------------------------------------------------------------

# 54. Post-Pilot ADRs

Pilot evidence may produce ADRs for:

``` text
infrastructure scaling
retrieval changes
provider routing
learning-policy refinement
new source types
storage strategy
review scheduling
```

Product/educational changes must still be grounded in evidence and
corresponding product documentation.

------------------------------------------------------------------------

# 55. Initial ADR Directory

At freeze:

``` text
/docs
  /adr
    README.md
```

No artificial ADRs need to be created merely to populate the directory.

The baseline decision index in this document is sufficient for
pre-freeze decisions.

------------------------------------------------------------------------

# 56. ADR Index Maintenance

When ADRs exist, add an index:

  ADR        Title              Status     Date         Categories
  ---------- ------------------ ---------- ------------ --------------
  ADR-0001   Example Decision   ACCEPTED   YYYY-MM-DD   ARCHITECTURE

This table begins empty at initial v1 freeze unless a real pending
decision already exists.

------------------------------------------------------------------------

# 57. ADR Directory README

The ADR README should briefly state:

``` text
Architecture Decision Records capture significant decisions made after
the Hippocampus v1 Source-of-Truth baseline.

Before creating an ADR, check Documents 00–27 to determine whether the
decision has already been made.

Accepted ADRs that change the Source of Truth require corresponding
documentation patches.
```

------------------------------------------------------------------------

# 58. Source-of-Truth Freeze Procedure

After Document 27 is approved:

``` text
README + 00–27
↓
Consistency Audit
↓
Conflict Resolution
↓
Version/Metadata Audit
↓
Reference Audit
↓
Architecture Dependency Audit
↓
MVP Scope Audit
↓
Security Audit
↓
Roadmap Alignment Audit
↓
Patch
↓
FINAL REVIEW
↓
HIPPOCAMPUS v1.0 SOURCE OF TRUTH
```

------------------------------------------------------------------------

# 59. Final Consistency Audit Checklist

The audit must verify at minimum:

1.  Product mission is consistent.
2.  Primary persona remains medical students.
3.  MVP scope is consistent.
4.  Educational principles align with features.
5.  Study Missions remain central.
6.  Learning Engine authority is consistent.
7.  AI provider strategy consistently reflects Gemini + remote Ollama
    APIs.
8.  RAG architecture is consistent.
9.  Large-PDF handling is consistent.
10. Database entities match architecture.
11. Backend modules match domain.
12. Frontend contracts match backend.
13. security rules are consistent.
14. deployment matches technical stack.
15. observability matches deployed components.
16. testing covers documented risks.
17. roadmap respects dependencies.
18. document versions/references are valid.
19. no superseded decisions remain accidentally authoritative.
20. no undocumented MVP features appear in downstream technical
    documents.

------------------------------------------------------------------------

# 60. Freeze Meaning

"Frozen" does not mean documents can never change.

It means:

> **Implementation must treat the approved documentation as
> authoritative until a deliberate documented decision changes it.**

------------------------------------------------------------------------

# 61. Implementation Tracker After Freeze

After freeze, create a separate implementation tracker.

The tracker should contain:

``` text
Phase
Workstream
Task
Status
Dependencies
Tests
ADR
Notes
```

The tracker reports implementation progress.

It does not redefine architecture.

------------------------------------------------------------------------

# 62. Decision Log vs Implementation Tracker

``` text
Decision Log
= Why are we doing it this way?

Implementation Tracker
= Have we built it yet?
```

These responsibilities must remain separate.

------------------------------------------------------------------------

# 63. Decision Log vs Development Roadmap

``` text
26 Roadmap
= In what order do we build?

27 Decision Log
= How do we govern architectural choices and changes?
```

------------------------------------------------------------------------

# 64. Decision Log vs Source-of-Truth Documents

``` text
00–26
= Approved system definition

27
= Governance/index over those decisions

ADR
= Approved future change/clarification

Code
= Implementation
```

------------------------------------------------------------------------

# 65. Locked v1 Decision-Governance Decisions

The following are approved:

1.  Important architectural decisions must be explicit and traceable.
2.  Documents 00--15 remain product/educational authority.
3.  Documents 16--25 remain technical authority.
4.  Document 26 defines implementation order.
5.  Document 27 defines decision governance.
6.  Code does not silently override documentation.
7.  Accepted ADRs may change architecture only through documented impact
    review.
8.  Product/educational intent cannot be casually overridden by a
    technical ADR.
9.  ADRs use sequential `ADR-XXXX` numbering.
10. ADR numbers are never reused.
11. ADR statuses are PROPOSED, ACCEPTED, SUPERSEDED, REJECTED, or
    DEPRECATED.
12. Accepted historical ADRs are retained.
13. Superseded ADRs reference their replacement.
14. Rejected ADRs may remain as historical rationale.
15. Not every coding choice requires an ADR.
16. Major technology/provider/security/data changes normally require
    ADRs.
17. MVP expansion requires product-scope review, not merely an ADR.
18. Learning-policy authority changes require educational/product
    review.
19. Baseline `BASE-*` decisions index existing v1 decisions without
    requiring retroactive ADR files.
20. Significant post-freeze changes normally use ADRs.
21. Deferred implementation decisions are intentional, not documentation
    gaps.
22. Evidence/benchmarks should inform implementation choices where
    appropriate.
23. Security risk acceptance must be explicit and time-bounded where
    appropriate.
24. Documentation affected by an ADR must be patched.
25. Document versions/revision histories must reflect accepted changes.
26. Pull requests should identify architectural changes.
27. Undocumented architectural drift is treated as a defect.
28. Coding agents must stop rather than silently make significant
    unresolved architectural decisions.
29. The ADR directory begins empty unless a genuine decision requires an
    ADR.
30. The final consistency audit occurs after Document 27.
31. The consistency audit covers product, education, AI, RAG, data,
    security, deployment, testing, and roadmap alignment.
32. The final approved set becomes the Hippocampus v1.0 Source of Truth.
33. Freeze means authoritative, not immutable.
34. An implementation tracker is created after freeze.
35. The implementation tracker tracks progress and cannot redefine
    architecture.

------------------------------------------------------------------------

# 66. Out of Scope

This document does not define:

-   implementation tickets;
-   sprint scheduling;
-   branch strategy;
-   code-review approval count;
-   release calendar;
-   future v2 decisions;
-   organization-wide architecture governance.

It governs Hippocampus v1 decision traceability.

------------------------------------------------------------------------

# 67. Next Step

This is the final planned v1 governance document.

The next action is not another architecture document.

The next action is:

> **Final README + Documents 00--27 consistency audit.**

After the audit:

``` text
Patch inconsistencies
↓
Final review
↓
Freeze Hippocampus v1.0 Source of Truth
↓
Create implementation tracker
↓
Phase 0 — Engineering Foundation
```

------------------------------------------------------------------------

# 68. Revision History

  ----------------------------------------------------------------------------
  Version           Date              Author            Changes
  ----------------- ----------------- ----------------- ----------------------
  1.0.0             2026-08-24        Project           Initial finalized
                                      Hippocampus Team  Decision Log / ADR
                                                        Index defining
                                                        authority hierarchy,
                                                        ADR lifecycle and
                                                        template, baseline v1
                                                        decision index,
                                                        deferred decisions,
                                                        architectural-drift
                                                        handling,
                                                        Source-of-Truth
                                                        patching, coding-agent
                                                        governance, and final
                                                        documentation-freeze
                                                        procedure

  ----------------------------------------------------------------------------

------------------------------------------------------------------------

# 69. Approval

**Status:** Final

**Reviewed By:** Project Hippocampus Team

**Approved By:** Project Hippocampus Team
