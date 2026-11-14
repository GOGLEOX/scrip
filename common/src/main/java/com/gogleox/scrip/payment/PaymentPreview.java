package com.gogleox.scrip.payment;
import com.gogleox.scrip.account.AccountId;
import java.time.Instant;
import java.util.UUID;
public record PaymentPreview(UUID nonce, UUID actorId, AccountId source, AccountId destination, long amount, long fee, long totalDebit, long policyRevision, Instant expiresAt, boolean confirmationRequired) {}
