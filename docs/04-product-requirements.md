---
Document ID: 04
Title: Product Requirements
Version: 1.0.1
Status: Final
Owner: Project Hippocampus Team
Authors: Project Hippocampus Team
Created: 2026-08-23
Last Updated: 2026-08-24
Purpose: Define what Hippocampus must do to solve the documented learning problems while following the Educational Foundation.
Scope: Product-level capabilities, functional boundaries, educational traceability, success criteria, assumptions, constraints, and exclusions.
Audience: Product, engineering, AI, UX, research, and medical-education contributors.
Prerequisites:
  - README
  - 00 - Project Vision
  - 01 - Guiding Principles
  - 02 - Problem Statement
  - 03 - Educational Foundation
Related Documents:
  - 05 - User Personas
  - 06 - User Journey & Learning Flow
  - 07 - Feature Specifications
  - 08 - Non-Functional Requirements
  - 09 - MVP Scope & Roadmap
---

# 04 - Product Requirements

## 1. Purpose

This document defines the product requirements of Project Hippocampus.

It translates the project's finalized vision, guiding principles, documented learning problems, and educational evidence into explicit product-level requirements.

This document answers:

> **What must Hippocampus do to solve the documented learning problems while following the Educational Foundation?**

It intentionally does not prescribe technical implementation.

---

## 2. Product Objective

> **Hippocampus shall transform medical learning materials into structured, evidence-based learning experiences that help students understand, retrieve, connect, apply, and retain medical knowledge.**

The product must prioritize the quality of the learning process over the quantity of generated content or available tools.

The educational direction established in the Educational Foundation is:

> **Understand → Retrieve → Connect → Apply → Receive Feedback → Reflect → Revisit**

This direction should guide the product experience without becoming an inflexible universal sequence.

---

## 3. Product Scope

Hippocampus is an AI-assisted educational platform designed primarily for medical students.

The product should support students in converting learning materials into coherent study experiences that incorporate evidence-based learning strategies.

The product scope includes:

- Subject and topic organization
- User-provided learning materials
- Multimodal educational content
- Guided study sessions
- Explanations and clarification
- Retrieval practice
- Knowledge checks
- Contextualized application
- Clinical reasoning preparation
- Visual learning
- Study-time planning
- Learning progress
- Spaced review
- Educational feedback
- Learning evidence
- Educational AI safety and transparency

The product is not intended to replace medical-school instruction, faculty, supervised clinical education, official learning materials, or clinical practice.

---

## 4. Target Users

### 4.1 Primary User

The primary user is a medical student who needs to understand and retain large amounts of interconnected medical knowledge.

The platform should be especially useful for students who:

- Are studying foundational or pre-clinical medical subjects
- Need help understanding difficult concepts
- Study from multiple types of learning materials
- Need structured study guidance
- Want active rather than purely passive learning
- Need to connect foundational knowledge to practical or clinical contexts
- Need support revisiting weak concepts over time

### 4.2 Learner-Level Principle

The system must not assume that every medical student has the same level of expertise.

Learning support, terminology, task complexity, and contextualized application should be appropriate to the learner's current level where practical.

A pre-clinical student should not be expected to reason at the same level as a clerk or practicing clinician.

---

## 5. Core Product Experience

Hippocampus should not present its primary capabilities as a large collection of disconnected learning tools.

The core experience should instead guide the student through a coherent learning flow.

```text
Choose Subject / Topic
          ↓
Add Learning Material
          ↓
Interpret Available Material
          ↓
Create Study Session
          ↓
┌───────────────────────────┐
│       STUDY MISSION       │
│                           │
│  1. Understand            │
│          ↓                │
│  2. Retrieve              │
│          ↓                │
│  3. Connect               │
│          ↓                │
│  4. Apply                 │
│          ↓                │
│  5. Check Understanding   │
│          ↓                │
│  6. Reflect               │
└───────────────────────────┘
          ↓
Schedule / Recommend Revisit
          ↓
Continue Learning
```

Not every topic requires every activity in exactly the same form.

The system should select learning activities according to the material, subject, learner level, available time, and available learning evidence.

Capabilities such as quizzes, explanations, images, scenarios, recall questions, and review are therefore learning mechanisms **inside the experience**, not competing destinations that students must manually coordinate.

---

# 6. Product Requirements

## PR-01 — Subject & Topic Organization

### Requirement

Hippocampus must allow learning materials and learning progress to be organized into meaningful subjects and topics.

A conceptual hierarchy may include:

```text
Subject
   ↓
Topic
   ↓
Learning Material
   ↓
Study Sessions
   ↓
Learning Progress
```

Example:

```text
Anatomy
└── Upper Limb
    ├── Brachial Plexus
    ├── Shoulder
    └── Arm
```

### Rationale

Medical knowledge is extensive and interconnected. Students require sufficient organization to understand what they are studying, what they have already studied, and what remains to be learned.

### Educational Basis

Supports cognitive-load management, integrated learning, metacognition, and structured learning guidance.

### Acceptance Direction

The product should make it possible for students to locate a subject or topic, associate relevant learning material with it, study it, and later return to its learning history without unnecessary navigation complexity.

---

## PR-02 — Multimodal Learning Material

### Requirement

Hippocampus must support educational material in multiple forms.

Supported product-level material types should include, where feasible:

- PDF
- Images
- Text and notes
- Transcripts
- Video-derived educational content

The definitive MVP file formats and processing limits will be established during feature and technical specification.

### Rationale

Medical students learn from heterogeneous resources. Restricting the learning experience to plain text would discard educationally important information, particularly for visually dependent subjects.

### Educational Basis

Supports visual learning, integrated learning, cognitive-load-aware presentation, and the Project Vision's requirement to work with student-provided materials.

### Acceptance Direction

The product should preserve educationally relevant information from supported source materials and should not silently reduce important visual material to incomplete text representations.

---

## PR-03 — Material Understanding & Grounding

### Requirement

Before teaching from user-provided material, Hippocampus must establish what information it can reliably derive from the material.

The product must distinguish, where relevant, between:

- Source-derived information
- AI-generated explanation
- Supplemental knowledge
- Uncertainty or inability to interpret content reliably

### Rationale

An educational system for medicine must not present generated content as though it were directly contained in a student's source material when it is not.

### Educational Basis

Supports transparency, trust, formative learning, and the Educational Foundation's AI-safety principles.

### Acceptance Direction

When source material cannot be reliably interpreted, the product should communicate the limitation rather than fabricate source content.

Students should be able to understand the basis of important explanations where practical.

---

## PR-04 — Guided Study Mission

### Requirement

Hippocampus must provide a structured study experience for a selected topic rather than requiring students to manually coordinate multiple learning tools.

A Study Mission should be capable of incorporating:

- Understanding
- Retrieval
- Connection
- Application
- Knowledge checking
- Feedback
- Reflection

The exact sequence and activities may vary according to the learning context.

### Rationale

Students may know what they need to study without knowing how to structure an effective session.

### Educational Basis

Supports active learning, retrieval practice, cognitive-load management, scaffolding, formative assessment, and metacognition.

### Acceptance Direction

A student should be able to begin a topic and progress through a coherent learning session without repeatedly choosing among disconnected product features.

---

## PR-05 — Adaptive Explanation

### Requirement

Hippocampus must help students clarify concepts they do not understand.

The product should support multiple explanatory approaches where appropriate, such as:

```text
Initial explanation
        ↓
Simpler explanation
        ↓
Analogy
        ↓
Step-by-step mechanism
        ↓
Visual representation
        ↓
Concrete example
        ↓
Check understanding
```

The product must preserve medically important meaning when simplifying.

### Rationale

A single explanation is not appropriate for every learner or every concept.

### Educational Basis

Supports scaffolding, elaboration, self-explanation, cognitive-load management, and progressive complexity.

### Acceptance Direction

A student who does not understand an explanation should be able to request another educationally meaningful representation without simply receiving an increasingly vague or medically inaccurate simplification.

---

## PR-06 — Retrieval & Knowledge Checks

### Requirement

Hippocampus must provide opportunities for active retrieval and knowledge checking.

Activities may include:

- Recall
- Identification
- Explanation
- Comparison
- Mechanism questions
- Image-based questions
- Application questions
- Multiple-choice questions
- Other educationally appropriate retrieval activities

The product must distinguish between **accidental repetition** and **intentional spaced retrieval**.

### Rationale

Students need opportunities to determine whether knowledge can be retrieved rather than merely recognized.

Repeatedly generating effectively identical questions can also create unnecessary frustration without adding educational value.

### Educational Basis

Directly supports retrieval practice, active learning, spaced practice, formative assessment, and metacognition.

### Acceptance Direction

The system should avoid unnecessary duplicate questions while intentionally revisiting important concepts when repetition is educationally justified.

---

## PR-07 — Contextualized Application

### Requirement

Hippocampus must provide appropriately scaffolded opportunities to apply foundational knowledge in meaningful medical contexts.

Applications may connect:

```text
Mechanism → Symptom
Anatomy → Injury
Physiology → Abnormal Finding
Pathology → Clinical Presentation
Pharmacology → Treatment Effect
Foundational Knowledge → Patient Scenario
```

Contextualized activities should increase in complexity according to learner readiness.

### Rationale

Medical students ultimately need to connect foundational knowledge to practical and clinical situations.

Pre-clinical learners may benefit from structured application before full clinical exposure.

### Educational Basis

Supported by the Educational Foundation's evidence concerning contextualized application, case-based learning, illness-script formation, self-explanation, and pre-clinical clinical-reasoning education.

### Acceptance Direction

The product should be capable of moving a learner from foundational knowledge toward progressively more complex application without implying that simulated scenarios replace real clinical training.

---

## PR-08 — Visual Learning

### Requirement

Hippocampus must treat educationally important visual material as a first-class part of the learning experience.

This is particularly important for subjects such as:

- Anatomy
- Histology
- Pathology
- Radiology
- Neuroanatomy
- Embryology
- Physiology

### Rationale

Important medical concepts may depend on spatial, structural, morphological, or visual relationships that cannot be adequately represented through text alone.

### Educational Basis

Supports the Educational Foundation's visual and multimedia learning principles and cognitive-load-aware instructional design.

### Acceptance Direction

When important visual information is available, the product should be able to incorporate it meaningfully into explanation, retrieval, identification, or application rather than treating the visual only as an attachment.

Visuals must remain relevant and should not introduce unnecessary visual clutter.

---

## PR-09 — Study Session & Time Management

### Requirement

Hippocampus must allow students to study within an available time constraint.

A student may indicate, for example:

> "I have 30 minutes to study this topic."

The system should structure an appropriate Study Mission within the available period.

### Rationale

Students frequently study under real scheduling constraints. The system should help them use available study time productively without claiming that one universal session duration is optimal.

### Educational Basis

Supports structured learning guidance, cognitive-load management, metacognition, and self-regulated learning.

### Acceptance Direction

The product should be able to prioritize appropriate learning activities within the available study time.

Any activity-time allocation should be treated as planning guidance rather than a scientifically universal prescription.

---

## PR-10 — Learning Progress & Mastery

### Requirement

Hippocampus must track learning progress at a meaningful topic or concept level rather than equating content consumption with learning.

Progress may consider:

- Topics studied
- Retrieval performance
- Application performance
- Confidence
- Repeated errors
- Review history
- Retention evidence over time

### Rationale

Finishing a PDF, video, or Study Mission does not necessarily mean the learner has mastered the underlying concepts.

### Educational Basis

Supports retrieval practice, formative assessment, metacognition, self-regulated learning, and spaced practice.

### Acceptance Direction

The product must distinguish **completion** from **evidence of learning**.

Progress representations should avoid implying certainty about mastery when the available evidence is insufficient.

---

## PR-11 — Review & Spaced Relearning

### Requirement

Hippocampus must support revisiting important concepts after initial study.

A conceptual review flow is:

```text
Study
  ↓
Assess
  ↓
Identify Weak Concepts
  ↓
Plan Revisit
  ↓
Retrieve Later
  ↓
Reassess
```

### Rationale

A topic should not be considered permanently learned simply because an initial study session has been completed.

### Educational Basis

Directly supports spaced practice, retrieval practice, long-term retention, and metacognition.

### Acceptance Direction

The system should use available learning evidence to determine what concepts are appropriate to revisit.

Intentional spaced repetition must not be incorrectly removed by anti-duplication logic.

---

## PR-12 — Educational AI Safety

### Requirement

Hippocampus must treat AI-generated educational content as potentially fallible and design the product accordingly.

The product must:

- Communicate meaningful uncertainty
- Avoid fabricating source content
- Distinguish source material from generated or supplemental content where relevant
- Avoid presenting itself as a clinical authority
- Avoid implying that virtual cases replace clinical exposure
- Support verification of important information where practical
- Preserve source traceability where feasible
- Avoid encouraging students to bypass reasoning

### Rationale

Errors in medical educational content can produce misconceptions and undermine trust.

### Educational Basis

Directly derives from the Guiding Principles and the Educational Foundation's sections on AI's educational role, safety, limitations, and evidence interpretation.

### Acceptance Direction

AI uncertainty and limitations must be considered product behavior, not merely implementation details.

The product should prefer transparent limitation over unsupported certainty.

---

## PR-13 — Learning Evidence

### Requirement

Hippocampus must maintain sufficient learning history to explain why a concept is being presented, reviewed, or prioritized.

Example:

```text
Brachial Plexus → Posterior Cord

Last studied: Aug 20
Recall: Strong
Application: Weak
Confidence: Low

Reason for review:
You correctly identified the radial nerve,
but had difficulty applying posterior-cord
anatomy to an injury scenario.
```

### Rationale

A raw percentage provides limited information about what the student understands and what requires further work.

### Educational Basis

Supports metacognition, formative assessment, self-regulated learning, retrieval practice, and personalized review.

### Acceptance Direction

When the system recommends review or prioritizes a concept, it should be capable of providing a learner-understandable reason based on available learning evidence.

---

# 7. Functional Boundaries

The product requirements establish **what** Hippocampus must accomplish.

They do not determine **how** these capabilities will be implemented.

This document therefore does not select:

- Programming languages
- Frameworks
- AI models
- AI runtimes
- Databases
- Vector databases
- Retrieval architectures
- API styles
- Cloud providers
- Deployment models
- Infrastructure

These decisions belong to later architecture documents.

---

# 8. Educational Traceability

Every product requirement must trace back to documented problems and educational principles.

| Product Requirement | Primary Problems Addressed | Primary Educational Basis |
|---|---|---|
| PR-01 Subject & Topic Organization | Information overload; fragmented resources; lack of guidance | Cognitive load; metacognition; integrated learning |
| PR-02 Multimodal Learning Material | Fragmented resources; complex concepts | Visual learning; integrated learning |
| PR-03 Material Understanding & Grounding | Complex concepts; limited feedback | Transparency; educational AI safety |
| PR-04 Guided Study Mission | Passive learning; cognitive overload; lack of guidance | Active learning; scaffolding; formative assessment |
| PR-05 Adaptive Explanation | Complex concepts; limited personalization | Elaboration; scaffolding; cognitive-load management |
| PR-06 Retrieval & Knowledge Checks | Passive learning; poor retention; limited feedback | Retrieval practice; active learning; formative assessment |
| PR-07 Contextualized Application | Weak subject integration; complex concepts | Contextualized application; case-based learning; clinical reasoning |
| PR-08 Visual Learning | Complex concepts; fragmented resources | Visual learning; cognitive-load-aware design |
| PR-09 Study Session & Time Management | Information overload; lack of guidance | Self-regulated learning; metacognition |
| PR-10 Learning Progress & Mastery | Poor retention; limited feedback | Formative assessment; metacognition; retrieval |
| PR-11 Review & Spaced Relearning | Poor long-term retention | Spaced practice; retrieval practice |
| PR-12 Educational AI Safety | Limited feedback; complex concepts | Transparency; AI educational role; safety limitations |
| PR-13 Learning Evidence | Limited personalization; limited feedback; lack of guidance | Metacognition; formative assessment; self-regulated learning |

The intended traceability chain remains:

```text
Documented Problem
        ↓
Educational Evidence
        ↓
Learning Principle
        ↓
Educational Requirement
        ↓
Product Requirement
        ↓
Feature
        ↓
Implementation
        ↓
Evaluation
```

---

# 9. Product Success Criteria

Hippocampus should ultimately be evaluated according to the following hierarchy:

## 9.1 Educational Outcomes

The most important question is whether the product improves meaningful learning.

Potential future measures include:

- Retrieval performance
- Retention over time
- Conceptual understanding
- Application performance
- Reduction in recurring misconceptions
- Ability to connect related concepts

## 9.2 Learning Behaviors

The product should encourage behaviors consistent with the Educational Foundation, including:

- Active retrieval
- Repeated review
- Explanation
- Application
- Reflection
- Appropriate study planning

## 9.3 Product Experience

The product should provide a coherent and manageable learning experience.

Potential future measures include:

- Study Mission completion
- Ability to navigate subjects/topics
- Student-reported usefulness
- Perceived clarity
- Friction in completing learning flows

## 9.4 AI and System Quality

System quality is necessary but is an enabling layer rather than the ultimate educational outcome.

Potential future measures include:

- Groundedness
- Source fidelity
- Explanation quality
- Question quality
- Scenario quality
- Error rate
- Reliability
- Latency

> **A technically impressive AI system that does not improve the learning process is not sufficient for Hippocampus.**

---

# 10. Assumptions

This PRD currently assumes that:

1. Medical students use multiple forms of digital learning material.
2. Students benefit from having a coherent learning flow rather than manually coordinating many disconnected tools.
3. Not every topic requires the same learning activity sequence.
4. Some medical subjects require visual learning.
5. Students have different levels of prior knowledge and available study time.
6. Learning evidence can be used to improve future review decisions.
7. AI-generated educational content requires safeguards and evaluation.
8. Contextualized application can support learning but does not replace supervised clinical experience.
9. Product requirements may later be constrained by technical feasibility, privacy, cost, model capability, and content-processing limitations.
10. Such constraints must be documented rather than silently weakening educational requirements.

---

# 11. Constraints

The product must operate within the following product-level constraints:

- Medical educational accuracy is important and generated content cannot be assumed correct.
- User-provided material may be incomplete, low quality, unsupported, or difficult to interpret.
- Visual information may not always be extractable or interpretable.
- Video processing may require different capabilities from text or PDF processing.
- AI models may have context, reasoning, latency, and reliability limitations.
- Available study time may be insufficient to cover an entire topic.
- Personalization is limited by the amount and quality of available learning evidence.
- Educational research does not justify universal prescriptions for every learner or subject.
- Real clinical competence cannot be established through the application alone.

These constraints must be considered explicitly during later feature and architecture design.

---

# 12. Out of Scope

This document does not define:

- Specific implementation technologies
- Spring Boot architecture
- React architecture
- AI provider/runtime configuration
- Specific language models
- RAG implementation
- Embedding models
- Vector databases
- Database schema
- REST API design
- Authentication architecture
- Infrastructure
- Deployment
- Pricing or monetization
- Clinical decision support
- Patient-care recommendations
- Replacement of formal medical education
- Replacement of supervised clinical experience

---

# 13. Open Questions

The following questions are intentionally deferred to later documents:

1. Which user personas should be prioritized within the broader medical-student population?
2. What is the exact end-to-end user journey?
3. Which activities belong in every Study Mission versus being selected conditionally?
4. Which upload formats belong in the MVP?
5. How should video-derived learning material be handled in the MVP?
6. How should mastery and confidence be represented to avoid false precision?
7. How should intentional spaced repetition differ from accidental duplication?
8. How should visual learning interactions vary by medical subject?
9. What level of source traceability is required for different generated outputs?
10. Which product requirements belong in MVP versus later releases?

These questions should be resolved in their appropriate documents rather than prematurely in this PRD.

---

# 14. Related Documents

- README - Documentation Guide
- 00 - Project Vision
- 01 - Guiding Principles
- 02 - Problem Statement
- 03 - Educational Foundation
- 05 - User Personas
- 06 - User Journey & Learning Flow
- 07 - Feature Specifications
- 08 - Non-Functional Requirements
- 09 - MVP Scope & Roadmap

---

# 15. Next Document

**05 - User Personas**

The next document should define who Hippocampus is designing for in sufficient detail to guide the learning flow and feature specifications.

It should not change the educational principles or product requirements established in documents 00–04.

---

# 16. Revision History

| Version | Date | Author | Changes |
|---|---|---|---|
| 1.0.1 | 2026-08-24 | Project Hippocampus Team | Final consistency patch replacing premature single-provider terminology with provider-neutral architecture wording. |
| 1.0.0 | 2026-08-23 | Project Hippocampus Team | Initial finalized Product Requirements Document containing the 13 approved product requirements |

---

# 17. Approval

**Status:** Final

**Reviewed By:** Project Hippocampus Team

**Approved By:** Project Hippocampus Team
