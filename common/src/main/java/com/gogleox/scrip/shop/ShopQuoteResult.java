package com.gogleox.scrip.shop;
import java.util.*;
public record ShopQuoteResult(Status status, Optional<ShopQuote> quote) { public enum Status { READY, CONFIRMATION_REQUIRED, UNAUTHORIZED, INVALID, INSUFFICIENT_FUNDS, FAILURE } public ShopQuoteResult { Objects.requireNonNull(status);quote=quote==null?Optional.empty():quote; } }
