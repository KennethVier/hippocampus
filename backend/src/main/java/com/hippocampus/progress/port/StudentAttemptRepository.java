package com.hippocampus.progress.port;

import java.util.List;
import java.util.UUID;

import com.hippocampus.progress.domain.StudentAttempt;

public interface StudentAttemptRepository {
    StudentAttempt append(StudentAttempt attempt);

    List<StudentAttempt> findOwnedByActivity(UUID learningActivityId, UUID ownerId);
}
