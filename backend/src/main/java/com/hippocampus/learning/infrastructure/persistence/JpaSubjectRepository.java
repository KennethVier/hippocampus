package com.hippocampus.learning.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Repository;

import com.hippocampus.learning.domain.Subject;
import com.hippocampus.learning.domain.SubjectStatus;
import com.hippocampus.learning.port.DuplicateSubjectNameException;
import com.hippocampus.learning.port.SubjectPage;
import com.hippocampus.learning.port.SubjectPageRequest;
import com.hippocampus.learning.port.SubjectRepository;

@Repository
@Lazy
public class JpaSubjectRepository implements SubjectRepository {

    private static final String SUBJECT_NAME_CONSTRAINT = "uq_subjects_user_lower_name";

    private final SpringDataSubjectRepository subjects;

    public JpaSubjectRepository(SpringDataSubjectRepository subjects) {
        this.subjects = subjects;
    }

    @Override
    public Subject save(Subject subject) {
        SubjectEntity entity = subject.id() == null
                ? new SubjectEntity(subject.ownerId(), subject.name(), toPersistence(subject.status()))
                : subjects.findByIdAndUserId(subject.id(), subject.ownerId()).orElseThrow();
        entity.setName(subject.name());
        entity.setDescription(subject.description());
        entity.setSortOrder(subject.sortOrder());
        entity.setStatus(toPersistence(subject.status()));
        try {
            return toDomain(subjects.saveAndFlush(entity));
        } catch (DataIntegrityViolationException exception) {
            if (isSubjectNameConflict(exception)) {
                throw new DuplicateSubjectNameException(exception);
            }
            throw exception;
        }
    }

    @Override
    public Optional<Subject> findOwnedById(UUID subjectId, UUID ownerId) {
        return subjects.findByIdAndUserId(subjectId, ownerId).map(JpaSubjectRepository::toDomain);
    }

    @Override
    public SubjectPage findActiveByOwner(UUID ownerId, SubjectPageRequest pageRequest) {
        Page<SubjectEntity> page = subjects.findActiveByUserId(ownerId, PageRequest.of(pageRequest.page(), pageRequest.size()));
        return new SubjectPage(
                page.getContent().stream().map(JpaSubjectRepository::toDomain).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    private static boolean isSubjectNameConflict(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolation
                    && SUBJECT_NAME_CONSTRAINT.equals(constraintViolation.getConstraintName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static Subject toDomain(SubjectEntity entity) {
        return new Subject(
                entity.getId(), entity.getUserId(), entity.getName(), entity.getDescription(), entity.getSortOrder(),
                SubjectStatus.valueOf(entity.getStatus().name()), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private static LearningOrganizationStatus toPersistence(SubjectStatus status) {
        return LearningOrganizationStatus.valueOf(status.name());
    }
}
