package com.gogleox.scrip.organization;

import com.gogleox.scrip.account.AccountId;
import com.gogleox.scrip.account.TransferOutcome;
import com.gogleox.scrip.ledger.AccountLedger;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Server-side organization boundary. Callers supply verified server identities, never client-declared roles. */
public final class OrganizationService {
    private final AccountLedger ledger;
    public OrganizationService(AccountLedger ledger) { this.ledger = ledger; }
    public CompletionStage<OrganizationOutcome> create(UUID operationId, String name, UUID ownerId) { return ledger.createOrganization(operationId,name,ownerId); }
    public Optional<OrganizationSnapshot> find(OrganizationId id) { return ledger.findOrganization(id); }
    public List<AccountAccess> accessibleAccounts(UUID playerId) { return ledger.accessibleAccounts(playerId); }
    public CompletionStage<OrganizationOutcome> invite(UUID operationId, OrganizationId id, UUID actorId, UUID inviteeId) { return ledger.inviteMember(operationId,id,actorId,inviteeId); }
    public CompletionStage<OrganizationOutcome> accept(UUID operationId, OrganizationId id, UUID actorId) { return ledger.acceptInvitation(operationId,id,actorId); }
    public CompletionStage<OrganizationOutcome> remove(UUID operationId, OrganizationId id, UUID actorId, UUID subjectId) { return ledger.removeMember(operationId,id,actorId,subjectId); }
    public CompletionStage<OrganizationOutcome> changeRole(UUID operationId, OrganizationId id, UUID actorId, UUID subjectId, OrganizationRole role) { return ledger.changeRole(operationId,id,actorId,subjectId,role); }
    public OrganizationOutcome requestOwnershipTransfer(UUID operationId, OrganizationId id, UUID actorId, UUID candidateId) { return ledger.requestOwnershipTransfer(operationId,id,actorId,candidateId); }
    public CompletionStage<TransferOutcome> deposit(UUID operationId, OrganizationId id, UUID actorId, long amount) { return ledger.depositToOrganization(operationId,id,actorId,amount); }
    public CompletionStage<TransferOutcome> spend(UUID operationId, OrganizationId id, UUID actorId, AccountId destination, long amount) { return ledger.spendOrganizationFunds(operationId,id,actorId,destination,amount); }
}
