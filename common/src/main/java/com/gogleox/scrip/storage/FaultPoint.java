package com.gogleox.scrip.storage;

/** Deliberate failure boundaries used only by persistence tests. */
public enum FaultPoint {
    OPEN_JOURNAL,
    BEFORE_APPEND,
    DURING_APPEND,
    BEFORE_FORCE,
    DURING_FORCE,
    BEFORE_SNAPSHOT_WRITE,
    DURING_SNAPSHOT_WRITE,
    BEFORE_SNAPSHOT_FORCE,
    BEFORE_SNAPSHOT_REPLACEMENT,
    BEFORE_MANIFEST_REPLACEMENT
}
