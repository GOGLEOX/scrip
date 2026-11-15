package com.gogleox.scrip.tax;

import java.util.OptionalLong;
import java.util.UUID;

/** Immutable durable financial result; no property or claim state is represented here. */
public record TaxPaymentReceipt(String invoiceId, String taxPeriodId, UUID operationId,
                                long amount, long fee, long totalDebit, OptionalLong durableSequence) {
}
