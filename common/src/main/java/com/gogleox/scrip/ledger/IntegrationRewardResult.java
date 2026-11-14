package com.gogleox.scrip.ledger;

import java.util.OptionalLong;
import java.util.UUID;

/** Immutable result of a durable trusted-integration issuance. */
public record IntegrationRewardResult(Status status, UUID operationId, OptionalLong durableSequence) {
    public enum Status { ISSUED, DUPLICATE, CONFLICT, INVALID, UNKNOWN_PLAYER, STORAGE_FAILURE }
    static IntegrationRewardResult issued(UUID id,long sequence){return new IntegrationRewardResult(Status.ISSUED,id,OptionalLong.of(sequence));}
    static IntegrationRewardResult duplicate(UUID id,long sequence){return new IntegrationRewardResult(Status.DUPLICATE,id,OptionalLong.of(sequence));}
    static IntegrationRewardResult conflict(){return new IntegrationRewardResult(Status.CONFLICT,null,OptionalLong.empty());}
    static IntegrationRewardResult invalid(){return new IntegrationRewardResult(Status.INVALID,null,OptionalLong.empty());}
    static IntegrationRewardResult unknownPlayer(){return new IntegrationRewardResult(Status.UNKNOWN_PLAYER,null,OptionalLong.empty());}
    static IntegrationRewardResult storageFailure(){return new IntegrationRewardResult(Status.STORAGE_FAILURE,null,OptionalLong.empty());}
}
