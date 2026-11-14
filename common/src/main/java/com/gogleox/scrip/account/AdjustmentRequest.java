package com.gogleox.scrip.account;

import java.util.Objects;
import java.util.UUID;

/** Internal service request reserved for future authorized server-side adjustments. */
public record AdjustmentRequest(UUID operationId, AccountId account, long delta, String reason) {
    public AdjustmentRequest {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(reason, "reason");
        if (delta == 0 || reason.isBlank() || reason.length() > 128) {
            throw new IllegalArgumentException("Adjustment requires a nonzero delta and a bounded reason");
        }
    }
}
