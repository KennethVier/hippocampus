package com.hippocampus.bootstrap;

import com.hippocampus.learning.domain.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FollowUpActivityContentValidatorTests {
    final FollowUpActivityContentValidator validator=new FollowUpActivityContentValidator();
    final String valid="""
        {"activityType":"SHORT_ANSWER","concept":"trusted target","learningObjective":"trusted objective",
        "question":"Explain this target.","options":[],"correctOption":null,"expectedAnswer":"A bounded answer.",
        "explanation":"Bounded feedback.","difficulty":"FOUNDATIONAL","sourceReferences":[],"limitations":[]}
        """;
    void check(String payload) { validator.validate(payload,"trusted objective","trusted target",RetrievalActivityType.SHORT_ANSWER,LearningDifficulty.FOUNDATIONAL); }
    @Test void acceptsWordingVariantsWithoutInventingSemanticQualification() {
        assertThatCode(() -> check(valid)).doesNotThrowAnyException();
        assertThatCode(() -> check(valid.replace("Explain this target.","Describe this target."))).doesNotThrowAnyException();
    }
    @Test void rejectsTargetObjectiveFormatDifficultyAndMissingAnswerMismatches() {
        for(String invalid:new String[]{valid.replace("trusted target","other target"),valid.replace("trusted objective","other objective"),
                valid.replace("SHORT_ANSWER","IDENTIFICATION"),valid.replace("FOUNDATIONAL","INTERMEDIATE"),
                valid.replace("A bounded answer."," ")}) {
            assertThatThrownBy(() -> check(invalid)).isInstanceOf(RuntimeException.class);
        }
    }
    @Test void matchingLabelsDoNotProveTheMeaningOfTheQuestion() {
        // Deliberately documents Contract C: this structurally consistent string is not semantic qualification.
        assertThatCode(() -> check(valid.replace("Explain this target.","An unrelated but structurally valid question?")))
                .doesNotThrowAnyException();
    }
}
