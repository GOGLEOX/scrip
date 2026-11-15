package com.gogleox.scrip.tax;

import com.gogleox.scrip.account.AccountId;
import java.util.Objects;
import java.util.UUID;

/** Ledger-owned replay record for one immutable invoice/period identity. */
public record TaxPaymentRecord(String invoiceId, String taxPeriodId, UUID actorId,
                                AccountId sourceAccount, AccountId taxRecipient, long amount,
                                long fee, long policyRevision, UUID operationId, long sequence) {
    public TaxPaymentRecord {
        Objects.requireNonNull(invoiceId, "invoiceId");
        Objects.requireNonNull(taxPeriodId, "taxPeriodId");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(sourceAccount, "sourceAccount");
        Objects.requireNonNull(taxRecipient, "taxRecipient");
        Objects.requireNonNull(operationId, "operationId");
    }

    public boolean matches(TaxPaymentRequest request) {
        return invoiceId.equals(request.invoiceId()) && taxPeriodId.equals(request.taxPeriodId())
                && actorId.equals(request.actorId()) && sourceAccount.equals(request.sourceAccount())
                && taxRecipient.equals(request.taxRecipient()) && amount == request.amount();
    }
}
