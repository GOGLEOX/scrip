package com.gogleox.scrip.maintenance;

/** One read-only reason why a personal account cannot be considered for future cleanup. */
public enum MaintenanceExclusion {
    ONLINE,
    INSUFFICIENT_INACTIVITY,
    POSITIVE_BALANCE,
    FROZEN,
    TOMBSTONED,
    ORGANIZATION_MEMBER,
    PROTECTED,
    PENDING_SETTLEMENT,
    FINANCIAL_HOLD
}
