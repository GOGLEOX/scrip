package com.gogleox.scrip.api;
import java.util.Objects;
/** Server-owned registration; a source may set a bounded maximum issuance amount. */
public record TrustedRewardSource(String id, long maximumAmount, boolean commandAllowed) { public TrustedRewardSource { Objects.requireNonNull(id);if(!id.matches("[A-Za-z0-9._:-]+")||maximumAmount<1)throw new IllegalArgumentException("Invalid trusted reward source"); } }
