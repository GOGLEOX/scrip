package com.gogleox.scrip.organization;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Durable evidence for consequential organization administration. */
public record OrganizationAuditRecord(long sequence, UUID operationId, OrganizationId organizationId,
                                      OrganizationAuditAction action, UUID actorId, Optional<UUID> subjectId,
                                      String detail, Instant occurredAt) {
    public OrganizationAuditRecord {
        if (sequence < 1) throw new IllegalArgumentException("Audit sequence must be positive");
        Objects.requireNonNull(operationId, "operationId"); Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(action, "action"); Objects.requireNonNull(actorId, "actorId");
        subjectId = subjectId == null ? Optional.empty() : subjectId;
        Objects.requireNonNull(detail, "detail"); Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
