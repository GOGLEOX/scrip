package com.gogleox.scrip.storage;

import java.util.List;
import java.util.Optional;

/** Valid checkpoint plus journal entries that must be applied after it. */
public record RecoveryState(Optional<VersionedSnapshot> snapshot, List<JournalEntry> entries,
                            long latestSequence, boolean truncatedTail) {
    public RecoveryState {
        snapshot = snapshot == null ? Optional.empty() : snapshot;
        entries = List.copyOf(entries);
        if (latestSequence < 0) {
            throw new IllegalArgumentException("Latest sequence cannot be negative");
        }
    }
}
