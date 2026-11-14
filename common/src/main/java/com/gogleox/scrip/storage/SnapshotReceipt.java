package com.gogleox.scrip.storage;

import java.util.Objects;

/** Identifies a checkpoint that has been written, forced and verified. */
public record SnapshotReceipt(long sequence, String snapshotFile) {
    public SnapshotReceipt {
        if (sequence < 0) {
            throw new IllegalArgumentException("Snapshot sequence cannot be negative");
        }
        Objects.requireNonNull(snapshotFile, "snapshotFile");
    }
}
