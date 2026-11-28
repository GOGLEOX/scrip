package com.gogleox.scrip.storage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Fail-closed deterministic migration chain for future snapshot schemas. */
public final class SnapshotMigrations {
    private final int currentSchema;
    private final Map<Integer, SnapshotMigration> migrations;

    public SnapshotMigrations(int currentSchema, List<SnapshotMigration> migrations) {
        if (currentSchema < 1) throw new IllegalArgumentException("Schema version must be positive");
        this.currentSchema = currentSchema;
        this.migrations = new HashMap<>();
        for (SnapshotMigration migration : List.copyOf(migrations)) {
            Objects.requireNonNull(migration, "migration");
            if (migration.fromSchemaVersion() < 1 || migration.toSchemaVersion() != migration.fromSchemaVersion() + 1
                    || this.migrations.putIfAbsent(migration.fromSchemaVersion(), migration) != null) {
                throw new IllegalArgumentException("Migrations must form a unique adjacent version chain");
            }
        }
    }

    public int currentSchema() { return currentSchema; }

    public VersionedSnapshot migrate(VersionedSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.schemaVersion() > currentSchema) {
            throw new StorageException("Snapshot schema is newer than this server supports");
        }
        byte[] state = snapshot.state();
        int schema = snapshot.schemaVersion();
        while (schema < currentSchema) {
            SnapshotMigration migration = migrations.get(schema);
            if (migration == null) throw new StorageException("No migration from snapshot schema " + schema);
            state = Objects.requireNonNull(migration.migrate(state), "migration result");
            schema = migration.toSchemaVersion();
        }
        return new VersionedSnapshot(schema, snapshot.sequence(), state);
    }
}
