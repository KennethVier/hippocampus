package com.hippocampus.learning.infrastructure.persistence;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.hippocampus.learning.domain.LearningActivity;
import com.hippocampus.learning.domain.LearningObjective;
import com.hippocampus.learning.domain.MissionMaterial;
import com.hippocampus.learning.domain.StudyMission;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.LockModeType;
import com.hippocampus.learning.port.StudyMissionRepository;

@Repository
@Lazy
public class JpaStudyMissionRepository implements StudyMissionRepository {
    private final SpringDataStudyMissionRepository missions;
    private final SpringDataMissionMaterialRepository materials;
    private final SpringDataLearningObjectiveRepository objectives;
    private final SpringDataLearningActivityRepository activities;
    private final SpringDataActivitySourceReferenceRepository sourceReferences;
    private final EntityManager entityManager;

    public JpaStudyMissionRepository(
            SpringDataStudyMissionRepository missions,
            SpringDataMissionMaterialRepository materials,
            SpringDataLearningObjectiveRepository objectives,
            SpringDataLearningActivityRepository activities,
            SpringDataActivitySourceReferenceRepository sourceReferences, EntityManager entityManager) {
        this.missions = missions;
        this.materials = materials;
        this.objectives = objectives;
        this.activities = activities;
        this.sourceReferences = sourceReferences;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public StudyMission save(StudyMission mission) {
        StudyMissionEntity missionEntity = missions.findById(mission.id())
                .map(existing -> {
                    if (!existing.getUserId().equals(mission.userId())) {
                        throw new IllegalArgumentException("mission owner cannot change");
                    }
                    existing.apply(mission, null);
                    return existing;
                })
                .orElseGet(() -> new StudyMissionEntity(mission, null));
        missions.saveAndFlush(missionEntity);

        for (MissionMaterial material : mission.materials()) {
            MissionMaterialEntity entity = materials.findById(material.id())
                    .map(existing -> {
                        if (!existing.hasFrozenIdentity(mission.id(), material)) {
                            throw new IllegalArgumentException("mission material frozen identity cannot change");
                        }
                        return existing;
                    })
                    .orElseGet(() -> new MissionMaterialEntity(mission.id(), material));
            materials.save(entity);
        }
        materials.flush();

        for (LearningObjective objective : mission.objectives()) {
            LearningObjectiveEntity entity = objectives.findById(objective.id())
                    .map(existing -> {
                        if (!existing.getStudyMissionId().equals(mission.id())) {
                            throw new IllegalArgumentException("objective mission cannot change");
                        }
                        existing.apply(objective);
                        return existing;
                    })
                    .orElseGet(() -> new LearningObjectiveEntity(mission.id(), objective));
            objectives.save(entity);
        }
        objectives.flush();

        for (LearningActivity activity : mission.activities()) {
            LearningActivityEntity entity = activities.findById(activity.id())
                    .map(existing -> {
                        if (!existing.getStudyMissionId().equals(mission.id())) {
                            throw new IllegalArgumentException("activity mission cannot change");
                        }
                        existing.apply(activity);
                        return existing;
                    })
                    .orElseGet(() -> new LearningActivityEntity(mission.id(), activity));
            activities.save(entity);
        }
        activities.flush();

        for (LearningActivity activity : mission.activities()) {
            for (UUID sourceReferenceId : activity.sourceReferenceIds()) {
                var id = new ActivitySourceReferenceId(activity.id(), sourceReferenceId);
                if (!sourceReferences.existsById(id)) {
                    sourceReferences.save(new ActivitySourceReferenceEntity(id));
                }
            }
        }
        sourceReferences.flush();

        missionEntity.apply(mission, mission.currentActivityId());
        missions.saveAndFlush(missionEntity);
        return findOwnedById(mission.id(), mission.userId()).orElseThrow();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StudyMission> findOwnedById(UUID missionId, UUID ownerId) {
        return missions.findByIdAndUserId(missionId, ownerId).map(this::toDomain);
    }

    @Override
    @Transactional
    public Optional<StudyMission> findOwnedByIdForUpdate(UUID missionId, UUID ownerId) {
        return lockedSnapshot(missionId, ownerId);
    }

    @Override
    @Transactional
    public Optional<StudyMission> findFreshOwnedByIdForUpdate(UUID missionId, UUID ownerId) {
        return lockedSnapshot(missionId, ownerId);
    }

    private Optional<StudyMission> lockedSnapshot(UUID missionId, UUID ownerId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("Mission locking requires an effective write transaction");
        }
        // Lock admission precedes aggregate mutation. A retained request context may contain
        // stale or dirty aggregate fields: do not auto-flush them before obtaining committed state.
        FlushModeType previousFlushMode = entityManager.getFlushMode();
        entityManager.setFlushMode(FlushModeType.COMMIT);
        try {
            return missions.findByIdAndUserIdForUpdate(missionId, ownerId).map(mission -> {
                entityManager.refresh(mission, LockModeType.PESSIMISTIC_WRITE);
                return toDomain(mission, true);
            });
        } finally {
            entityManager.setFlushMode(previousFlushMode);
        }
    }

    private StudyMission toDomain(StudyMissionEntity mission) {
        return toDomain(mission, false);
    }

    private StudyMission toDomain(StudyMissionEntity mission, boolean refresh) {
        var missionMaterials = materials.findAllByStudyMissionIdOrderById(mission.getId()).stream()
                .peek(entity -> { if (refresh) entityManager.refresh(entity); })
                .map(MissionMaterialEntity::toDomain)
                .toList();
        var missionObjectives = objectives.findAllByStudyMissionIdOrderById(mission.getId()).stream()
                .peek(entity -> { if (refresh) entityManager.refresh(entity); })
                .map(LearningObjectiveEntity::toDomain)
                .toList();
        var missionActivities = activities.findAllByStudyMissionIdOrderBySequenceNumber(mission.getId()).stream()
                .peek(entity -> { if (refresh) entityManager.refresh(entity); })
                .map(activity -> activity.toDomain(sourceReferences
                        .findAllByLearningActivityId(activity.getId()).stream()
                        .map(link -> link.getId().sourceReferenceId())
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))))
                .toList();
        return new StudyMission(
                mission.getId(), mission.getUserId(), mission.getTopicId(), mission.getSubtopicId(),
                mission.getStatus(), mission.getLearningState(), mission.getGroundingMode(),
                mission.getAvailableTimeMinutes(), mission.getStartedAt(), mission.getCompletedAt(),
                mission.getStoppedAt(), mission.getCurrentActivityId(), missionMaterials,
                missionObjectives, missionActivities, mission.getCreatedAt(), mission.getUpdatedAt());
    }
}
