package com.hippocampus.materials.port;

import java.util.UUID;

public interface MaterialTopicLinkRepository {

    CreateMaterialTopicLinkResult createUserSelectedActive(
            UUID ownerId, UUID topicId, UUID materialId, UUID materialVersionId);
}
