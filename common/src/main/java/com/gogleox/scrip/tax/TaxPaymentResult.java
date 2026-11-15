package com.gogleox.scrip.tax;

import java.util.Objects;
import java.util.Optional;

/** Typed response for the external tax authority to decide its own unpaid-tax policy. */
public record TaxPaymentResult(Status status, Optional<TaxPaymentReceipt> receipt) {
    public enum Status {
        PAID,
        DUPLICATE,
        CONFLICT,
        INTEGRATION_DISABLED,
        CONFIRMATION_REQUIRED,
        INSUFFICIENT_FUNDS,
        FROZEN_ACCOUNT,
        UNAUTHORIZED,
        UNKNOWN_ACCOUNT,
        POLICY_CHANGED,
        INVALID,
        STORAGE_FAILURE
    }

    public TaxPaymentResult {
        Objects.requireNonNull(status, "status");
        receipt = receipt == null ? Optional.empty() : receipt;
    }
}
