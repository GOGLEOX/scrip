package com.gogleox.scrip.maintenance;

/** Durable server maintenance settings. No scheduler or mutation is enabled by this record alone. */
public record MaintenanceConfiguration(boolean enabled, int inactivityDays, int intervalHours, int maximumBatch) {
    public static final int MIN_INACTIVITY_DAYS = 30, MAX_INACTIVITY_DAYS = 3650;
    public static final int MIN_INTERVAL_HOURS = 1, MAX_INTERVAL_HOURS = 168;
    public static final int DEFAULT_BATCH = 100, MAX_BATCH = 1000;
    public MaintenanceConfiguration {
        if (inactivityDays < MIN_INACTIVITY_DAYS || inactivityDays > MAX_INACTIVITY_DAYS) throw new IllegalArgumentException("Inactivity days are out of range");
        if (intervalHours < MIN_INTERVAL_HOURS || intervalHours > MAX_INTERVAL_HOURS) throw new IllegalArgumentException("Cleanup interval is out of range");
        if (maximumBatch < 1 || maximumBatch > MAX_BATCH) throw new IllegalArgumentException("Cleanup batch is out of range");
    }
    public static MaintenanceConfiguration defaults() { return new MaintenanceConfiguration(true,365,24,DEFAULT_BATCH); }
    public MaintenanceConfiguration withEnabled(boolean value) { return new MaintenanceConfiguration(value,inactivityDays,intervalHours,maximumBatch); }
    public MaintenanceConfiguration withInactivityDays(int value) { return new MaintenanceConfiguration(enabled,value,intervalHours,maximumBatch); }
    public MaintenanceConfiguration withIntervalHours(int value) { return new MaintenanceConfiguration(enabled,inactivityDays,value,maximumBatch); }
}
