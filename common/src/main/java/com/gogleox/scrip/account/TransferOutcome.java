package com.gogleox.scrip.account;

import java.util.OptionalLong;
import java.util.UUID;

/** Typed result for a transfer attempt or an idempotent retry. */
public record TransferOutcome(Status status, UUID operationId, OptionalLong durableSequence, boolean idempotent) {
    public enum Status {
        SUCCESS,
        INVALID_AMOUNT,
        UNKNOWN_ACCOUNT,
        ACCOUNT_TOMBSTONED,
        SAME_ACCOUNT,
        INSUFFICIENT_FUNDS,
        DESTINATION_OVERFLOW,
        OPERATION_CONFLICT,
        STORAGE_FAILURE
    }

    public static TransferOutcome rejected(Status status, UUID operationId) {
        return new TransferOutcome(status, operationId, OptionalLong.empty(), false);
    }

    public static TransferOutcome success(UUID operationId, long sequence, boolean idempotent) {
        return new TransferOutcome(Status.SUCCESS, operationId, OptionalLong.of(sequence), idempotent);
    }
}
