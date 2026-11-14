package com.gogleox.scrip.account;

import java.util.Objects;
import java.util.UUID;

/** Opaque account identity, independent of a mutable display name. */
public record AccountId(UUID value) {
    public AccountId {
        Objects.requireNonNull(value, "value");
    }
}
