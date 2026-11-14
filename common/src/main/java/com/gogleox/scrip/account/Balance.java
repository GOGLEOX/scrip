package com.gogleox.scrip.account;

/** Whole-unit value only. No debit, credit, transfer or persistence behavior. */
public record Balance(long units) {
    public Balance {
        if (units < 0) {
            throw new IllegalArgumentException("Balance must be nonnegative");
        }
    }
}
