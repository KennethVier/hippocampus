---
Document ID: 03
Title: Educational Foundation
Version: 1.0.1
Status: Final
Owner: Project Hippocampus Team
Authors: Project Hippocampus Team
Created: 2026-08-23
Last Updated: 2026-08-24
Next Review: After completion of Product Requirements
Purpose: Define the educational science and evidence that guide how Hippocampus supports medical-student learning.
Scope: Learning science, cognitive principles, medical education, contextualized application, and instructional-design implications.
Audience: Product, engineering, AI, UX, research, and medical-education contributors.
Prerequisites:
  - README
  - 00 - Project Vision
  - 01 - Guiding Principles
  - 02 - Problem Statement
---

# 03 - Educational Foundation

## 1. Purpose

This document defines the educational foundation of Project Hippocampus.

Its purpose is to establish the learning principles, cognitive principles, and medical-education evidence that should guide the design of Hippocampus.

> **Educational evidence should determine the learning experience, and technology should implement that experience.**

This document is the educational source of truth for future product decisions.

## 2. Scope

This document covers:

- Learning science relevant to medical students
- Retrieval and memory
- Spaced and distributed learning
- Active learning
- Cognitive load
- Elaboration and self-explanation
- Contextualized application
- Case-based and clinical-reasoning learning
- Formative assessment and feedback
- Metacognition and self-directed learning
- Visual and multimedia learning considerations
- Implications for Hippocampus

It does not define product features, AI models, AI architecture, backend/frontend architecture, databases, infrastructure, or implementation details.

## 3. Educational Philosophy

Hippocampus is based on the following position:

> **The goal is not to help students consume more information. The goal is to help students understand, retrieve, connect, apply, and retain knowledge.**

### 3.1 Learning should be active

Students should regularly retrieve and manipulate knowledge rather than only reread or rewatch information.

### 3.2 Understanding should precede isolated memorization

Memorization remains necessary in medicine, but isolated facts become more useful when connected to mechanisms, structures, concepts, and clinical contexts.

### 3.3 Knowledge should be revisited over time

Important knowledge should return through appropriately spaced opportunities for retrieval.

### 3.4 Application should connect foundational knowledge to meaningful contexts

Pre-clinical students can begin developing clinical reasoning through appropriately scaffolded cases, while recognizing that virtual experiences cannot replace real patient exposure.

### 3.5 Instruction should match learner expertise

Novices require more structure and support than advanced learners. Support and complexity should change as competence develops.

### 3.6 Feedback should support learning

Assessment should help learners identify and correct gaps rather than only produce a score.

### 3.7 Technology should reduce unnecessary cognitive burden

The interface, explanations, and learning flow should avoid unnecessary complexity.

### 3.8 AI should support thinking rather than replace it

AI should facilitate retrieval, explanation, questioning, feedback, and reflection rather than bypassing student reasoning.

## 4. Core Learning Principles

### 4.1 Retrieval Practice

**Definition:** Retrieving information from memory rather than simply reviewing it.

**Evidence:** A 2024 systematic review examined distributed practice and retrieval practice in health-professions education. A 2026 systematic review and meta-analysis of spaced repetition in medical education included 21,415 learners and found a significant overall effect favoring spaced-repetition study over standard study techniques.

**Implications:** Hippocampus should regularly require students to retrieve knowledge before revealing explanations. Retrieval should progress from factual recall toward conceptual explanation, application, and reasoning.

### 4.2 Spaced and Distributed Practice

**Definition:** Distributing learning and retrieval opportunities across time.

**Evidence:** Recent systematic reviews and meta-analyses in medical and health-professions education report positive effects of spaced learning/repetition on learning and retention, while also noting limitations in study quality and variation in implementation.

**Implications:** Important topics should return after initial learning through appropriately spaced retrieval rather than being treated as completed after one session.

### 4.3 Active Learning

**Definition:** Learning that requires mental engagement through retrieval, prediction, explanation, problem solving, application, or reflection.

**Implications:** Hippocampus should move learners from receiving information toward processing, retrieving, applying, and reflecting on information.

### 4.4 Elaboration and Self-Explanation

**Definition:** Connecting new information with existing knowledge and explaining why an answer or mechanism makes sense.

**Evidence:** Research in medical education has examined self-explanation during clinical reasoning and found that examples and prompts can influence its learning value.

**Implications:** Students should be asked to explain mechanisms, justify answers, explain alternatives, and connect concepts.

### 4.5 Contextualized Application and Clinical Reasoning

**Definition:** Placing foundational knowledge into meaningful situations where learners use it to interpret information, explain mechanisms, or make decisions.

**Evidence:** A 2022 systematic review found positive results from structured approaches designed to develop illness scripts and integrate biomedical and clinical knowledge in pre-clinical students. A 2023 scoping review documented clinical-reasoning curricula in preclinical undergraduate medical education. Recent case-based/problem-based learning reviews also report benefits for several learning outcomes.

**Important limitation:** Virtual cases prepare students for clinical reasoning but cannot replace clerkships, patient interaction, bedside teaching, supervised clinical practice, or clinical-skills training.

**Implications:** Hippocampus should support a progression such as:

```text
Concept
  ↓
Recall
  ↓
Connection
  ↓
Application
  ↓
Clinical scenario
  ↓
Reasoning
  ↓
Feedback
  ↓
Reflection
```

### 4.6 Case-Based Learning

**Definition:** Using patient or problem scenarios as contexts for integrating and applying knowledge.

**Evidence:** Systematic reviews and meta-analyses have reported benefits of case-based learning for outcomes such as critical thinking and selected knowledge or clinical-learning outcomes, while also emphasizing heterogeneity and the need for stronger research.

**Implications:** Cases should require learners to identify relevant information, connect findings to concepts, explain mechanisms, justify answers, receive feedback, and reconsider reasoning.

### 4.7 Cognitive Load Management

**Definition:** Designing instruction around the limited capacity of working memory and the learner's existing expertise.

**Evidence:** AMEE guidance and subsequent reviews describe cognitive-load principles as highly relevant to medical education. Effective design reduces unnecessary load, manages task complexity, and adjusts instructional support to learner expertise.

> **Hippocampus should reduce unnecessary cognitive load, not eliminate productive cognitive effort.**

**Implications:**

- Present information progressively.
- Break complex topics into meaningful units.
- Avoid unnecessary interface complexity.
- Provide scaffolding for novices.
- Increase complexity gradually.
- Reduce support as learners become more capable.

### 4.8 Worked Examples and Scaffolding

**Definition:** Providing structured examples and support before expecting independent performance.

**Evidence:** Cognitive-load-informed medical-education models recommend beginning with high support and lower complexity, then gradually increasing complexity and fading support as learners become more proficient.

**Implications:** A learning progression may move from:

```text
Example
  ↓
Explanation
  ↓
Guided reasoning
  ↓
Partial completion
  ↓
Independent reasoning
```

### 4.9 Formative Assessment and Feedback

**Definition:** Using learner performance to identify gaps and guide subsequent learning.

**Implications:** Feedback should ideally answer:

1. Was the answer correct?
2. Why?
3. What concept was involved?
4. Where did the reasoning diverge?
5. What should be revisited?
6. Can the learner attempt the problem again?

Feedback should support learning rather than merely reveal an answer.

### 4.10 Metacognition and Self-Regulated Learning

**Definition:** Awareness and regulation of one's own learning through planning, monitoring, and adjustment.

**Evidence:** A 2025 systematic review and meta-analysis found evidence supporting self-directed learning in undergraduate medical education, while reporting substantial heterogeneity between studies.

**Implications:** Students should be able to set goals, estimate confidence, identify weak areas, monitor progress, reflect on errors, and adjust their study behavior.

## 5. Visual and Multimedia Learning

Medical education contains subjects in which visual representations are essential, including anatomy, histology, radiology, pathology, neuroanatomy, physiology, and embryology.

Visual material should therefore be treated as meaningful instructional content rather than decoration.

However:

> **Adding an image does not automatically improve learning.**

Visuals should be relevant, appropriately labeled, integrated with explanations, and free from unnecessary visual clutter.

For visually dependent subjects, Hippocampus should preserve and meaningfully use important source visuals rather than converting everything into plain text.

## 6. Progressive Complexity

Learning should progress from supported understanding toward independent application.

```text
Low complexity + high support
          ↓
Low complexity + reduced support
          ↓
Higher complexity + guided support
          ↓
Higher complexity + reduced support
          ↓
Contextualized application
          ↓
Independent reasoning
```

This approach is consistent with cognitive-load-informed medical-education models that adjust task complexity and instructional support according to learner expertise.

## 7. Integrated Learning

Medical knowledge is highly interconnected:

```text
Anatomy
   ↓
Physiology
   ↓
Pathophysiology
   ↓
Pharmacology
   ↓
Clinical findings
   ↓
Clinical reasoning
```

Hippocampus should support meaningful connections across subjects when those connections improve understanding or application.

Integration should be purposeful, not indiscriminate.

## 8. Educational Principles for Hippocampus

| Principle | Educational Goal | Hippocampus Implication |
|---|---|---|
| Retrieval practice | Strengthen recall | Students regularly retrieve knowledge |
| Spaced practice | Improve retention | Important topics return over time |
| Active learning | Increase engagement | Students do more than consume content |
| Elaboration | Build connections | Students explain relationships and mechanisms |
| Self-explanation | Develop reasoning | Students justify answers |
| Contextualized application | Connect knowledge to use | Concepts appear in meaningful scenarios |
| Case-based learning | Integrate knowledge | Cases connect multiple concepts |
| Cognitive-load management | Reduce unnecessary burden | Information and interface are structured |
| Scaffolding | Support developing learners | Difficulty and guidance adapt to expertise |
| Formative feedback | Correct misconceptions | Feedback occurs during learning |
| Metacognition | Improve self-regulation | Students monitor confidence and progress |
| Visual learning | Support spatial/conceptual understanding | Important visuals are preserved and used |

## 9. Evidence Interpretation Rules

Hippocampus will not treat every educational study as equally strong evidence.

When evaluating research, consider:

1. Study design
2. Systematic reviews and meta-analyses
3. Randomized controlled trials
4. Sample size
5. Outcome measures
6. Risk of bias
7. Generalizability to medical students
8. Whether outcomes measure learning or only satisfaction
9. Whether effects are short-term or durable
10. Whether evidence supports causation or only association

Use evidence language carefully:

- **Strong evidence**
- **Moderate evidence**
- **Promising evidence**
- **Mixed evidence**
- **Insufficient evidence**

Avoid absolute claims such as "research proves this is the best way to learn."

Prefer:

> "Current evidence supports..."

or:

> "Evidence suggests..."

## 10. Practical Application Principle

Hippocampus recognizes that medical students ultimately need to use knowledge in practical and clinical contexts.

> **Virtual application is preparation for clinical practice, not a replacement for clinical practice.**

The platform may eventually provide clinical scenarios, patient cases, mechanism-to-symptom connections, structured reasoning exercises, image interpretation practice, progressive case questions, and simulated decision points.

These should help students connect foundational knowledge to realistic contexts without implying equivalence to real patient care.

## 11. AI's Educational Role

AI is not itself an educational principle.

AI is a delivery mechanism that may support evidence-based learning strategies.

Potential roles include:

- Explaining difficult concepts at an appropriate level
- Generating retrieval questions
- Asking follow-up questions
- Providing contextualized scenarios
- Giving formative feedback
- Prompting self-explanation
- Adapting difficulty
- Connecting related concepts
- Identifying potential knowledge gaps

AI should not encourage passive answer consumption, unnecessary dependence, unverified medical claims, replacement of student reasoning, replacement of medical educators, or replacement of clinical supervision.

## 12. Educational Safety and Limitations

Hippocampus is an educational tool.

It is not a substitute for:

- Medical-school instruction
- Faculty guidance
- Clinical supervision
- Patient encounters
- Clinical-skills laboratories
- Official textbooks or institutional materials
- Evidence-based clinical guidelines

AI-generated educational content may contain errors. The platform should therefore be designed around the assumption that generated explanations, questions, cases, summaries, and other learning materials require appropriate validation and evaluation.

## 13. Research-to-Product Traceability

Future requirements should be traceable through:

```text
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

For example:

```text
Retrieval practice
        ↓
Learners should repeatedly recall knowledge
        ↓
Hippocampus needs retrieval opportunities
        ↓
Question-based learning
        ↓
Adaptive questions
        ↓
Implementation
        ↓
Measure learning outcomes
```

This prevents features from being added simply because they are technologically interesting.

## 14. What Hippocampus Will Not Claim

Hippocampus will not claim that:

- One learning technique is universally superior.
- AI can replace medical educators.
- Virtual cases replace clinical exposure.
- More studying automatically produces better learning.
- More questions automatically produce better learning.
- Flashcards alone produce deep understanding.
- Visuals automatically improve learning.
- Personalization automatically improves learning.
- A longer study session is necessarily more effective.
- An AI-generated explanation is automatically accurate.

## 15. Educational Design Summary

The educational foundation can be summarized as:

> **Understand → Retrieve → Connect → Apply → Receive Feedback → Reflect → Revisit**

This is not intended to be a rigid universal learning algorithm. It represents the direction of the learning experience.

Hippocampus should help students move beyond:

> **Read → Memorize → Test → Forget**

toward:

> **Understand → Retrieve → Connect → Apply → Reason → Reflect → Retain**

## 16. Key References

1. Trumble, E., Lodge, J., Mandrusiak, A., & Forbes, R. (2024). *Systematic review of distributed practice and retrieval practice in health professions education.*  
   https://pubmed.ncbi.nlm.nih.gov/37615780/

2. Maye, J. A., & Hurley, F. (2026). *The Effectiveness of Spaced Repetition in Medical Education: A Systematic Review and Meta-Analysis.*  
   https://pubmed.ncbi.nlm.nih.gov/41601436/

3. *Effects of spaced learning on knowledge acquisition, knowledge retention, and learning-related anxiety in health professions education: A systematic review and meta-analysis* (2026).  
   https://pubmed.ncbi.nlm.nih.gov/42468294/

4. Young, J. Q., Van Merrienboer, J., Durning, S., & Ten Cate, O. (2014). *Cognitive Load Theory: implications for medical education: AMEE Guide No. 86.*  
   https://pubmed.ncbi.nlm.nih.gov/24593808/

5. Ghanbari, S., Haghani, F., Barekatain, M., & Jamali, A. (2020). *A systematized review of cognitive load theory in health sciences education and a perspective from cognitive neuroscience.*  
   https://pubmed.ncbi.nlm.nih.gov/32953905/

6. Leppink, J., & van den Heuvel, A. (2015). *The evolution of cognitive load theory and its application to medical education.*  
   https://pubmed.ncbi.nlm.nih.gov/26016429/

7. Si, J. (2022). *Strategies for developing pre-clinical medical students' clinical reasoning based on illness script formation: a systematic review.*  
   https://pubmed.ncbi.nlm.nih.gov/35255616/

8. Hawks, M. K., et al. (2023). *Clinical Reasoning Curricula in Preclinical Undergraduate Medical Education: A Scoping Review.*  
   https://pubmed.ncbi.nlm.nih.gov/36862627/

9. Ten Cate, O., et al. *Principles and Practice of Case-based Clinical Reasoning Education: A Method for Preclinical Students.*  
   https://pubmed.ncbi.nlm.nih.gov/31314234/

10. Cen, X.-Y., Hua, Y., Niu, S., & Yu, T. (2021). *Application of case-based learning in medical student education: a meta-analysis.*  
    https://pubmed.ncbi.nlm.nih.gov/33928603/

11. *Effectiveness of case-based learning in comparison to alternate learning methods on learning competencies and student satisfaction among healthcare professional students: A systematic review* (2025).  
    https://pubmed.ncbi.nlm.nih.gov/40144176/

12. Lu, B.-R., et al. (2026). *Effectiveness of case-based learning combined with problem-based learning versus lecture-based learning in clinical medical education: a systematic review and meta-analysis.*  
    https://pubmed.ncbi.nlm.nih.gov/41510950/

13. Chamberland, M., et al. (2015). *Self-explanation in learning clinical reasoning: the added value of examples and prompts.*  
    https://pubmed.ncbi.nlm.nih.gov/25626750/

14. Aulakh, J., et al. (2025). *Self-directed learning versus traditional didactic learning in undergraduate medical education: a systematic review and meta-analysis.*  
    https://pubmed.ncbi.nlm.nih.gov/39815233/

15. *AI-Powered Problem- and Case-based Learning in Medical and Dental Education: A Systematic Review and Meta-analysis* (2025).  
    https://pubmed.ncbi.nlm.nih.gov/40578029/

## 17. Related Documents

- README - Documentation Guide
- 00 - Project Vision
- 01 - Guiding Principles
- 02 - Problem Statement
- 04 - Product Requirements
- 05 - User Personas
- 06 - User Journey & Learning Flow
- 07 - Feature Specifications

## 18. Next Document

**04 - Product Requirements**

The next document should translate this educational foundation into explicit product requirements without prematurely deciding technical implementation.

---

# Revision History

| Version | Date | Author | Changes |
|---|---|---|---|
| 1.0.1 | 2026-08-24 | Project Hippocampus Team | Final consistency audit: corrected document status from Draft to Final; educational content unchanged. |
| 1.0.0 | 2026-08-23 | Project Hippocampus Team | Initial educational foundation |

# Approval

**Status:** Final

**Reviewed By:** Project Hippocampus Team

**Approved By:** Project Hippocampus Team
