package com.gogleox.scrip.account;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A player identity seen and verified by the logical server. */
public record KnownPlayer(UUID playerId, String lastKnownName, Instant lastSeenAt) {
    public KnownPlayer {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(lastKnownName, "lastKnownName");
        Objects.requireNonNull(lastSeenAt, "lastSeenAt");
        if (lastKnownName.isBlank() || lastKnownName.length() > 16) {
            throw new IllegalArgumentException("Player name must contain 1 to 16 characters");
        }
    }
}
