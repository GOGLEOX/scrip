package com.gogleox.scrip.storage;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/** Immutable, loader-neutral input to the durable journal. */
public record JournalOperation(UUID operationId, String type, byte[] payload) {
    public JournalOperation {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(payload, "payload");
        if (type.isBlank() || type.length() > 128) {
            throw new IllegalArgumentException("Operation type must contain 1 to 128 characters");
        }
        if (payload.length > 1_000_000) {
            throw new IllegalArgumentException("Operation payload exceeds the journal record limit");
        }
        payload = Arrays.copyOf(payload, payload.length);
    }

    @Override
    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }
}
