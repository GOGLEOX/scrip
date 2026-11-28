package com.gogleox.scrip.storage;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/** Immutable evidence of a verified, consistent local backup. */
public record BackupReceipt(Path directory, long latestSequence, long checkpointSequence, Instant createdAt) {
    public BackupReceipt {
        directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        if (latestSequence < 0 || checkpointSequence < 0 || checkpointSequence > latestSequence) {
            throw new IllegalArgumentException("Invalid backup sequence range");
        }
    }
}
