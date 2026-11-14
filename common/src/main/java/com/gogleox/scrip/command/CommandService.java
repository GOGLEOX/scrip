package com.gogleox.scrip.command;

import com.gogleox.scrip.account.AccountControlOutcome;
import com.gogleox.scrip.account.AccountSnapshot;
import com.gogleox.scrip.account.AdminAdjustmentRequest;
import com.gogleox.scrip.account.AdministrativeAuditRecord;
import com.gogleox.scrip.account.AuditActor;
import com.gogleox.scrip.account.TransferOutcome;
import com.gogleox.scrip.account.TransferRequest;
import com.gogleox.scrip.account.TransactionRecord;
import com.gogleox.scrip.ledger.AccountLedger;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Loader-neutral command operations; adapters must supply server-verified identities. */
public final class CommandService {
    private final AccountLedger ledger;

    public CommandService(AccountLedger ledger) { this.ledger = ledger; }
    public Optional<AccountSnapshot> balance(UUID playerId) { return ledger.findPersonal(playerId); }
    public Optional<AccountSnapshot> balanceByVerifiedPlayer(UUID playerId) { return ledger.findPersonal(playerId); }
    public TransferOutcome pay(UUID senderId, UUID recipientId, long amount, UUID operationId) {
        Optional<AccountSnapshot> sender = balance(senderId), recipient = balanceByVerifiedPlayer(recipientId);
        if (sender.isEmpty() || recipient.isEmpty()) return TransferOutcome.rejected(TransferOutcome.Status.UNKNOWN_ACCOUNT, operationId);
        return ledger.transfer(new TransferRequest(operationId, sender.get().id(), recipient.get().id(), amount)).toCompletableFuture().join();
    }
    public List<TransactionRecord> history(UUID playerId) { return balance(playerId).map(account -> ledger.history(account.id())).orElse(List.of()); }
    public TransferOutcome adjust(UUID targetId, long delta, String reason, AuditActor actor, UUID operationId) {
        return balanceByVerifiedPlayer(targetId).map(account -> ledger.adjustAdministratively(new AdminAdjustmentRequest(operationId, account.id(), delta, reason, actor)).toCompletableFuture().join()).orElseGet(() -> TransferOutcome.rejected(TransferOutcome.Status.UNKNOWN_ACCOUNT, operationId));
    }
    public AccountControlOutcome setFrozen(UUID targetId, boolean frozen, String reason, AuditActor actor, UUID operationId) {
        return balanceByVerifiedPlayer(targetId).map(account -> ledger.setFrozen(operationId, account.id(), frozen, actor, reason).toCompletableFuture().join()).orElseGet(() -> AccountControlOutcome.rejected(AccountControlOutcome.Status.UNKNOWN_ACCOUNT, operationId));
    }
    public List<AdministrativeAuditRecord> audit(UUID targetId) { return balanceByVerifiedPlayer(targetId).map(account -> ledger.administrativeAudit(account.id())).orElse(List.of()); }
}
