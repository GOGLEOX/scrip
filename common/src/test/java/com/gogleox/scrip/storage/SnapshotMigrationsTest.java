package com.gogleox.scrip.storage;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SnapshotMigrationsTest {
    @Test void appliesOnlyAnExplicitAdjacentMigrationChain() {
        SnapshotMigration migration = new SnapshotMigration() {
            @Override public int fromSchemaVersion() { return 1; }
            @Override public int toSchemaVersion() { return 2; }
            @Override public byte[] migrate(byte[] source) { return new byte[] {source[0], 2}; }
        };
        VersionedSnapshot migrated = new SnapshotMigrations(2, List.of(migration)).migrate(new VersionedSnapshot(1, 4, new byte[] {1}));
        assertArrayEquals(new byte[] {1, 2}, migrated.state());
        assertThrows(StorageException.class, () -> new SnapshotMigrations(2, List.of()).migrate(new VersionedSnapshot(1, 4, new byte[] {1})));
        assertThrows(StorageException.class, () -> new SnapshotMigrations(2, List.of(migration)).migrate(new VersionedSnapshot(3, 4, new byte[] {1})));
    }
}
