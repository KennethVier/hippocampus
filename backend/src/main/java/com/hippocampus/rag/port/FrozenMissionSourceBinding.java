package com.hippocampus.rag.port;

import java.util.Objects;
import java.util.UUID;

/** Internal lookup binding. Repositories must authorize it against persisted ownership and claim state. */
public record FrozenMissionSourceBinding(UUID missionId,long generation,UUID claimToken) {
    public FrozenMissionSourceBinding {
        Objects.requireNonNull(missionId); Objects.requireNonNull(claimToken);
        if(generation<=0 || missionId.equals(new UUID(0,0)) || claimToken.equals(new UUID(0,0)))
            throw new IllegalArgumentException("Invalid initial source binding");
    }
}
