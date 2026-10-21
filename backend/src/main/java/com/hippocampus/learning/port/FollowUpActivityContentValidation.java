package com.hippocampus.learning.port;

import com.hippocampus.learning.domain.LearningDifficulty;
import com.hippocampus.learning.domain.RetrievalActivityType;

/** Structural contract only. Does not establish medical semantic qualification. */
public interface FollowUpActivityContentValidation {
    void validate(String payload, String objective, String target,
            RetrievalActivityType type, LearningDifficulty difficulty);
}
