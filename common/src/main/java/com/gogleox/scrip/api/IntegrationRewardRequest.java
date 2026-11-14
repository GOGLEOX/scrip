package com.gogleox.scrip.api;
import java.util.*;
public record IntegrationRewardRequest(String sourceId, String claimId, UUID recipientId, long amount) { public IntegrationRewardRequest { Objects.requireNonNull(sourceId);Objects.requireNonNull(claimId);Objects.requireNonNull(recipientId); } }
