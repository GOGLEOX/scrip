package com.gogleox.scrip.organization;

import java.util.Objects;
import java.util.UUID;

/** Stable server-assigned organization identity. */
public record OrganizationId(UUID value) {
    public OrganizationId { Objects.requireNonNull(value, "value"); }
}
