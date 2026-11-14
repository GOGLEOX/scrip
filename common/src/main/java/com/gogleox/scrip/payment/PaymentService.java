package com.gogleox.scrip.payment;

import com.gogleox.scrip.account.AccountId;
import com.gogleox.scrip.account.TransferOutcome;
import com.gogleox.scrip.ledger.AccountLedger;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/** Server-authoritative preview and confirmation service; confirmations are intentionally never client-created. */
public final class PaymentService {
    public static final long CONFIRMATION_THRESHOLD = 2000;
    private static final int MAX_PENDING = 1000;
    private final AccountLedger ledger; private final Clock clock; private final Map<UUID,PaymentPreview> pending=new HashMap<>();
    public PaymentService(AccountLedger ledger) { this(ledger,Clock.systemUTC()); }
    PaymentService(AccountLedger ledger,Clock clock) { this.ledger=Objects.requireNonNull(ledger);this.clock=Objects.requireNonNull(clock); }
    public synchronized PaymentResult preview(UUID actor,AccountId source,AccountId destination,long amount) {
        expire(); if(pending.size()>=MAX_PENDING)return PaymentResult.of(PaymentResult.Status.STORAGE_FAILURE,null);
        if(amount<=0)return PaymentResult.of(PaymentResult.Status.INVALID_AMOUNT,null);if(!ledger.canInitiatePayment(actor,source))return PaymentResult.of(PaymentResult.Status.UNAUTHORIZED,null);if(ledger.findAccount(destination).isEmpty())return PaymentResult.of(PaymentResult.Status.UNKNOWN_ACCOUNT,null);
        PaymentPolicy policy=ledger.paymentPolicy();long fee,total;try{fee=policy.feeFor(amount);total=Math.addExact(amount,fee);}catch(ArithmeticException exception){return PaymentResult.of(PaymentResult.Status.INSUFFICIENT_FUNDS,null);}if(ledger.findAccount(source).orElseThrow().balance().units()<total)return PaymentResult.of(PaymentResult.Status.INSUFFICIENT_FUNDS,null);
        PaymentPreview preview=new PaymentPreview(UUID.randomUUID(),actor,source,destination,amount,fee,total,policy.revision(),clock.instant().plusSeconds(policy.confirmationSeconds()),amount>=CONFIRMATION_THRESHOLD);pending.put(preview.nonce(),preview);return PaymentResult.of(PaymentResult.Status.PREVIEW_READY,preview);
    }
    public synchronized PaymentResult confirm(UUID actor,UUID nonce) {
        PaymentPreview preview=pending.remove(nonce);if(preview==null)return PaymentResult.of(PaymentResult.Status.NONCE_USED,null);if(!preview.actorId().equals(actor))return PaymentResult.of(PaymentResult.Status.UNAUTHORIZED,null);if(!clock.instant().isBefore(preview.expiresAt()))return PaymentResult.of(PaymentResult.Status.EXPIRED,null);if(ledger.paymentPolicy().revision()!=preview.policyRevision())return PaymentResult.of(PaymentResult.Status.POLICY_CHANGED,null);
        TransferOutcome outcome=ledger.executePayment(UUID.randomUUID(),actor,preview.source(),preview.destination(),preview.amount(),preview.fee(),preview.policyRevision()).toCompletableFuture().join();return PaymentResult.of(map(outcome),preview);
    }
    public synchronized PaymentResult executeWithoutConfirmation(UUID actor,PaymentPreview preview) { if(preview.confirmationRequired())return PaymentResult.of(PaymentResult.Status.CONFIRMATION_REQUIRED,preview);return confirm(actor,preview.nonce()); }
    /** API path: consumes the server-created preview and returns the durable transfer outcome. */
    public synchronized TransferOutcome executeAuthorizedPreview(UUID actor, PaymentPreview preview) {
        Objects.requireNonNull(preview,"preview"); PaymentPreview stored=pending.remove(preview.nonce());
        if(stored==null||!stored.equals(preview)||!stored.actorId().equals(actor)||!clock.instant().isBefore(stored.expiresAt())) return TransferOutcome.rejected(TransferOutcome.Status.UNAUTHORIZED,preview.nonce());
        if(ledger.paymentPolicy().revision()!=stored.policyRevision()) return TransferOutcome.rejected(TransferOutcome.Status.POLICY_CHANGED,preview.nonce());
        return ledger.executePayment(UUID.randomUUID(),actor,stored.source(),stored.destination(),stored.amount(),stored.fee(),stored.policyRevision()).toCompletableFuture().join();
    }
    private void expire(){Instant now=clock.instant();pending.entrySet().removeIf(entry->!now.isBefore(entry.getValue().expiresAt()));}
    private static PaymentResult.Status map(TransferOutcome outcome){return switch(outcome.status()){case SUCCESS->PaymentResult.Status.EXECUTED;case INSUFFICIENT_FUNDS->PaymentResult.Status.INSUFFICIENT_FUNDS;case FROZEN_ACCOUNT->PaymentResult.Status.FROZEN;case POLICY_CHANGED->PaymentResult.Status.POLICY_CHANGED;case UNAUTHORIZED->PaymentResult.Status.UNAUTHORIZED;case UNKNOWN_ACCOUNT->PaymentResult.Status.UNKNOWN_ACCOUNT;case STORAGE_FAILURE->PaymentResult.Status.STORAGE_FAILURE;default->PaymentResult.Status.INVALIDATED;};}
}
