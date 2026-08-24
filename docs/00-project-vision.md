---
Document ID: 00
Title: Project Vision
Version: 1.1.0
Status: Final
Owner: Project Hippocampus Team
Authors: Project Hippocampus Team
Created: 2026-08-23
Last Updated: 2026-08-24
Purpose: Define the long-term vision, mission, student-centered product identity, success criteria, and v1 boundary of Project Hippocampus.
---

# 00 - Project Vision

# Vision Statement

To become the most trusted AI learning companion for medical students by transforming passive studying into personalized, interactive, and evidence-based learning experiences that improve understanding, long-term retention, and clinical reasoning.

---

# Mission Statement

Our mission is to empower medical students to learn more effectively by combining artificial intelligence with proven educational research.

Rather than simply summarizing content or answering questions, the platform will guide students through structured study sessions that adapt to each student's knowledge, pace, and learning progress.

Every learning activity should help students:

- Understand concepts deeply
- Retain information for the long term
- Apply knowledge to clinical scenarios
- Build confidence in their learning journey

---

# Problem Statement

Medical education is one of the most demanding academic disciplines. Students must absorb large volumes of information from textbooks, lectures, laboratory sessions, images, and clinical cases within limited time.

Current study methods often rely on:

- Passive reading
- Highlighting
- Re-reading notes
- Watching lectures repeatedly

While AI tools can summarize content and answer questions, they rarely teach in a structured, personalized, and evidence-based manner.

---

# Our Solution

Project Hippocampus is an AI-powered medical learning platform that functions as an intelligent tutor rather than a generic chatbot.

Hippocampus is designed to learn from student-provided educational resources. The long-term product may support many source formats, but **v1 intentionally supports a narrower set**:

- PDF
- Images
- Plain text / pasted notes
- Transcript text
- Video only through transcript-derived content where available

PowerPoint/Word-native ingestion, direct audio transcription, and full arbitrary video understanding are deferred until later evidence justifies them.

The platform transforms supported resources into structured Study Missions consisting of:

- AI-guided explanations
- Interactive diagrams
- Analogies
- Active recall questions
- Clinical scenarios
- Adaptive quizzes
- Personalized review schedules

The platform guides students through the learning process instead of making them choose between disconnected learning tools.

---

# Target Users

## Primary Users

- First-year medical students
- Second-year medical students
- Medical students preparing for examinations

## Future Users

- Nursing students
- Dentistry students
- Pharmacy students
- Allied health students
- Residency trainees

---

# Product Principles

The platform is built around the following philosophies:

- Learning before AI
- Evidence-based educational principles
- Understanding before memorization
- Active learning over passive consumption
- Personalized learning
- Simplicity over feature overload
- Transparency and honesty regarding AI limitations

---

# Product Goals

The platform aims to:

- Improve conceptual understanding
- Increase long-term retention
- Develop clinical reasoning
- Improve the effectiveness of students' available study time
- Encourage consistent study habits
- Reduce cognitive overload

---

# Success Metrics

We will measure success through:

- Improvement in quiz performance
- Improvement in review retention
- Study mission completion rate
- Student satisfaction
- AI explanation quality
- AI response accuracy

---

# Version 1 Scope

Included:

- Subject and topic organization
- Supported learning-material intake
- Material readiness, structure, and source grounding
- Guided Study Missions
- Adaptive explanations and scaffolding
- Active retrieval / knowledge checks
- Concept connections
- Appropriately scaffolded medical application scenarios
- Source-image-centered visual learning
- Basic time-aware mission planning
- Learning evidence and progress
- Evidence-informed review and relearning
- Provider-abstracted external AI support through Ollama API and Google Gemini API

Mechanisms such as questions, recall prompts, summaries, or card-like review may appear inside the guided flow, but they do not define the product independently.

---

# Out of Scope (Version 1)

- Patient diagnosis
- Treatment recommendations
- Electronic health records
- Telemedicine
- Social networking features

---

# North Star

> The application should never ask, "Which feature would you like to use?" Instead, it should determine the most effective next learning activity using evidence-based educational principles while keeping the experience simple, visual, and focused.

---

# Success Definition

Success is not measured by how advanced the AI appears, but by whether students genuinely understand, remember, and apply what they have learned more effectively than with traditional study methods.


## Finalization Note

The feature examples in this vision are illustrative and are not the definitive product specification. Definitive product capabilities will be established in the Product Requirements and Feature Specifications documents.

Hippocampus success should ultimately be evaluated in an educational hierarchy:

1. Educational outcomes
2. Learning behaviors
3. Product experience
4. AI and system quality

AI accuracy and system quality are enabling measures, not the ultimate definition of educational success.

---

## Revision History

| Version | Date | Author | Changes |
|---|---|---|---|
| 1.0.0 | 2026-08-23 | Project Hippocampus Team | Finalized vision and aligned scope/metrics with the Educational Foundation |
