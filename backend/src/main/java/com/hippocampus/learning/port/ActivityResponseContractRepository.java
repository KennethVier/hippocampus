package com.hippocampus.learning.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityResponseContractRepository {

    Optional<ResponseContract> findValidatedForActivity(UUID activityId, UUID artifactId, UUID ownerId);

    record ResponseContract(
            String question,
            List<String> expectedConcepts,
            String expectedAnswer,
            String correctOption,
            String feedback) {

        public ResponseContract {
            expectedConcepts = expectedConcepts == null ? List.of() : List.copyOf(expectedConcepts);
        }

        public boolean supportsDeterministicEvaluation() {
            return correctOption != null && !correctOption.isBlank();
        }
    }
}
