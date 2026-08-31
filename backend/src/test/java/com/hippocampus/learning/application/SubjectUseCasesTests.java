package com.hippocampus.learning.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hippocampus.identity.domain.AuthenticatedUser;
import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.learning.domain.Subject;
import com.hippocampus.learning.domain.SubjectStatus;
import com.hippocampus.learning.port.DuplicateSubjectNameException;
import com.hippocampus.learning.port.SubjectPage;
import com.hippocampus.learning.port.SubjectPageRequest;
import com.hippocampus.learning.port.SubjectRepository;
import com.hippocampus.shared.application.error.ApplicationNotFoundException;
import com.hippocampus.shared.domain.error.DomainConflictException;

class SubjectUseCasesTests {

    private final UUID ownerId = UUID.randomUUID();
    private final CurrentUser currentUser = () -> new AuthenticatedUser(ownerId);

    @Test
    void createUsesCurrentUserAndMapsDuplicateConflict() {
        FakeSubjects subjects = new FakeSubjects();
        SubjectResult created = new CreateSubject(currentUser, subjects)
                .execute(new CreateSubject.Command("  Anatomy  ", null, null));

        assertThat(created.name()).isEqualTo("  Anatomy  ");
        assertThat(subjects.lastSaved.ownerId()).isEqualTo(ownerId);
        subjects.rejectDuplicate = true;
        assertThatThrownBy(() -> new CreateSubject(currentUser, subjects)
                .execute(new CreateSubject.Command("Anatomy", null, null)))
                .isInstanceOf(DomainConflictException.class)
                .extracting(failure -> ((DomainConflictException) failure).errorCode().value())
                .isEqualTo("SUBJECT_NAME_CONFLICT");
    }

    @Test
    void getUpdateAndArchiveAreOwnerScopedAndPreserveArchivedLifecycle() {
        FakeSubjects subjects = new FakeSubjects();
        Subject archived = subjects.save(Subject.create(ownerId, "Anatomy", null, null)).archive();
        subjects.save(archived);

        assertThat(new GetSubject(currentUser, subjects).execute(archived.id()).status())
                .isEqualTo(SubjectResult.Status.ARCHIVED);
        SubjectResult updated = new UpdateSubject(currentUser, subjects).execute(
                archived.id(), new UpdateSubject.Command("Renamed", "Description", 3));
        assertThat(updated.status()).isEqualTo(SubjectResult.Status.ARCHIVED);
        assertThat(subjects.lastLookupOwner).isEqualTo(ownerId);
        assertThat(new ArchiveSubject(currentUser, subjects).execute(archived.id()).status())
                .isEqualTo(SubjectResult.Status.ARCHIVED);
    }

    @Test
    void missingOwnedSubjectUsesStableNotFoundAndListPassesOwnerAndBounds() {
        FakeSubjects subjects = new FakeSubjects();
        assertThatThrownBy(() -> new GetSubject(currentUser, subjects).execute(UUID.randomUUID()))
                .isInstanceOf(ApplicationNotFoundException.class)
                .extracting(failure -> ((ApplicationNotFoundException) failure).errorCode().value())
                .isEqualTo("SUBJECT_NOT_FOUND");

        SubjectPageResult page = new ListSubjects(currentUser, subjects).execute(2, 25);
        assertThat(page.page()).isEqualTo(2);
        assertThat(subjects.lastLookupOwner).isEqualTo(ownerId);
        assertThat(subjects.lastPageRequest).isEqualTo(new SubjectPageRequest(2, 25));
    }

    private static final class FakeSubjects implements SubjectRepository {
        private final Map<UUID, Subject> stored = new LinkedHashMap<>();
        private Subject lastSaved;
        private UUID lastLookupOwner;
        private SubjectPageRequest lastPageRequest;
        private boolean rejectDuplicate;

        @Override
        public Subject save(Subject subject) {
            if (rejectDuplicate) {
                throw new DuplicateSubjectNameException(new IllegalStateException("duplicate"));
            }
            UUID id = subject.id() == null ? UUID.randomUUID() : subject.id();
            Instant now = Instant.parse("2026-08-30T10:00:00Z");
            Subject persisted = new Subject(id, subject.ownerId(), subject.name(), subject.description(),
                    subject.sortOrder(), subject.status(),
                    subject.createdAt() == null ? now : subject.createdAt(), now);
            stored.put(id, persisted);
            lastSaved = persisted;
            return persisted;
        }

        @Override
        public Optional<Subject> findOwnedById(UUID subjectId, UUID requestedOwnerId) {
            lastLookupOwner = requestedOwnerId;
            return Optional.ofNullable(stored.get(subjectId)).filter(subject -> subject.ownerId().equals(requestedOwnerId));
        }

        @Override
        public SubjectPage findActiveByOwner(UUID requestedOwnerId, SubjectPageRequest pageRequest) {
            lastLookupOwner = requestedOwnerId;
            lastPageRequest = pageRequest;
            var active = new ArrayList<Subject>();
            stored.values().stream()
                    .filter(subject -> subject.ownerId().equals(requestedOwnerId))
                    .filter(subject -> subject.status() == SubjectStatus.ACTIVE)
                    .forEach(active::add);
            return new SubjectPage(active, pageRequest.page(), pageRequest.size(), active.size(), 0);
        }
    }
}
