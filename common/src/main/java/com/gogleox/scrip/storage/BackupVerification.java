package com.gogleox.scrip.storage;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Result of validating a backup manifest, every hashed artifact, and recovery data. */
public record BackupVerification(boolean valid, long latestSequence, long checkpointSequence, Instant createdAt,
                                 List<String> files, String detail) {
    public BackupVerification {
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        files = List.copyOf(files);
        detail = Objects.requireNonNull(detail, "detail");
    }
}
