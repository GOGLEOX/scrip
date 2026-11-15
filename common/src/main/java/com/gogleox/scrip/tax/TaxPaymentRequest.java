package com.gogleox.scrip.tax;

import com.gogleox.scrip.account.AccountId;
import java.util.Objects;
import java.util.UUID;

/** A server-authenticated invoice request; tax calculation and enforcement remain outside this contract. */
public record TaxPaymentRequest(UUID actorId, AccountId sourceAccount, AccountId taxRecipient,
                                String invoiceId, String taxPeriodId, long amount, boolean payerConfirmed) {
    public TaxPaymentRequest {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(sourceAccount, "sourceAccount");
        Objects.requireNonNull(taxRecipient, "taxRecipient");
        if (!validPart(invoiceId) || !validPart(taxPeriodId)) {
            throw new IllegalArgumentException("Invoice and tax-period identifiers must be bounded server identifiers");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Tax amount must be positive");
        }
    }

    private static boolean validPart(String value) {
        return value != null && value.matches("[A-Za-z0-9._:-]{1,96}");
    }
}
