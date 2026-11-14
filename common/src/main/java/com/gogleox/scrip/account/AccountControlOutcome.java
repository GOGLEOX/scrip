package com.gogleox.scrip.account;

import java.util.Optional;
import java.util.UUID;

/** Result of a freeze or unfreeze request. */
public record AccountControlOutcome(Status status, UUID operationId,
                                    Optional<AdministrativeAuditRecord> auditRecord) {
    public enum Status {
        SUCCESS,
        UNKNOWN_ACCOUNT,
        ACCOUNT_TOMBSTONED,
        ALREADY_IN_REQUESTED_STATE,
        OPERATION_CONFLICT,
        STORAGE_FAILURE
    }

    public AccountControlOutcome {
        auditRecord = auditRecord == null ? Optional.empty() : auditRecord;
    }

    public static AccountControlOutcome rejected(Status status, UUID operationId) {
        return new AccountControlOutcome(status, operationId, Optional.empty());
    }

    public static AccountControlOutcome success(UUID operationId, AdministrativeAuditRecord record) {
        return new AccountControlOutcome(Status.SUCCESS, operationId, Optional.of(record));
    }
}
