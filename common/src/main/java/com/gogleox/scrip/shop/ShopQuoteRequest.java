package com.gogleox.scrip.shop;
import com.gogleox.scrip.account.AccountId;
import java.util.*;
/** Price amount must be supplied by trusted server shop logic, never a client packet. */
public record ShopQuoteRequest(UUID actorId, AccountId buyer, AccountId seller, long serverPrice, String priceReference) { public ShopQuoteRequest { Objects.requireNonNull(actorId);Objects.requireNonNull(buyer);Objects.requireNonNull(seller);if(serverPrice<=0)throw new IllegalArgumentException("Price must be positive"); } }
