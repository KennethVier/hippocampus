package com.hippocampus.learning.application;

import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.learning.domain.Subject;
import com.hippocampus.learning.port.DuplicateSubjectNameException;
import com.hippocampus.learning.port.SubjectRepository;

@Service
public class CreateSubject {
    private final CurrentUser currentUser;
    private final SubjectRepository subjects;

    public CreateSubject(CurrentUser currentUser, @Lazy SubjectRepository subjects) {
        this.currentUser = currentUser;
        this.subjects = subjects;
    }

    @Transactional
    public SubjectResult execute(Command command) {
        UUID ownerId = currentUser.authenticatedUser().userId();
        try {
            return SubjectResult.from(subjects.save(
                    Subject.create(ownerId, command.name(), command.description(), command.sortOrder())));
        } catch (DuplicateSubjectNameException exception) {
            throw SubjectFailures.nameConflict();
        }
    }

    public record Command(String name, String description, Integer sortOrder) {}
}
