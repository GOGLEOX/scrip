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
import com.gogleox.scrip.payment.PaymentResult;
import com.gogleox.scrip.payment.PaymentService;
import com.gogleox.scrip.reward.RewardService;
import com.gogleox.scrip.reward.StarterGrantMode;
import com.gogleox.scrip.maintenance.JournalMaintenancePreview;
import com.gogleox.scrip.maintenance.MaintenanceConfiguration;
import com.gogleox.scrip.maintenance.MaintenancePreview;
import com.gogleox.scrip.api.*;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Loader-neutral command operations; adapters must supply server-verified identities. */
public final class CommandService {
    private final AccountLedger ledger;
    private final PaymentService payments;
    private final RewardService rewards;
    private final TrustedRewardSourceRegistry trustedRewardSources;
    private final DefaultEconomyService economy;
    private volatile boolean starterKitCompatible;
    private volatile boolean ftbQuestsCompatible;

    public CommandService(AccountLedger ledger) { this.ledger = ledger; this.payments=new PaymentService(ledger); this.rewards=new RewardService(ledger); this.trustedRewardSources=new TrustedRewardSourceRegistry();this.trustedRewardSources.register(new TrustedRewardSource("command",100_000,true));this.trustedRewardSources.register(new TrustedRewardSource("ftb-quests",100_000,true));this.economy=new DefaultEconomyService(ledger,trustedRewardSources,ignored->false); }
    public void playerJoined(UUID playerId) { rewards.starterGrant(playerId, StarterGrantMode.FIRST_JOIN); }
    /** Loader adapters set this from their own optional-mod discovery; the common module has no Serilum dependency. */
    public void setStarterKitCompatible(boolean compatible) { starterKitCompatible=compatible; }
    /** FTB Quests is NeoForge-only in the supported 1.21.1 matrix. */
    public void setFtbQuestsCompatible(boolean compatible) { ftbQuestsCompatible=compatible; }
    public Optional<AccountSnapshot> balance(UUID playerId) { return ledger.findPersonal(playerId); }
    public Optional<AccountSnapshot> balanceByVerifiedPlayer(UUID playerId) { return ledger.findPersonal(playerId); }
    /** Returns an account only when the authenticated actor may view it. */
    public Optional<AccountSnapshot> accountForActor(UUID actorId, com.gogleox.scrip.account.AccountId accountId) {
        return ledger.accessibleAccounts(actorId).stream().anyMatch(access -> access.accountId().equals(accountId))
                ? ledger.findAccount(accountId) : Optional.empty();
    }
    /** Uses server-side permission checks before creating a payment preview. */
    public PaymentResult previewPayment(UUID actorId, com.gogleox.scrip.account.AccountId source, com.gogleox.scrip.account.AccountId destination, long amount) {
        return payments.preview(actorId, source, destination, amount);
    }
    /** Bounded account history after server-side view authorization. */
    public List<TransactionRecord> historyForActor(UUID actorId, com.gogleox.scrip.account.AccountId accountId, int limit) {
        if (limit < 1 || limit > 100 || accountForActor(actorId, accountId).isEmpty()) return List.of();
        return ledger.history(accountId).stream().limit(limit).toList();
    }
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
    public PaymentResult previewPersonalPayment(UUID actorId,UUID recipientId,long amount) { return balance(actorId).flatMap(source -> balanceByVerifiedPlayer(recipientId).map(destination -> payments.preview(actorId,source.id(),destination.id(),amount))).orElseGet(()->PaymentResult.of(PaymentResult.Status.UNKNOWN_ACCOUNT,null)); }
    public PaymentResult executePreview(UUID actorId,com.gogleox.scrip.payment.PaymentPreview preview) { return payments.executeWithoutConfirmation(actorId,preview); }
    public PaymentResult confirmPayment(UUID actorId,UUID nonce) { return payments.confirm(actorId,nonce); }
    public PaymentResult cancelPaymentPreview(UUID actorId, UUID nonce) { return payments.cancel(actorId, nonce); }
    public MaintenanceConfiguration maintenanceConfiguration() { return ledger.maintenanceConfiguration(); }
    public MaintenanceConfiguration updateMaintenanceConfiguration(UUID operationId, MaintenanceConfiguration configuration) { return ledger.updateMaintenanceConfiguration(operationId,configuration).toCompletableFuture().join(); }
    public MaintenancePreview previewMaintenance(int inactivityDays, Collection<UUID> onlinePlayers) { return ledger.previewMaintenance(inactivityDays,onlinePlayers); }
    public JournalMaintenancePreview journalMaintenancePreview() { return ledger.journalMaintenancePreview(); }
    /** Command adapter has already authenticated an administrator; only allow registered command-enabled sources. */
    public IntegrationRewardResponse integrationReward(String source,String claimId,UUID recipient,long amount) { return trustedRewardSources.find(source).filter(TrustedRewardSource::commandAllowed).map(ignored->economy.issueReward(new IntegrationRewardRequest(source,claimId,recipient,amount)).toCompletableFuture().join()).orElseGet(()->new IntegrationRewardResponse(IntegrationRewardResponse.Status.UNAUTHORIZED_SOURCE,null,java.util.OptionalLong.empty())); }
    /** Executes only from Starter Kit's server command source after its player entity was independently resolved. */
    public IntegrationRewardResponse starterKitGrant(UUID commandPlayer, UUID recipient, String claimId, long configuredAmount) {
        if(!starterKitCompatible || !commandPlayer.equals(recipient) || !"starter".equals(claimId)) return new IntegrationRewardResponse(IntegrationRewardResponse.Status.UNAUTHORIZED_SOURCE,null,java.util.OptionalLong.empty());
        if(ledger.rewardConfiguration().starterGrant()!=configuredAmount) return new IntegrationRewardResponse(IntegrationRewardResponse.Status.INVALID,null,java.util.OptionalLong.empty());
        return switch(rewards.starterGrant(recipient,StarterGrantMode.STARTER_KIT)) { case ISSUED -> new IntegrationRewardResponse(IntegrationRewardResponse.Status.ISSUED,null,java.util.OptionalLong.empty()); case DUPLICATE -> new IntegrationRewardResponse(IntegrationRewardResponse.Status.DUPLICATE,null,java.util.OptionalLong.empty()); case DISABLED -> new IntegrationRewardResponse(IntegrationRewardResponse.Status.INVALID,null,java.util.OptionalLong.empty()); case UNKNOWN_PLAYER -> new IntegrationRewardResponse(IntegrationRewardResponse.Status.UNKNOWN_PLAYER,null,java.util.OptionalLong.empty()); default -> new IntegrationRewardResponse(IntegrationRewardResponse.Status.STORAGE_FAILURE,null,java.util.OptionalLong.empty()); };
    }
    /** FTB Command Reward source is the claiming player; quest and reward IDs form the static part of an once-only claim. */
    public IntegrationRewardResponse ftbQuestReward(UUID commandPlayer, UUID recipient, String questRewardId, long amount) {
        if(!ftbQuestsCompatible || !commandPlayer.equals(recipient) || questRewardId==null || !questRewardId.matches("[0-9A-Fa-f]{1,16}:[0-9A-Fa-f]{1,16}")) return new IntegrationRewardResponse(IntegrationRewardResponse.Status.UNAUTHORIZED_SOURCE,null,java.util.OptionalLong.empty());
        String[] ids=questRewardId.split(":",-1);String claim="quest:"+ids[0].toLowerCase(java.util.Locale.ROOT)+":reward:"+ids[1].toLowerCase(java.util.Locale.ROOT)+":player:"+recipient;
        return integrationReward("ftb-quests",claim,recipient,amount);
    }
}
