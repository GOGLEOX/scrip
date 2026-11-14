package com.gogleox.scrip.api;
import java.time.Instant;
import java.util.*;
/** Published only after a durable ledger receipt has been returned. */
public record EconomyPostCommitEvent(Kind kind, UUID operationId, OptionalLong durableSequence, Instant committedAt) { public enum Kind { TRANSFER, ADMIN_ADJUSTMENT, INTEGRATION_REWARD } public EconomyPostCommitEvent { Objects.requireNonNull(kind);Objects.requireNonNull(operationId);durableSequence=durableSequence==null?OptionalLong.empty():durableSequence;Objects.requireNonNull(committedAt); } }
