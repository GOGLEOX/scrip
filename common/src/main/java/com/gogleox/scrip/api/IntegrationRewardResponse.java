package com.gogleox.scrip.api;
import java.util.*;
public record IntegrationRewardResponse(Status status, UUID operationId, OptionalLong durableSequence) { public enum Status { ISSUED, DUPLICATE, CONFLICT, UNAUTHORIZED_SOURCE, INVALID, UNKNOWN_PLAYER, LIMIT_EXCEEDED, STORAGE_FAILURE } public IntegrationRewardResponse { Objects.requireNonNull(status);durableSequence=durableSequence==null?OptionalLong.empty():durableSequence; } }
