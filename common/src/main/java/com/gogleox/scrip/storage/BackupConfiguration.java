package com.gogleox.scrip.storage;

/** Bounded local-backup scheduling policy. Scheduling itself stays outside the server tick thread. */
public record BackupConfiguration(boolean enabled, int intervalHours, int retentionCount) {
    public static final int DEFAULT_INTERVAL_HOURS = 24;
    public static final int DEFAULT_RETENTION_COUNT = 7;

    public BackupConfiguration {
        if (intervalHours < 1 || intervalHours > 24 * 31) {
            throw new IllegalArgumentException("Backup interval must be between 1 and 744 hours");
        }
        if (retentionCount < 1 || retentionCount > 365) {
            throw new IllegalArgumentException("Backup retention must be between 1 and 365 backups");
        }
    }

    public static BackupConfiguration defaults() {
        return new BackupConfiguration(true, DEFAULT_INTERVAL_HOURS, DEFAULT_RETENTION_COUNT);
    }
}
