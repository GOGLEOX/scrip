package com.gogleox.scrip.tax;

import com.gogleox.scrip.account.*;
import com.gogleox.scrip.ledger.AccountLedger;
import com.gogleox.scrip.payment.PaymentService;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Optional server-side consumer; no ChunkLock classes, hooks, or policies are linked here. */
public final class DefaultTaxPaymentPort implements TaxPaymentPort {
    private final AccountLedger ledger;

    public DefaultTaxPaymentPort(AccountLedger ledger) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
    }

    @Override public int apiVersion() { return API_VERSION; }

    @Override public CompletionStage<TaxPaymentResult> pay(TaxPaymentRequest request) {
        Objects.requireNonNull(request, "request");
        Optional<TaxPaymentRecord> prior = ledger.taxPayment(request.invoiceId(), request.taxPeriodId());
        if (prior.isPresent()) {
            return CompletableFuture.completedFuture(prior.get().matches(request)
                    ? result(TaxPaymentResult.Status.DUPLICATE, receipt(prior.get()))
                    : result(TaxPaymentResult.Status.CONFLICT, null));
        }
        Optional<AccountSnapshot> source = ledger.findAccount(request.sourceAccount());
        if (source.isEmpty() || ledger.findAccount(request.taxRecipient()).isEmpty()) {
            return CompletableFuture.completedFuture(result(TaxPaymentResult.Status.UNKNOWN_ACCOUNT, null));
        }
        if (source.get().lifecycle() == AccountLifecycle.FROZEN) {
            return CompletableFuture.completedFuture(result(TaxPaymentResult.Status.FROZEN_ACCOUNT, null));
        }
        if (request.amount() >= PaymentService.CONFIRMATION_THRESHOLD && !request.payerConfirmed()) {
            return CompletableFuture.completedFuture(result(TaxPaymentResult.Status.CONFIRMATION_REQUIRED, null));
        }
        UUID operationId = stableOperationId(request.invoiceId(), request.taxPeriodId());
        return ledger.payTaxInvoice(operationId, request).thenApply(DefaultTaxPaymentPort::map);
    }

    private static TaxPaymentResult map(AccountLedger.TaxPaymentOutcome outcome) {
        TaxPaymentResult.Status status = switch (outcome.status()) {
            case PAID -> TaxPaymentResult.Status.PAID;
            case DUPLICATE -> TaxPaymentResult.Status.DUPLICATE;
            case CONFLICT -> TaxPaymentResult.Status.CONFLICT;
            case CONFIRMATION_REQUIRED -> TaxPaymentResult.Status.CONFIRMATION_REQUIRED;
            case INSUFFICIENT_FUNDS -> TaxPaymentResult.Status.INSUFFICIENT_FUNDS;
            case FROZEN_ACCOUNT -> TaxPaymentResult.Status.FROZEN_ACCOUNT;
            case UNAUTHORIZED -> TaxPaymentResult.Status.UNAUTHORIZED;
            case UNKNOWN_ACCOUNT -> TaxPaymentResult.Status.UNKNOWN_ACCOUNT;
            case POLICY_CHANGED -> TaxPaymentResult.Status.POLICY_CHANGED;
            case INVALID -> TaxPaymentResult.Status.INVALID;
            case STORAGE_FAILURE -> TaxPaymentResult.Status.STORAGE_FAILURE;
        };
        TaxPaymentReceipt receipt = outcome.record() == null ? null : receipt(outcome.record());
        return result(status, receipt);
    }

    private static TaxPaymentResult result(TaxPaymentResult.Status status, TaxPaymentReceipt receipt) {
        return new TaxPaymentResult(status, Optional.ofNullable(receipt));
    }

    private static TaxPaymentReceipt receipt(TaxPaymentRecord record) {
        long total = Math.addExact(record.amount(), record.fee());
        return new TaxPaymentReceipt(record.invoiceId(), record.taxPeriodId(), record.operationId(),
                record.amount(), record.fee(), total, OptionalLong.of(record.sequence()));
    }

    private static UUID stableOperationId(String invoiceId, String periodId) {
        String value = "scrip:tax:v1:" + invoiceId.length() + ":" + invoiceId + ":" + periodId.length() + ":" + periodId;
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
