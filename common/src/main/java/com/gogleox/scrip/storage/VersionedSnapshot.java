package com.gogleox.scrip.storage;

import java.util.Arrays;
import java.util.Objects;

/** Opaque application state captured at a known durable journal sequence. */
public record VersionedSnapshot(int schemaVersion, long sequence, byte[] state) {
    public VersionedSnapshot {
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("Schema version must be positive");
        }
        if (sequence < 0) {
            throw new IllegalArgumentException("Snapshot sequence cannot be negative");
        }
        Objects.requireNonNull(state, "state");
        state = Arrays.copyOf(state, state.length);
    }

    @Override
    public byte[] state() {
        return Arrays.copyOf(state, state.length);
    }
}
