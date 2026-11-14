package com.gogleox.scrip.api;
import com.gogleox.scrip.account.*;
import java.util.*;
public record AdministrativeAdjustmentRequest(UUID operationId, UUID actorId, AccountId accountId, long delta, String reason) { public AdministrativeAdjustmentRequest { Objects.requireNonNull(operationId);Objects.requireNonNull(actorId);Objects.requireNonNull(accountId);Objects.requireNonNull(reason); } }
