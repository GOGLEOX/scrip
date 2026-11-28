package com.gogleox.scrip.storage;

/** One deterministic, in-memory transformation between adjacent persisted snapshot schemas. */
public interface SnapshotMigration {
    int fromSchemaVersion();

    int toSchemaVersion();

    byte[] migrate(byte[] source);
}
