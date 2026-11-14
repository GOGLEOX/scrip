package com.gogleox.scrip.account;

import java.util.Objects;
import java.util.UUID;

/** Immutable transfer intent. The caller supplies a stable operation ID for retry safety. */
public record TransferRequest(UUID operationId, AccountId source, AccountId destination, long amount) {
    public TransferRequest {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
    }
}
