package com.gogleox.scrip.api;

import com.gogleox.scrip.account.*;
import com.gogleox.scrip.organization.OrganizationSnapshot;
import java.util.*;
import java.util.concurrent.CompletionStage;

/** Version 1 loader-neutral public economy contract. Implementations never expose ledger state. */
public interface EconomyService {
    int API_VERSION = 1;
    int apiVersion();
    CompletionStage<Optional<AccountSnapshot>> personalAccount(UUID playerId);
    CompletionStage<Optional<AccountSnapshot>> account(AccountId accountId);
    CompletionStage<TransferPreviewResult> previewTransfer(TransferPreviewRequest request);
    CompletionStage<EconomyTransactionResult> executeTransfer(TransferExecutionRequest request);
    CompletionStage<EconomyTransactionResult> adjust(AdministrativeAdjustmentRequest request);
    CompletionStage<IntegrationRewardResponse> issueReward(IntegrationRewardRequest request);
    CompletionStage<List<TransactionRecord>> history(AccountId accountId, int limit);
    CompletionStage<Optional<OrganizationSnapshot>> organization(com.gogleox.scrip.organization.OrganizationId id);
    CompletionStage<ActorAuthorization> authorize(UUID actorId, AccountId accountId, EconomyPermission permission);
}
