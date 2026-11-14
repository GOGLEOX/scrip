package com.gogleox.scrip.reward;

import java.util.UUID;

/** Optional adapter contract. Implementations must call this only from verified server callbacks. */
public interface ExternalRewardSource {
    String identifier();
    UUID verifiedPlayerId();
    String completedObjectiveId();
}
