package com.gogleox.scrip.maintenance;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Bounded read-only cleanup eligibility result. */
public record MaintenancePreview(Instant evaluatedAt, int inactivityDays, int scannedAccounts,
                                 int unscannedAccounts, int eligibleAccounts,
                                 Map<MaintenanceExclusion,Integer> excludedByReason) {
    public MaintenancePreview {
        Objects.requireNonNull(evaluatedAt,"evaluatedAt");
        excludedByReason = Map.copyOf(excludedByReason);
        if (inactivityDays < 1 || scannedAccounts < 0 || unscannedAccounts < 0 || eligibleAccounts < 0) throw new IllegalArgumentException("Invalid maintenance preview");
    }
}
