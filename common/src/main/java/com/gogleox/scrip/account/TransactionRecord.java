package com.gogleox.scrip.account;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** One account-facing entry. Each account retains the latest 100 records. */
public record TransactionRecord(UUID operationId, Instant occurredAt, TransactionDirection direction,
                                Optional<AccountId> counterparty, long amount, String description) {
    public TransactionRecord {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(direction, "direction");
        counterparty = counterparty == null ? Optional.empty() : counterparty;
        if (amount <= 0) {
            throw new IllegalArgumentException("Transaction record amount must be positive");
        }
        Objects.requireNonNull(description, "description");
    }
}
