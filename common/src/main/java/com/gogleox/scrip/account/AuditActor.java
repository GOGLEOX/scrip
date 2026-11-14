package com.gogleox.scrip.account;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Authorized principal recorded with an administrative state transition. */
public record AuditActor(Optional<UUID> playerId, String displayName) {
    public AuditActor {
        playerId = playerId == null ? Optional.empty() : playerId;
        Objects.requireNonNull(displayName, "displayName");
        if (displayName.isBlank() || displayName.length() > 64) {
            throw new IllegalArgumentException("Audit actor name must contain 1 to 64 characters");
        }
    }

    public static AuditActor console() {
        return new AuditActor(Optional.empty(), "console");
    }

    public static AuditActor player(UUID playerId, String displayName) {
        return new AuditActor(Optional.of(Objects.requireNonNull(playerId, "playerId")), displayName);
    }
}
