package com.gogleox.scrip.account;

import java.util.Objects;
import java.util.UUID;

/** An administrative balance transition with an attributable authorized actor. */
public record AdminAdjustmentRequest(UUID operationId, AccountId account, long delta,
                                     String reason, AuditActor actor) {
    public AdminAdjustmentRequest {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(actor, "actor");
        if (delta == 0 || reason.isBlank() || reason.length() > 128) {
            throw new IllegalArgumentException("Administrative adjustment requires a nonzero bounded reason");
        }
    }
}
