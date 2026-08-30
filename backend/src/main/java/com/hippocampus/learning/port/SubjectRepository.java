package com.hippocampus.learning.port;

import java.util.Optional;
import java.util.UUID;

import com.hippocampus.learning.domain.Subject;

public interface SubjectRepository {
    Subject save(Subject subject);
    Optional<Subject> findOwnedById(UUID subjectId, UUID ownerId);
    SubjectPage findActiveByOwner(UUID ownerId, SubjectPageRequest pageRequest);
}
