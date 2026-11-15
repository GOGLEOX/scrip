package com.gogleox.scrip.shop;
import com.gogleox.scrip.payment.PaymentPreview;
import java.util.*;
/** Immutable server-issued price and payment preview; never inventory-delivery evidence. */
public record ShopQuote(UUID quoteId, UUID actorId, PaymentPreview payment, String priceReference) { public ShopQuote { Objects.requireNonNull(quoteId);Objects.requireNonNull(actorId);Objects.requireNonNull(payment);if(priceReference==null||priceReference.isBlank()||priceReference.length()>128)throw new IllegalArgumentException("Invalid server price reference"); } }
