package com.gogleox.scrip.payment;
import java.util.Optional;
public record PaymentResult(Status status, Optional<PaymentPreview> preview) {
    public enum Status { PREVIEW_READY, EXECUTED, CANCELLED, INVALID_AMOUNT, UNKNOWN_ACCOUNT, UNAUTHORIZED, INSUFFICIENT_FUNDS, FROZEN, EXPIRED, NONCE_USED, POLICY_CHANGED, INVALIDATED, STORAGE_FAILURE, CONFIRMATION_REQUIRED }
    public PaymentResult { preview=preview==null?Optional.empty():preview; }
    public static PaymentResult of(Status status,PaymentPreview preview){return new PaymentResult(status,Optional.ofNullable(preview));}
}
