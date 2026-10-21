package com.hippocampus.learning.port;

import com.hippocampus.learning.domain.FollowUpIntent;
import java.util.List;
import java.util.UUID;

/** Writes require the caller's owned-mission lock and transaction. Terminal rows are immutable. */
public interface FollowUpIntentRepository {
    List<FollowUpIntent> findOwned(UUID missionId, UUID ownerId);
    void insert(FollowUpIntent intent);
    void update(FollowUpIntent previous, FollowUpIntent updated);
}
