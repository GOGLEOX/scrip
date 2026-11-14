package com.gogleox.scrip.storage;

import java.util.Objects;
import java.util.UUID;

/** Returned only after the framed journal record has been forced to storage. */
public record CommitReceipt(long sequence, UUID operationId) {
    public CommitReceipt {
        if (sequence < 1) {
            throw new IllegalArgumentException("Sequence must be positive");
        }
        Objects.requireNonNull(operationId, "operationId");
    }
}
