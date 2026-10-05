package com.hippocampus.ai.evaluation;

import com.hippocampus.ai.domain.ActivityType;
import com.hippocampus.ai.domain.Evaluation;
import com.hippocampus.ai.domain.ExplanationMode;
import com.hippocampus.ai.domain.QuestionDifficulty;
import com.hippocampus.rag.domain.GroundingMode;
import java.util.List;
import java.util.Map;

final class GoldenAiDataset {

    record All(
            String version,
            List<ExplanationCase> explanations,
            List<QuestionCase> questions,
            List<ResponseEvaluationCase> responseEvaluations) {}

    record ExplanationFile(String version, String task, List<ExplanationCase> cases) {}

    record QuestionFile(String version, String task, List<QuestionCase> cases) {}

    record ResponseEvaluationFile(
            String version, String task, List<ResponseEvaluationCase> cases) {}

    record Learner(
            String learningState,
            String topicExposure,
            String difficultyDirection,
            Map<String, String> relevantEvidence,
            List<String> relevantMisconceptions) {}

    record Source(String sourceId, String content) {}

    record ExplanationCase(
            String caseId,
            String subject,
            String topic,
            String learningObjective,
            String targetConcept,
            ExplanationMode explanationMode,
            GroundingMode groundingMode,
            Learner learner,
            List<Source> sourceEvidence,
            List<List<String>> requiredConceptGroups,
            List<String> forbiddenClaims,
            boolean requireLimitation,
            Boolean expectedSupplementalKnowledgeUsed,
            int maximumExplanationWords,
            String reviewerNotes) {}

    record QuestionCase(
            String caseId,
            String subject,
            String topic,
            String learningObjective,
            String targetConcept,
            ActivityType activityType,
            QuestionDifficulty difficulty,
            GroundingMode groundingMode,
            Learner learner,
            List<Source> sourceEvidence,
            List<String> recentQuestionIntents,
            String repetitionPurpose,
            List<List<String>> requiredConceptGroups,
            List<List<String>> requiredAnswerConceptGroups,
            List<String> answerLeakageTerms,
            List<String> forbiddenClaims,
            String reviewerNotes) {}

    record ResponseEvaluationCase(
            String caseId,
            String subject,
            String topic,
            String question,
            List<String> expectedConcepts,
            String expectedAnswer,
            String studentResponse,
            GroundingMode groundingMode,
            Learner learner,
            List<Source> sourceEvidence,
            List<Evaluation> allowedEvaluations,
            List<Evaluation> forbiddenEvaluations,
            List<List<String>> requiredCorrectConceptGroups,
            List<List<String>> requiredMissingConceptGroups,
            List<List<String>> allowedMisconceptionGroups,
            List<List<String>> requiredFeedbackConceptGroups,
            boolean requireNoMissingConcepts,
            boolean requireNoMisconceptions,
            List<String> forbiddenOutputTerms,
            String reviewerNotes) {}

    private GoldenAiDataset() {}
}
