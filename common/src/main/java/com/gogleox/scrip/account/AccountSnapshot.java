package com.gogleox.scrip.account;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable server-side account view. */
public record AccountSnapshot(AccountId id, UUID ownerId, AccountType type, Balance balance,
                              Instant createdAt, Instant lastLoginAt, Instant lastEconomicActivityAt,
                              AccountLifecycle lifecycle) {
    public AccountSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(balance, "balance");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(lastLoginAt, "lastLoginAt");
        Objects.requireNonNull(lastEconomicActivityAt, "lastEconomicActivityAt");
        Objects.requireNonNull(lifecycle, "lifecycle");
        if (type == AccountType.PERSONAL && ownerId == null) {
            throw new IllegalArgumentException("Personal accounts require a player UUID");
        }
    }
}
