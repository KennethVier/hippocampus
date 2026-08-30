package com.hippocampus.learning.application;

import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.learning.port.SubjectRepository;

@Service
public class GetSubject {
    private final CurrentUser currentUser;
    private final SubjectRepository subjects;

    public GetSubject(CurrentUser currentUser, @Lazy SubjectRepository subjects) {
        this.currentUser = currentUser;
        this.subjects = subjects;
    }

    @Transactional(readOnly = true)
    public SubjectResult execute(UUID subjectId) {
        UUID ownerId = currentUser.authenticatedUser().userId();
        return subjects.findOwnedById(subjectId, ownerId)
                .map(SubjectResult::from)
                .orElseThrow(SubjectFailures::notFound);
    }
}
