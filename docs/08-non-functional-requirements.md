---
Document ID: 08
Title: Non-Functional Requirements
Version: 1.1.0
Status: Final
Owner: Project Hippocampus Team
Authors: Project Hippocampus Team
Created: 2026-08-23
Last Updated: 2026-08-24
Purpose: Define the quality attributes, constraints, and measurable expectations Hippocampus must satisfy independently of specific implementation technologies.
Scope: Reliability, performance, security, privacy, accessibility, usability, AI quality, groundedness, data integrity, file-processing behavior, maintainability, observability, failure handling, portability, scalability, and compliance-oriented design constraints.
Audience: Product, UX, engineering, AI, QA, security, and architecture contributors.
Prerequisites:
  - README
  - 00 - Project Vision
  - 01 - Guiding Principles
  - 02 - Problem Statement
  - 03 - Educational Foundation
  - 04 - Product Requirements
  - 05 - User Personas
  - 06 - User Journey & Learning Flow
  - 07 - Feature Specifications
Related Documents:
  - 09 - MVP Scope & Roadmap
  - 10 - AI Architecture
  - 11 - AI Learning Engine
  - 12 - Prompt Engineering Strategy
  - 13 - RAG Architecture
  - 14 - Knowledge Base Design
  - 15 - AI Evaluation Strategy
---

# 08 - Non-Functional Requirements

## 1. Purpose

This document defines the non-functional requirements of Project Hippocampus.

It answers:

> **How well must Hippocampus behave while delivering the approved product capabilities?**

These requirements describe the expected quality of the product and system.

They do not select specific frameworks, models, databases, infrastructure, or implementation patterns.

---

# 2. Non-Functional Design Principles

All quality requirements should follow these principles.

## 2.1 Educational Quality Is a System Quality

A technically available response is not sufficient if it is educationally misleading, ungrounded, or inappropriate to learner level.

## 2.2 Transparency Over Silent Failure

When the system cannot perform reliably, it should communicate the limitation rather than simulate success.

## 2.3 Privacy and Security Are Default Requirements

They are not optional later-stage enhancements.

## 2.4 Performance Should Support Learning Flow

Latency should not unnecessarily interrupt concentration or make guided study feel fragmented.

## 2.5 Reliability Includes Continuity

Student progress, learning evidence, and study history should not be lost because of avoidable failures.

## 2.6 Accessibility Is Part of Product Quality

A student should not be excluded from core learning flows because of avoidable interface or content barriers.

## 2.7 Measurable Where Practical

Requirements should become testable acceptance targets when architecture and implementation details are defined.

---

# 3. NFR Classification

| Category | ID Range |
|---|---|
| Reliability & Availability | NFR-01 to NFR-05 |
| Performance & Responsiveness | NFR-06 to NFR-10 |
| AI Quality & Groundedness | NFR-11 to NFR-18 |
| Privacy & Security | NFR-19 to NFR-26 |
| Data Integrity & Continuity | NFR-27 to NFR-31 |
| File & Multimodal Processing | NFR-32 to NFR-36 |
| Usability & Cognitive Load | NFR-37 to NFR-41 |
| Accessibility | NFR-42 to NFR-46 |
| Maintainability & Modularity | NFR-47 to NFR-51 |
| Observability & Auditability | NFR-52 to NFR-56 |
| Portability & Deployment Flexibility | NFR-57 to NFR-59 |
| Scalability & Resource Management | NFR-60 to NFR-63 |

---

# 4. Reliability & Availability

## NFR-01 — Core Learning Flow Reliability

The primary Study Mission flow should remain usable under normal operating conditions.

A failure in one optional learning activity should not unnecessarily terminate the entire Study Mission when a safe fallback exists.

### Example

If visual analysis is unavailable:

```text
Visual Activity Failed
        ↓
Communicate Limitation
        ↓
Continue With Supported Non-Visual Activities
```

provided that doing so does not misrepresent lost visual information.

---

## NFR-02 — Graceful Degradation

The system should degrade transparently when a capability is unavailable.

Examples:

- Image interpretation unavailable
- Supplemental AI unavailable
- Partial source extraction
- Review recommendation unavailable because of insufficient evidence

The fallback must not pretend to preserve capabilities that were actually lost.

---

## NFR-03 — Recoverable Session Failure

A recoverable system error should not require the student to restart an entire completed or partially completed Study Mission whenever sufficient state exists to resume.

---

## NFR-04 — Safe Retry Behavior

Retries should avoid:

- Duplicate submissions
- Duplicate learning evidence
- Duplicate uploaded material
- Duplicate review events
- Replaying irreversible actions

where product semantics make duplication undesirable.

---

## NFR-05 — Availability Targets

Specific uptime targets will be defined during deployment planning.

The MVP should prioritize dependable core learning functionality over high-availability complexity that is disproportionate to actual usage.

---

# 5. Performance & Responsiveness

## NFR-06 — Interactive UI Responsiveness

Normal local interface interactions should provide immediate perceptual feedback.

Long-running operations should expose clear progress or state rather than appearing frozen.

---

## NFR-07 — AI Response Latency

AI-assisted activities should respond quickly enough to preserve learning flow.

Different activity types may have different acceptable latency.

For example:

- Simple explanation: lower target latency
- Complex case generation: higher acceptable latency
- Large material processing: asynchronous/progress-based experience may be appropriate

Exact numerical service-level targets belong in architecture and performance testing.

---

## NFR-08 — Streaming or Progressive Feedback

Where AI generation takes noticeable time, the experience should provide progressive feedback when safe and appropriate rather than waiting silently for an entire response.

---

## NFR-09 — Material Processing Feedback

Large or complex material processing must expose status.

Conceptual states:

```text
Queued
Processing
Partially Ready
Ready
Failed
```

The student should not need to guess whether processing is still occurring.

---

## NFR-10 — Time-Aware Mission Responsiveness

Time-constrained Study Missions should not spend a disproportionate part of the student's available study period on setup or unnecessary generation.

---

# 6. AI Quality & Groundedness

## NFR-11 — Source Fidelity

When Hippocampus claims that information originates from user-provided material, the content must be supported by that material.

Generated supplementation must not be mislabeled as source-derived content.

---

## NFR-12 — Grounded Answer Preference

When a learning interaction is intended to be based on uploaded material, the system should prefer evidence grounded in the available source context.

If the source is insufficient, the system should state that limitation or clearly identify supplemental knowledge.

---

## NFR-13 — Hallucination-Aware Behavior

The product must be designed under the assumption that generative AI can produce incorrect content.

It should therefore include product-level safeguards such as:

- Source grounding where appropriate
- Explicit uncertainty
- Validation workflows where feasible
- Restrictions against fabricated source claims
- Evaluation of generated educational content

---

## NFR-14 — Uncertainty Communication

The system should communicate meaningful uncertainty when:

- Source interpretation is incomplete
- The available context is insufficient
- The model cannot reliably determine an answer
- Visual interpretation is uncertain
- Multiple medically plausible interpretations exist

The system should not expose arbitrary numerical confidence scores unless those scores have a validated interpretation.

---

## NFR-15 — Educational Appropriateness

Generated explanations, questions, and cases should be appropriate to:

- Topic
- Learner level
- Current Study Mission stage
- Available learning evidence
- Educational objective

A factually correct response may still fail this requirement if it is pedagogically inappropriate.

---

## NFR-16 — Generated Question Quality

Generated assessment items should aim to avoid:

- Ambiguous wording
- Unsupported answers
- Multiple unintentionally correct answers
- Trivia unrelated to learning objectives
- Repeated near-duplicate questions
- Explanations inconsistent with the expected answer

---

## NFR-17 — Generated Case Quality

Clinical or practical scenarios should:

- Remain educational rather than diagnostic advice
- Match learner readiness
- Have sufficient information for the intended reasoning task
- Avoid unnecessary clinical complexity
- Connect to the learning objective
- Avoid implying real clinical competence

---

## NFR-18 — AI Evaluation Requirement

AI-dependent functionality must be evaluated before being considered production-ready.

Evaluation should include, where applicable:

- Accuracy
- Groundedness
- Source fidelity
- Educational usefulness
- Question quality
- Case quality
- Safety
- Latency
- Failure behavior

Detailed evaluation methodology belongs to **15 - AI Evaluation Strategy**.

---

# 7. Privacy & Security

## NFR-19 — Privacy by Design

Student learning data and uploaded materials must be handled with strong privacy and security protections from the beginning of system design.

---

## NFR-20 — Data Minimization

The system should collect and retain only data required for legitimate product functionality.

Future analytics should not automatically justify collecting unnecessary personal information.

---

## NFR-21 — Uploaded Material Protection

Student-provided materials must not be unintentionally exposed to other users.

Access controls must preserve user isolation where accounts or multi-user environments exist.

---

## NFR-22 — Sensitive Data Warning

Because students may upload inappropriate or sensitive material unintentionally, the product should communicate acceptable-use expectations.

The product is not intended for uploading identifiable patient records or protected clinical information.

---

## NFR-23 — Secrets Protection

Credentials, API keys, service secrets, model configuration secrets, and other sensitive system values must not be exposed to clients or committed into source code.

---

## NFR-24 — Secure File Handling

Uploaded files must be treated as untrusted input.

The system should validate supported formats, size constraints, and file characteristics before processing.

Implementation-specific malware and content-safety controls belong to the security architecture.

---

## NFR-25 — Authentication and Authorization Quality

Where authentication is used, authorization must ensure that one user cannot access another user's private materials, study history, or learning evidence.

Exact authentication technology is deferred.

---

## NFR-26 — Deletion and Retention Behavior

When the product supports deletion of learning material or user data, expected deletion/retention behavior should be explicit and consistent.

Technical retention policies will be defined later.

---

# 8. Data Integrity & Continuity

## NFR-27 — Learning Evidence Integrity

Learning evidence must not be silently duplicated, corrupted, or associated with the wrong student/topic/session.

---

## NFR-28 — Progress Integrity

Mission progress should remain consistent across normal navigation, refreshes, supported retries, and resumptions.

---

## NFR-29 — Review Integrity

Review scheduling or recommendation logic must use valid learning evidence and avoid creating contradictory review states.

---

## NFR-30 — Source Association Integrity

Learning material must remain correctly associated with its intended subject/topic unless explicitly reorganized by the student.

---

## NFR-31 — Recoverability

Critical user learning state should have an appropriate recovery strategy proportionate to the deployment model.

Exact backup and restore mechanisms belong to architecture/deployment documents.

---

# 9. File & Multimodal Processing

## NFR-32 — File Validation

Unsupported, corrupted, encrypted, or otherwise unusable material should fail clearly.

The system should avoid silently processing partial or invalid input as if complete.

---

## NFR-33 — Processing Traceability

The product should retain enough processing status to explain whether material is:

- Ready
- Partial
- Failed
- Unsupported

---

## NFR-34 — Visual Preservation

When educationally important visual information exists, the processing pipeline should preserve it where technically supported.

A successful text extraction does not automatically mean the full educational material was successfully processed.

---

## NFR-35 — Partial Processing Transparency

When only part of a document can be interpreted, the product should identify the limitation in a learner-understandable way.

---

## NFR-36 — Resource Limits

The product must support explicit limits for:

- File size
- Page count
- Image resolution
- Media duration
- Concurrent processing
- Total retained storage

Exact values belong in MVP and technical planning.

---

# 10. Usability & Cognitive Load

## NFR-37 — Guided Primary Navigation

Primary navigation should emphasize stable learning contexts rather than expose every learning mechanism as a separate destination.

---

## NFR-38 — Decision Burden

The product should minimize unnecessary choices before the student can begin studying.

If the system can safely infer a reasonable default, it should avoid asking the student to configure technical or educational details they do not need to manage.

---

## NFR-39 — Focused Study Experience

During an active Study Mission, unnecessary navigation, controls, and distractions should be minimized.

---

## NFR-40 — Consistent Interaction Patterns

Similar activities should use consistent interaction behavior across subjects unless the learning task requires otherwise.

---

## NFR-41 — Clear System State

The student should be able to understand:

- What is happening now
- What is expected from them
- Whether processing is occurring
- Whether an activity is complete
- What happens next

---

# 11. Accessibility

## NFR-42 — Keyboard Accessibility

Core learning flows should be operable using a keyboard where technically applicable.

---

## NFR-43 — Semantic Interface Structure

Interactive controls and content should use meaningful semantic structure compatible with assistive technologies.

---

## NFR-44 — Text Alternatives

Important non-text content should have appropriate textual alternatives when doing so does not undermine the learning objective.

For assessment tasks where identifying a visual is itself the learning objective, accessibility behavior may require specialized design rather than simply exposing the answer.

---

## NFR-45 — Readability

Text should support readable typography, appropriate contrast, scalable layouts, and responsive presentation.

---

## NFR-46 — Accessibility Target

The product should target contemporary WCAG guidance appropriate to a modern educational web application.

Exact conformance level should be selected and documented during UX/non-functional validation.

---

# 12. Maintainability & Modularity

## NFR-47 — Separation of Concerns

Product capabilities should be structured so that changes to one concern do not unnecessarily require changes across unrelated areas.

---

## NFR-48 — AI Provider Isolation

Even if the MVP uses a single AI runtime/provider, AI interaction logic should not be unnecessarily entangled with unrelated product logic.

This requirement does not prescribe a specific abstraction yet.

---

## NFR-49 — Educational Logic Ownership

Core educational behavior such as mission progression, learning evidence, review decisions, and product rules should remain distinguishable from raw AI generation behavior.

The LLM should not become the sole source of application state or business rules.

---

## NFR-50 — Testability

Critical product behavior should be designed so that it can be tested without depending exclusively on subjective manual AI evaluation.

---

## NFR-51 — Change Traceability

Significant changes to educational behavior or product requirements should remain traceable to documentation and decision records.

---

# 13. Observability & Auditability

## NFR-52 — Operational Logging

The system should provide sufficient logging to diagnose:

- Processing failures
- AI failures
- Unexpected latency
- Data errors
- Review anomalies
- Session continuity problems

without unnecessarily logging sensitive user content.

---

## NFR-53 — AI Interaction Diagnostics

The system should make it possible to diagnose AI behavior in development and controlled operational contexts.

Diagnostic data should respect privacy requirements.

---

## NFR-54 — Failure Classification

Failures should be categorized sufficiently to distinguish, for example:

- User input failure
- File-processing failure
- AI generation failure
- Timeout
- Data persistence failure
- Unsupported capability

---

## NFR-55 — Product Analytics Boundaries

Future product analytics must respect privacy and data-minimization principles.

Analytics should prioritize understanding learning-flow quality rather than surveillance-style collection.

---

## NFR-56 — Educational Auditability

Where practical, important AI-generated educational outputs should retain enough contextual information to support later quality review.

Exact retention behavior belongs to architecture and privacy design.

---

# 14. Portability & Deployment Flexibility

## NFR-57 — Environment Portability

The system should support consistent deployment across development and intended production environments without environment-specific product behavior where avoidable.

---

## NFR-58 — Configuration Externalization

Environment-specific settings should be configurable without modifying application source code.

---

## NFR-59 — AI Provider Portability

The AI architecture must remain provider-abstracted so external AI providers can be replaced, routed, or supplemented without redefining the Learning Engine, RAG ownership rules, grounding behavior, or persistent educational state.

For v1, the approved deployment uses external Ollama API and Google Gemini API providers. Local/self-hosted inference is not a v1 requirement, but the application architecture should not prevent a future self-hosted provider adapter if later evidence justifies it.

---

# 15. Scalability & Resource Management

## NFR-60 — Resource Awareness

AI generation and multimodal processing can be resource-intensive.

The system should manage resource-heavy work explicitly rather than assuming unlimited compute.

---

## NFR-61 — Concurrent Workload Handling

The system should prevent concurrent processing from causing uncontrolled degradation of the primary learning experience.

Exact concurrency targets will depend on deployment assumptions.

---

## NFR-62 — Bounded Work

Potentially expensive operations should have explicit bounds or controls.

Examples:

- Maximum input size
- Maximum generation length
- Maximum processing duration
- Maximum simultaneous jobs

---

## NFR-63 — Scale Without Educational Regression

Performance optimizations should not silently remove grounding, safety, or educational quality safeguards merely to increase throughput.

---

# 16. Priority Levels

Non-functional requirements should later be assigned implementation priority using:

- **Critical** — required for safe/core product operation
- **High** — strongly required for usable MVP quality
- **Medium** — important but may accept limited MVP implementation
- **Future** — relevant to later scale or maturity

The definitive MVP classification belongs in **09 - MVP Scope & Roadmap**.

At a minimum, the following categories should be treated as foundational:

- AI groundedness and transparency
- Student data privacy
- Core learning-flow reliability
- Learning evidence integrity
- Material-processing transparency
- Guided usability
- Safe failure behavior

---

# 17. Measurability Framework

Not every NFR currently has a numerical threshold because architecture and deployment assumptions are intentionally not yet finalized.

Later technical specifications should convert applicable NFRs into measurable criteria.

Example:

```text
Product NFR
AI responses should preserve learning flow.
        ↓
Architecture Target
95th percentile first-token latency <= X seconds
        ↓
Test
Load / AI performance test
```

Another example:

```text
Product NFR
Private material must remain isolated.
        ↓
Security Requirement
Cross-user resource access must be denied.
        ↓
Test
Authorization integration tests
```

Qualitative educational requirements should also receive explicit evaluation rubrics rather than being ignored because they are difficult to reduce to a single number.

---

# 18. NFR-to-Feature Traceability

| NFR Area | Most Affected Features |
|---|---|
| Reliability | F-04, F-05, F-12, F-13, F-16 |
| Performance | F-02, F-03, F-04, F-05, F-06, F-09 |
| AI Quality | F-03, F-06, F-07, F-09, F-15 |
| Privacy & Security | F-01, F-02, F-12, F-15, F-16 |
| Data Integrity | F-01, F-12, F-13, F-16 |
| File Processing | F-02, F-03, F-10, F-15 |
| Usability | F-01, F-04, F-05, F-11, F-16 |
| Accessibility | F-01 through F-16 where applicable |
| Maintainability | All feature groups |
| Observability | F-02, F-03, F-04, F-05, F-12, F-13, F-15 |
| Portability | System-wide |
| Scalability | AI/material-processing and Study Mission capabilities |

---

# 19. Non-Functional Anti-Patterns

Hippocampus should avoid:

## 19.1 Silent AI Failure

Returning plausible content after grounding or processing failed.

## 19.2 Performance at the Cost of Safety

Removing grounding or validation solely to reduce latency.

## 19.3 Over-Logging

Storing uploaded educational content or sensitive student data in logs without a legitimate need.

## 19.4 Fake Availability

Showing a feature as available while its underlying capability is not actually usable.

## 19.5 Accessibility as a Post-Launch Patch

Designing inaccessible core flows and postponing all accessibility work indefinitely.

## 19.6 Unbounded AI Work

Allowing uncontrolled file sizes, context sizes, or generation lengths.

## 19.7 AI-Controlled Business Logic

Letting generated text become the only authority for persistent product state, mastery, or review rules.

## 19.8 False Precision

Displaying unsupported numerical mastery or confidence values as if scientifically exact.

---

# 20. Out of Scope

This document does not select:

- Java or Spring Boot versions
- React architecture
- Ollama configuration
- Specific local models
- Specific embedding models
- Vector database
- Relational database
- Authentication provider
- Hosting provider
- Container orchestration
- Monitoring vendor
- Exact SLA values
- Exact file-size limits
- Exact hardware requirements
- Exact review algorithm
- Exact mastery formula

These decisions belong to later scope and architecture documents.

---

# 21. Related Documents

- README - Documentation Guide
- 00 - Project Vision
- 01 - Guiding Principles
- 02 - Problem Statement
- 03 - Educational Foundation
- 04 - Product Requirements
- 05 - User Personas
- 06 - User Journey & Learning Flow
- 07 - Feature Specifications
- 09 - MVP Scope & Roadmap
- 10 - AI Architecture
- 15 - AI Evaluation Strategy

---

# 22. Next Document

**09 - MVP Scope & Roadmap**

The next document should determine which approved product requirements, feature groups, and non-functional requirements are necessary for the first viable release.

The MVP must remain educationally coherent.

Scope reduction must not transform Hippocampus into a generic:

```text
Upload
  ↓
AI Chat
  ↓
Quiz Generator
```

The first release should preserve the core identity:

> **A guided, evidence-based medical learning experience.**

---

# 23. Revision History

| Version | Date | Author | Changes |
|---|---|---|---|
| 1.1.0 | 2026-08-24 | Project Hippocampus Team | Final consistency patch replacing obsolete local-AI requirement with provider portability aligned to external Ollama API + Gemini API architecture. |
| 1.0.0 | 2026-08-23 | Project Hippocampus Team | Initial finalized Non-Functional Requirements defining system and product quality expectations before architecture selection |

---

# 24. Approval

**Status:** Final

**Reviewed By:** Project Hippocampus Team

**Approved By:** Project Hippocampus Team
