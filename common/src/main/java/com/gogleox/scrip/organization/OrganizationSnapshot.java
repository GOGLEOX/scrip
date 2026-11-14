package com.gogleox.scrip.organization;

import com.gogleox.scrip.account.AccountId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable organization view. The owner remains explicit to prevent orphaned balances. */
public record OrganizationSnapshot(OrganizationId id, String name, AccountId accountId, UUID ownerId,
                                   int memberCount, Instant createdAt) {
    public OrganizationSnapshot {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(name, "name");
        Objects.requireNonNull(accountId, "accountId"); Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(createdAt, "createdAt");
        if (name.isBlank() || memberCount < 1) throw new IllegalArgumentException("Invalid organization snapshot");
    }
}
