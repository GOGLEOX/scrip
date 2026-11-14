package com.gogleox.scrip.api;
import com.gogleox.scrip.account.AccountId;
import java.util.*;
public record TransferPreviewRequest(UUID actorId, AccountId source, AccountId destination, long amount) { public TransferPreviewRequest { Objects.requireNonNull(actorId);Objects.requireNonNull(source);Objects.requireNonNull(destination); } }
