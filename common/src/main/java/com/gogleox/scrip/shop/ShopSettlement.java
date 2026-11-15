package com.gogleox.scrip.shop;
import com.gogleox.scrip.account.AccountId;
import java.time.Instant;
import java.util.*;
/** Durable payment state; inventory delivery is deliberately outside this contract. */
public record ShopSettlement(String id, UUID actorId, AccountId buyer, AccountId seller, long amount, long fee, long policyRevision, Instant expiresAt, UUID paymentOperationId, UUID refundOperationId, SettlementState state) { public ShopSettlement { Objects.requireNonNull(id);Objects.requireNonNull(actorId);Objects.requireNonNull(buyer);Objects.requireNonNull(seller);Objects.requireNonNull(expiresAt);Objects.requireNonNull(paymentOperationId);Objects.requireNonNull(refundOperationId);Objects.requireNonNull(state);if(id.isBlank()||id.length()>96||amount<=0||fee<0||policyRevision<0)throw new IllegalArgumentException("Invalid settlement"); } public ShopSettlement withState(SettlementState next){return new ShopSettlement(id,actorId,buyer,seller,amount,fee,policyRevision,expiresAt,paymentOperationId,refundOperationId,next);} }
