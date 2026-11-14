package com.gogleox.scrip.account;

import java.util.Objects;

/** One world-local whole-unit currency. */
public record CurrencyDefinition(String displayName) {
    public CurrencyDefinition {
        Objects.requireNonNull(displayName, "displayName");
        if (displayName.isBlank() || displayName.length() > 32) {
            throw new IllegalArgumentException("Currency name must contain 1 to 32 characters");
        }
    }

    public static CurrencyDefinition defaultCurrency() {
        return new CurrencyDefinition("Coins");
    }
}
