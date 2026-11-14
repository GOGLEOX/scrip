package com.gogleox.scrip.storage;

import java.util.Objects;

/** A journal operation paired with its monotonic durable sequence. */
public record JournalEntry(long sequence, JournalOperation operation) {
    public JournalEntry {
        if (sequence < 1) {
            throw new IllegalArgumentException("Sequence must be positive");
        }
        Objects.requireNonNull(operation, "operation");
    }
}
