package com.gogleox.scrip.maintenance;

/** Read-only journal inspection for the reserved compaction controls. */
public record JournalMaintenancePreview(long latestSequence, int replayEntryCount, boolean hasCheckpoint) {
    public JournalMaintenancePreview {
        if (latestSequence < 0 || replayEntryCount < 0) throw new IllegalArgumentException("Invalid journal preview");
    }
}
