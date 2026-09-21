package com.hippocampus.ai.domain;

public sealed interface AiTaskContext permits
        ExplanationInput,
        QuestionGenerationInput,
        ResponseEvaluationInput,
        ConceptConnectionInput,
        ContextualApplicationInput,
        StructuredOutputRepairInput {}
