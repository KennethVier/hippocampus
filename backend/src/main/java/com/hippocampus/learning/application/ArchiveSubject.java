package com.hippocampus.learning.application;

import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.learning.domain.Subject;
import com.hippocampus.learning.port.SubjectRepository;

@Service
public class ArchiveSubject {
    private final CurrentUser currentUser;
    private final SubjectRepository subjects;

    public ArchiveSubject(CurrentUser currentUser, @Lazy SubjectRepository subjects) {
        this.currentUser = currentUser;
        this.subjects = subjects;
    }

    @Transactional
    public SubjectResult execute(UUID subjectId) {
        UUID ownerId = currentUser.authenticatedUser().userId();
        Subject subject = subjects.findOwnedById(subjectId, ownerId).orElseThrow(SubjectFailures::notFound);
        return SubjectResult.from(subjects.save(subject.archive()));
    }
}
