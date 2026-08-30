package com.hippocampus.learning.application;

import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.learning.port.SubjectPageRequest;
import com.hippocampus.learning.port.SubjectRepository;

@Service
public class ListSubjects {
    private final CurrentUser currentUser;
    private final SubjectRepository subjects;

    public ListSubjects(CurrentUser currentUser, @Lazy SubjectRepository subjects) {
        this.currentUser = currentUser;
        this.subjects = subjects;
    }

    @Transactional(readOnly = true)
    public SubjectPageResult execute(int page, int size) {
        UUID ownerId = currentUser.authenticatedUser().userId();
        return SubjectPageResult.from(subjects.findActiveByOwner(ownerId, new SubjectPageRequest(page, size)));
    }
}
