---
Document ID: 02
Title: Problem Statement
Version: 1.0.0
Status: Final
Owner: Project Hippocampus Team
Authors: Project Hippocampus Team
Created: 2026-08-23
Last Updated: 2026-08-23
Purpose: Define the educational problems Hippocampus aims to solve.
Prerequisites:
  - README
  - 00 - Project Vision
  - 01 - Guiding Principles
Related Documents:
  - 03 - Educational Foundation
  - 04 - Product Requirements
---

# 02 - Problem Statement

## 1. Purpose

Hippocampus exists to address practical learning problems faced by medical students when studying large amounts of interconnected material.

The problems below are intentionally described at the **problem level**, not as a list of product features. They establish what needs to improve before any solution is selected.

## 2. Background

Medical education requires students to acquire a large body of interconnected knowledge while developing understanding, long-term retention, application, and eventually clinical reasoning.

The Educational Foundation establishes evidence supporting active retrieval, spaced learning, meaningful elaboration, contextualized application, feedback, cognitive-load management, metacognition, and appropriately scaffolded clinical reasoning.

The problem Hippocampus addresses is therefore not simply that students have "too much information."

It is that students need a more coherent way to turn information into **understanding, retrievable knowledge, meaningful connections, and progressively stronger application**.

## 3. Core Problems

### 3.1 Information Overload

**Problem:** Medical students encounter large volumes of information across lectures, textbooks, notes, presentations, videos, images, and other resources.

**Why it matters:** Large information volume can make it difficult to determine what is important, organize knowledge, and allocate limited study time effectively.

**Evidence relationship:** Cognitive-load research emphasizes the importance of managing unnecessary cognitive burden and structuring information around learner expertise.

**Hippocampus implication:** The system should help students organize and prioritize learning without simply adding another large source of information.

### 3.2 Passive Learning Habits

**Problem:** Students can spend substantial study time reading, rereading, watching, or highlighting without sufficiently retrieving or applying what they learned.

**Why it matters:** Passive exposure does not necessarily demonstrate whether knowledge can be retrieved or used.

**Evidence relationship:** Retrieval practice and active-learning research support engaging learners in activities that require recall, explanation, and application.

**Hippocampus implication:** Learning should require meaningful cognitive participation rather than only content consumption.

### 3.3 Poor Long-Term Retention

**Problem:** Students may understand material during an initial study session but struggle to retrieve it later.

**Why it matters:** Medical education requires knowledge to remain accessible across courses and eventually in clinical contexts.

**Evidence relationship:** Retrieval practice and spaced/distributed learning are supported by research in health-professions and medical education.

**Hippocampus implication:** Learning should continue beyond the initial exposure through appropriately spaced retrieval and review.

### 3.4 Difficulty Understanding Complex Concepts

**Problem:** Medical concepts can involve multiple interacting mechanisms, structures, processes, and relationships.

**Why it matters:** Memorizing isolated facts does not necessarily produce conceptual understanding.

**Evidence relationship:** Elaboration, self-explanation, scaffolding, and cognitive-load-informed instruction provide educational approaches for supporting complex learning.

**Hippocampus implication:** Difficult concepts should be broken down, connected to prior knowledge, explained at an appropriate level, and progressively applied.

### 3.5 Fragmented Learning Resources

**Problem:** Relevant knowledge may be distributed across lecture slides, notes, textbooks, images, videos, transcripts, and other resources.

**Why it matters:** Students may spend effort locating and reconciling information instead of learning from it.

**Evidence relationship:** Integrated learning and cognitive-load principles support organizing related information meaningfully while avoiding unnecessary complexity.

**Hippocampus implication:** Student-provided materials should be transformed into a coherent learning context while preserving important source information and its limitations.

### 3.6 Limited Personalized Learning

**Problem:** Students differ in prior knowledge, confidence, learning gaps, pace, and difficulty with specific concepts.

**Why it matters:** A single fixed explanation or question sequence may be inappropriate for every learner.

**Evidence relationship:** Cognitive-load-informed instruction emphasizes adapting task complexity and support to learner expertise. Self-regulated learning research also highlights the importance of monitoring and adjusting learning.

**Hippocampus implication:** Learning support should adapt where evidence indicates that adaptation is educationally useful.

### 3.7 Weak Integration Across Subjects

**Problem:** Students can encounter anatomy, physiology, pathology, pharmacology, and clinical concepts as separate blocks of information even when they describe the same underlying system.

**Why it matters:** Medicine requires connecting mechanisms, structures, findings, and interventions.

**Evidence relationship:** Clinical-reasoning education emphasizes integrating biomedical knowledge with clinically meaningful contexts.

**Hippocampus implication:** The learning experience should make meaningful cross-subject relationships visible when those relationships improve understanding or application.

### 3.8 Limited Formative Feedback

**Problem:** Students may know that an answer is wrong without understanding why their reasoning failed or what concept they should revisit.

**Why it matters:** Incorrect reasoning can persist when learners receive only a score or answer key.

**Evidence relationship:** Formative assessment and feedback are important components of learning and clinical-reasoning education.

**Hippocampus implication:** Feedback should help learners understand errors, correct misconceptions, and attempt reasoning again.

### 3.9 Cognitive Overload

**Problem:** Students can become overwhelmed not only by the amount of material but also by how information, tasks, and interfaces are presented.

**Why it matters:** Unnecessary cognitive load can consume mental resources that should be directed toward learning.

**Evidence relationship:** Cognitive Load Theory provides a framework for distinguishing unnecessary burden from productive cognitive effort.

**Hippocampus implication:** The product should simplify the learning experience without simplifying away necessary thinking.

### 3.10 Lack of Structured Learning Guidance

**Problem:** Students may know what they need to study but lack a clear progression for moving from understanding to retrieval, application, review, and reflection.

**Why it matters:** Self-directed study requires learners to plan, monitor, and regulate their learning.

**Evidence relationship:** Research on metacognition and self-directed learning supports structured opportunities for planning, monitoring, feedback, and adjustment.

**Hippocampus implication:** Hippocampus should provide a coherent learning flow rather than forcing students to choose among a large collection of disconnected tools.

## 4. Problem Relationships

These problems are interconnected:

```text
Information Overload
        ↓
Difficulty Organizing Knowledge
        ↓
Cognitive Overload
        ↓
Passive / Inefficient Study
        ↓
Weak Understanding
        ↓
Poor Retrieval and Retention
        ↓
Weak Integration and Application
        ↓
Difficulty Developing Clinical Reasoning
```

This is not intended to claim that every student experiences the problems in this exact sequence. It illustrates how multiple educational problems can reinforce one another.

## 5. What Hippocampus Is Trying to Improve

Hippocampus should help move students from:

> Information consumption

toward:

> Understanding → Retrieval → Connection → Application → Feedback → Reflection → Revisit

The platform should therefore be evaluated by whether it improves the **learning process and educational outcomes**, not merely by how many tools or generated materials it provides.

## 6. Problem-to-Evidence Traceability

| Problem | Primary Educational Evidence |
|---|---|
| Information overload | Cognitive Load Theory |
| Passive learning habits | Active learning and retrieval practice |
| Poor long-term retention | Retrieval practice and spaced practice |
| Difficulty understanding complex concepts | Elaboration, self-explanation, scaffolding |
| Fragmented learning resources | Integrated learning and cognitive-load principles |
| Limited personalized learning | Adaptive support, learner expertise, self-regulated learning |
| Weak integration across subjects | Clinical reasoning and integrated medical education |
| Limited formative feedback | Formative assessment and feedback |
| Cognitive overload | Cognitive Load Theory |
| Lack of structured learning guidance | Metacognition and self-regulated learning |

The detailed research evidence belongs in **03 - Educational Foundation**. This document intentionally provides the problem-level bridge between the vision and the educational foundation.

## 7. Out of Scope

This document does not prescribe:

- Specific features
- Specific AI models
- Technical architecture
- UI implementation
- Database structure
- Deployment strategy

Those decisions belong to later documents.

## 8. Success Criteria

Every future product requirement should trace back to:

```text
Documented Problem
        ↓
Educational Evidence
        ↓
Educational Requirement
        ↓
Product Requirement
        ↓
Feature
```

A feature that cannot be connected to a documented problem or educational requirement should be questioned before implementation.

## 9. Related Documents

- README - Documentation Guide
- 00 - Project Vision
- 01 - Guiding Principles
- 03 - Educational Foundation
- 04 - Product Requirements

## 10. Revision History

| Version | Date | Author | Changes |
|---|---|---|---|
| 1.0.0 | 2026-08-23 | Project Hippocampus Team | Finalized problem definitions and aligned them with the Educational Foundation |

## 11. Approval

**Status:** Final

**Reviewed By:** Project Hippocampus Team

**Approved By:** Project Hippocampus Team
