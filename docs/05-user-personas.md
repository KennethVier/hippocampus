---
Document ID: 05
Title: User Personas
Version: 1.0.0
Status: Final
Owner: Project Hippocampus Team
Authors: Project Hippocampus Team
Created: 2026-08-23
Last Updated: 2026-08-23
Purpose: Define the primary user of Hippocampus and the learning states that should influence how the product responds to that user.
Scope: Medical-student persona, learner context, learning states, cross-state needs, requirement traceability, assumptions, and exclusions.
Audience: Product, UX, engineering, AI, research, and medical-education contributors.
Prerequisites:
  - README
  - 00 - Project Vision
  - 01 - Guiding Principles
  - 02 - Problem Statement
  - 03 - Educational Foundation
  - 04 - Product Requirements
Related Documents:
  - 06 - User Journey & Learning Flow
  - 07 - Feature Specifications
  - 08 - Non-Functional Requirements
  - 09 - MVP Scope & Roadmap
---

# 05 - User Personas

## 1. Purpose

This document defines who Hippocampus is designed for and which learner circumstances should influence the learning experience.

It answers:

> **Who are we designing Hippocampus for, what do they need, and how should their changing learning state influence the product experience?**

Hippocampus is intentionally student-centered.

The product is designed primarily for **medical students**.

This document does not create multiple fictional personas based on demographic or lifestyle details that do not materially affect learning. Instead, it identifies one primary persona and the learning states that can change what educational support that student needs.

---

# 2. Persona Philosophy

The central persona decision for Hippocampus is:

> **Hippocampus has one primary persona—medical students—but those students move through different learning states that should influence how the learning experience responds.**

A single medical student may experience different learning needs at different times.

For example:

```text
Monday
Concept-Struggling

Wednesday
Time-Constrained

Friday
Memorization-Heavy

Three Weeks Later
Review & Retention
```

These are not different people.

They are different **learning states** of the same learner.

The product should therefore respond to meaningful learning context rather than assuming that each student permanently belongs to one fixed learning type.

---

# 3. Primary Persona — Medical Student

## P-01 — Medical Student

### Description

The primary user of Hippocampus is a medical student who must understand, retain, connect, and eventually apply a large amount of interconnected medical knowledge.

The product should be particularly suitable for students in foundational and pre-clinical stages of medical education, while remaining capable of supporting relevant learning needs as students progress.

### Typical Learning Context

The student may be studying subjects such as:

- Anatomy
- Physiology
- Biochemistry
- Histology
- Embryology
- Pathology
- Pharmacology
- Microbiology
- Immunology
- Neuroscience
- Other foundational medical subjects

The student may learn from:

- Lecture slides
- PDFs
- Textbooks
- Images
- Notes
- Transcripts
- Recorded lectures
- Review materials
- Other educational resources

### Core Goals

The student wants to:

1. Understand difficult medical concepts.
2. Identify what is important within large amounts of material.
3. Retain knowledge beyond an immediate examination.
4. Connect concepts across medical subjects.
5. Apply foundational knowledge to meaningful medical contexts.
6. Recognize what they genuinely understand versus what only feels familiar.
7. Use limited study time effectively.
8. Revisit weak concepts without unnecessarily repeating material already understood.
9. Receive useful feedback when their understanding or reasoning is incomplete.
10. Develop increasingly independent learning habits.

### Core Pain Points

The student may experience:

- Information overload
- Cognitive overload
- Passive study habits
- Difficulty understanding complex mechanisms
- Fragmented learning resources
- Weak long-term retention
- Difficulty connecting subjects
- Limited practical context during early medical training
- Uncertainty about what to review
- Difficulty knowing whether a topic is actually understood
- Limited study time
- Repetitive or inefficient review
- Difficulty learning from visually dependent material when it is reduced to text

These pain points correspond directly to the documented problems in **02 - Problem Statement**.

---

# 4. Primary Learning Context

## 4.1 Foundational and Pre-Clinical Learning

Hippocampus is especially concerned with the stage of medical education where students are building foundational knowledge but may have limited direct clinical exposure.

At this stage, a student may understand individual facts but have difficulty seeing how those facts connect to later clinical practice.

For example:

```text
Anatomy
   ↓
Physiology
   ↓
Pathophysiology
   ↓
Pharmacology
   ↓
Clinical Findings
   ↓
Clinical Reasoning
```

Hippocampus should help students form these connections progressively without pretending that simulated learning is equivalent to real patient care.

## 4.2 Learner-Level Principle

The system must not assume that all medical students possess the same prior knowledge or reasoning ability.

The learning experience should be appropriate to the student's current educational context where sufficient evidence is available.

> **Guidance should support the learner's current level while progressively encouraging greater independence.**

---

# 5. Learning States

## LS-01 — New Topic

### Situation

The student is encountering a topic for the first time or has very limited prior understanding.

### Goal

Build an accurate foundational mental model before progressing toward deeper retrieval and application.

### Pain Points

- Unfamiliar terminology
- Too many concepts introduced simultaneously
- Difficulty identifying foundational concepts
- Insufficient prior knowledge
- Complex source material
- Risk of memorizing details without understanding relationships

### Learning Need

The student needs:

- Appropriate orientation
- Clear explanations
- Progressive complexity
- Scaffolding
- Relevant visual support
- Connections to prerequisite knowledge
- Early checks for understanding

### Relevant Product Requirements

- PR-01 — Subject & Topic Organization
- PR-02 — Multimodal Learning Material
- PR-03 — Material Understanding & Grounding
- PR-04 — Guided Study Mission
- PR-05 — Adaptive Explanation
- PR-08 — Visual Learning

### Success State

The student can describe the core concept in their own words, recognize its important components, and begin retrieving foundational knowledge without relying entirely on the source material.

---

## LS-02 — Time-Constrained

### Situation

The student has limited available study time but still needs to make meaningful progress.

Example:

```text
Several lectures to review
An upcoming quiz
A practical examination
Multiple PDFs
90 minutes available tonight
```

### Goal

Use the available study period productively without becoming overwhelmed by everything that could be studied.

### Pain Points

- Too many competing materials
- Difficulty prioritizing
- Decision fatigue
- Unrealistic study plans
- Spending too much time organizing rather than learning
- Attempting to cover an entire topic when available time is insufficient

### Learning Need

The student needs:

- Clear study scope
- Prioritization
- Structured session planning
- Reduced decision burden
- Time-aware learning activities
- Honest communication when available time is insufficient for complete coverage

### Relevant Product Requirements

- PR-01 — Subject & Topic Organization
- PR-04 — Guided Study Mission
- PR-09 — Study Session & Time Management
- PR-10 — Learning Progress & Mastery
- PR-13 — Learning Evidence

### Success State

The student completes a meaningful learning session within the available period and understands what was covered, what remains incomplete, and what should be revisited later.

---

## LS-03 — Concept-Struggling

### Situation

The student has already encountered the material but cannot form a coherent understanding of the concept.

Example:

> "I understand depolarization and repolarization separately, but I do not understand how they relate to the ECG."

### Goal

Develop sufficient conceptual understanding to explain the concept and begin applying it.

### Pain Points

- Repeated rereading without improved understanding
- Explanations that assume too much prior knowledge
- Memorized terminology without a mental model
- Difficulty connecting mechanisms
- Difficulty identifying exactly where understanding breaks down

### Learning Need

The student may need:

- Alternative explanations
- Step-by-step mechanisms
- Appropriate analogies
- Visual representations
- Prerequisite connections
- Self-explanation prompts
- Targeted checks for understanding

### Relevant Product Requirements

- PR-03 — Material Understanding & Grounding
- PR-04 — Guided Study Mission
- PR-05 — Adaptive Explanation
- PR-06 — Retrieval & Knowledge Checks
- PR-08 — Visual Learning
- PR-13 — Learning Evidence

### Success State

The student can explain the concept coherently in their own words and successfully respond to a new conceptual question that requires more than recognition.

---

## LS-04 — Memorization-Heavy

### Situation

The student can recall facts but struggles to connect or apply them.

Example:

The student can recall:

> Radial nerve → posterior cord → C5–T1

but struggles with:

> A patient develops wrist drop after an injury. Which nerve is likely affected, and how does the anatomy explain the finding?

### Goal

Move from isolated factual recall toward meaningful connection, application, and reasoning.

### Pain Points

- Strong recognition but weak transfer
- Reliance on isolated facts
- Difficulty explaining mechanisms
- Difficulty using knowledge in unfamiliar situations
- False confidence caused by successful memorization

### Learning Need

The student needs progression from:

```text
Recall
  ↓
Explain
  ↓
Connect
  ↓
Apply
  ↓
Reason
```

### Relevant Product Requirements

- PR-04 — Guided Study Mission
- PR-06 — Retrieval & Knowledge Checks
- PR-07 — Contextualized Application
- PR-10 — Learning Progress & Mastery
- PR-13 — Learning Evidence

### Success State

The student can use previously memorized information to explain mechanisms, connect related concepts, and solve appropriately scaffolded application problems.

---

## LS-05 — Visual / Spatial Task

### Situation

The student is studying material where visual, structural, spatial, or morphological information is essential.

Examples include:

- Anatomy
- Histology
- Radiology
- Pathology
- Neuroanatomy
- Embryology

### Important Clarification

This state does **not** assume that the student has a fixed "visual learning style."

The educational need comes from the **nature of the material**, not from assigning the learner to an unsupported learning-style category.

### Goal

Understand and retrieve information that depends on visual or spatial relationships.

### Pain Points

- Text-only explanations of spatial relationships
- Loss of important visual information during material processing
- Difficulty identifying structures
- Images presented without meaningful instructional context
- Excessive visual clutter

### Learning Need

The student may need:

- Relevant source images
- Clear labels where appropriate
- Visual explanation
- Structure identification
- Image-based retrieval
- Connections between visual structures and underlying concepts

### Relevant Product Requirements

- PR-02 — Multimodal Learning Material
- PR-03 — Material Understanding & Grounding
- PR-05 — Adaptive Explanation
- PR-06 — Retrieval & Knowledge Checks
- PR-08 — Visual Learning

### Success State

The student can interpret the relevant visual information and connect what they see to the underlying medical concept.

---

## LS-06 — Review & Retention

### Situation

The student has studied the topic previously and now needs to determine what remains retrievable and what requires review.

### Goal

Strengthen long-term retention while avoiding unnecessary repetition of material that is already well understood.

### Pain Points

- Uncertainty about what has been forgotten
- Repeating entire topics unnecessarily
- Reviewing easy material while weak concepts remain unresolved
- Confusing topic completion with mastery
- Lack of explanation for why something is being reviewed

### Learning Need

The student needs:

- Delayed retrieval
- Targeted review
- Learning-history awareness
- Weak-concept prioritization
- Reassessment
- Meaningful review rationale

Example:

```text
Topic: Brachial Plexus

Recall: Strong
Structure Identification: Strong
Mechanism: Moderate
Application: Weak
Confidence: Medium

Priority:
Application first
Brief mechanism review
Avoid unnecessary basic recall
```

### Relevant Product Requirements

- PR-06 — Retrieval & Knowledge Checks
- PR-10 — Learning Progress & Mastery
- PR-11 — Review & Spaced Relearning
- PR-13 — Learning Evidence

### Success State

The student strengthens weak knowledge while spending less unnecessary effort on concepts for which sufficient learning evidence already exists.

---

## LS-07 — Self-Directed Study

### Situation

The student knows what they want to study and has a clear immediate objective.

Example:

> "I have 45 minutes. I want to study cardiac action potentials from this lecture."

### Goal

Receive enough structure to study effectively without losing control over the learning objective.

### Pain Points

- Tools that are either completely unstructured or overly restrictive
- Excessive setup before studying
- Inability to control topic or available time
- Recommendations that ignore the student's immediate academic priorities

### Learning Need

The student needs:

- Control over study objective
- Time-aware planning
- Structured learning progression
- Ability to request clarification
- Relevant learning evidence
- Appropriate recommendations without unnecessary restriction

### Relevant Product Requirements

- PR-01 — Subject & Topic Organization
- PR-04 — Guided Study Mission
- PR-05 — Adaptive Explanation
- PR-09 — Study Session & Time Management
- PR-10 — Learning Progress & Mastery
- PR-13 — Learning Evidence

### Success State

The student retains control over what they are studying while Hippocampus provides educational structure around how the session progresses.

> **Guided does not mean restrictive.**

---

# 6. Cross-State Learner Needs

Although learning states differ, several needs apply across the primary persona.

Hippocampus should consistently support:

## 6.1 Clarity

The student should understand what they are studying, why an activity is being presented, and what should happen next.

## 6.2 Appropriate Challenge

Activities should require productive thinking without introducing unnecessary difficulty unrelated to the learning objective.

## 6.3 Transparency

The student should be able to distinguish source-derived information, generated explanation, supplemental information, and meaningful uncertainty where relevant.

## 6.4 Learner Control

The product should provide guidance without unnecessarily taking control away from the student.

## 6.5 Meaningful Feedback

Feedback should help the student understand errors and improve reasoning rather than merely provide a score.

## 6.6 Continuity

Learning should continue across sessions through progress, review, and learning evidence rather than treating every session as isolated.

## 6.7 Educational Safety

The student should not be encouraged to treat AI-generated material as unquestionable medical authority.

---

# 7. Learning-State Transitions

Learning states are dynamic.

A student may move between states within the same topic.

Example:

```text
New Topic
    ↓
Concept-Struggling
    ↓
Understands Foundation
    ↓
Memorization-Heavy
    ↓
Contextualized Application
    ↓
Initial Competence
    ↓
Review & Retention
```

Another student may begin with a clear self-directed objective but become concept-struggling during the session.

The product should therefore avoid permanently labeling a student as:

- Weak
- Visual learner
- Memorizer
- Slow learner
- Advanced learner

Instead, Hippocampus should respond to available learning evidence and the student's current context.

---

# 8. Persona-to-Product Requirement Mapping

| Learner Context / State | Primary Product Requirements |
|---|---|
| Primary Medical Student | PR-01 through PR-13 |
| LS-01 New Topic | PR-01, PR-02, PR-03, PR-04, PR-05, PR-08 |
| LS-02 Time-Constrained | PR-01, PR-04, PR-09, PR-10, PR-13 |
| LS-03 Concept-Struggling | PR-03, PR-04, PR-05, PR-06, PR-08, PR-13 |
| LS-04 Memorization-Heavy | PR-04, PR-06, PR-07, PR-10, PR-13 |
| LS-05 Visual / Spatial Task | PR-02, PR-03, PR-05, PR-06, PR-08 |
| LS-06 Review & Retention | PR-06, PR-10, PR-11, PR-13 |
| LS-07 Self-Directed Study | PR-01, PR-04, PR-05, PR-09, PR-10, PR-13 |

This mapping does not create new product requirements.

It identifies which approved requirements are most relevant to different learner states.

---

# 9. Accessibility & Learner Diversity

Medical students differ in:

- Prior knowledge
- Pace
- Confidence
- Available study time
- Language proficiency
- Familiarity with medical terminology
- Ability to interpret specific representations
- Device and study environment
- Accessibility needs

Hippocampus should avoid assuming that a single presentation or interaction pattern will work equally well for every student.

However, personalization must be based on meaningful learner context or learning evidence rather than unsupported assumptions about fixed learning styles.

Detailed accessibility requirements belong in the Non-Functional Requirements and UX documentation.

---

# 10. Explicit Non-Personas

Hippocampus is not currently designed primarily for:

- Practicing physicians seeking clinical decision support
- Patients seeking medical advice
- Hospitals or healthcare organizations
- Residency clinical-management workflows
- Faculty course-management systems
- General K–12 education
- General-purpose AI users

These exclusions protect the product's student-centered scope.

They do not prohibit future expansion, but such expansion would require explicit product review rather than being assumed within the current scope.

---

# 11. Assumptions

This persona model assumes that:

1. Medical students can experience multiple learning states over time.
2. Learning states may change between topics and within a single study session.
3. The student's current learning evidence is more useful than assigning a permanent learning label.
4. Early medical students may have limited clinical exposure.
5. Contextualized application can prepare students to connect foundational knowledge to clinical reasoning without replacing clinical experience.
6. Students require different amounts of instructional support depending on the task and their current understanding.
7. Available study time can materially affect what constitutes a useful study session.
8. Visual support should be driven by the learning task and material rather than a fixed "visual learner" classification.
9. Students should retain meaningful control over their learning objectives.
10. The product should become less dependent on assumptions as it gathers reliable learning evidence.

---

# 12. Out of Scope

This document does not define:

- New product features
- Technical personalization algorithms
- AI architecture
- Recommendation algorithms
- User-interface layouts
- Database models
- Mastery formulas
- Specific accessibility implementation
- Clinical competency assessment
- Faculty-facing workflows
- Patient-facing workflows

These decisions belong to later documents where applicable.

---

# 13. Success Criteria

This persona model is successful if future product and UX decisions can answer:

1. Which learner need is being addressed?
2. Which learning state makes this behavior useful?
3. Which approved product requirement authorizes the capability?
4. Does the behavior align with the Educational Foundation?
5. Does the behavior help the medical student without introducing an unsupported permanent learner label?

A feature should not be justified by invented demographic characteristics that have no meaningful relationship to the educational problem.

---

# 14. Related Documents

- README - Documentation Guide
- 00 - Project Vision
- 01 - Guiding Principles
- 02 - Problem Statement
- 03 - Educational Foundation
- 04 - Product Requirements
- 06 - User Journey & Learning Flow
- 07 - Feature Specifications
- 08 - Non-Functional Requirements
- 09 - MVP Scope & Roadmap

---

# 15. Next Document

**06 - User Journey & Learning Flow**

The next document should define how the primary medical-student persona moves through Hippocampus from selecting a learning objective and providing material through guided learning, assessment, reflection, and future review.

The journey should account for the learning states defined here without creating separate applications or disconnected feature flows for each state.

---

# 16. Revision History

| Version | Date | Author | Changes |
|---|---|---|---|
| 1.0.0 | 2026-08-23 | Project Hippocampus Team | Initial finalized student-centered persona model with one primary persona and seven dynamic learning states |

---

# 17. Approval

**Status:** Final

**Reviewed By:** Project Hippocampus Team

**Approved By:** Project Hippocampus Team
