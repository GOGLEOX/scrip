package com.gogleox.scrip.api;
import com.gogleox.scrip.payment.PaymentPreview;
import java.util.*;
public record TransferPreviewResult(Status status, Optional<PaymentPreview> preview) { public enum Status { READY, UNAUTHORIZED, INVALID, UNKNOWN_ACCOUNT, INSUFFICIENT_FUNDS, FAILURE } public TransferPreviewResult { Objects.requireNonNull(status);preview=preview==null?Optional.empty():preview; } }
