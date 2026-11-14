package com.gogleox.scrip.api;
import com.gogleox.scrip.account.TransferOutcome;
import java.util.*;
public record EconomyTransactionResult(TransferOutcome.Status status, UUID operationId, OptionalLong durableSequence, boolean idempotent) { public EconomyTransactionResult { Objects.requireNonNull(status); durableSequence=durableSequence==null?OptionalLong.empty():durableSequence; } public static EconomyTransactionResult from(TransferOutcome outcome){return new EconomyTransactionResult(outcome.status(),outcome.operationId(),outcome.durableSequence(),outcome.idempotent());} }
