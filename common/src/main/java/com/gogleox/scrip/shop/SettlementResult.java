package com.gogleox.scrip.shop;
import java.util.*;
public record SettlementResult(Status status, Optional<ShopSettlement> settlement) { public enum Status { QUOTED, RESERVED, SETTLED, CANCELLED, REFUNDED, REQUIRES_RECONCILIATION, CONFIRMATION_REQUIRED, EXPIRED, INVALID, UNAUTHORIZED, INSUFFICIENT_FUNDS, DUPLICATE, NOT_FOUND, STORAGE_FAILURE } public SettlementResult { Objects.requireNonNull(status);settlement=settlement==null?Optional.empty():settlement; } }
