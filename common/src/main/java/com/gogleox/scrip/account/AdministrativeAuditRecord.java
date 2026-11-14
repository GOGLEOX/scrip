package com.gogleox.scrip.account;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Bounded, durable evidence for a server-authorized administrative action. */
public record AdministrativeAuditRecord(long sequence, UUID operationId, AdministrativeAction action,
                                        AccountId account, AuditActor actor, long amount,
                                        String reason, Instant occurredAt) {
    public AdministrativeAuditRecord {
        if (sequence < 1 || amount < 0) {
            throw new IllegalArgumentException("Audit sequence and amount must be nonnegative");
        }
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
