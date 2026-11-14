package com.gogleox.scrip.reward;

import com.gogleox.scrip.account.AccountSnapshot;
import com.gogleox.scrip.ledger.AccountLedger;
import java.util.*;

/** Bounded, server-side reward coordinator. Loader adapters must provide only verified events. */
public final class RewardService {
    private static final int MAX_PLAYTIME_PLAYERS = 10_000;
    private final AccountLedger ledger;
    private final Map<UUID, Long> activeSeconds = new HashMap<>();
    public RewardService(AccountLedger ledger) { this.ledger = Objects.requireNonNull(ledger); }

    public RewardResult advancement(UUID player, String advancementId) { return oneTime(player, RewardSource.ADVANCEMENT, "advancement:" + checked(advancementId)); }
    public RewardResult mobKill(UUID player, String entityType, boolean serverValidated, boolean eligibleTarget) {
        if (!serverValidated || !eligibleTarget) return RewardResult.INELIGIBLE;
        return repeatable(player, RewardSource.MOB_KILL, "mob:" + checked(entityType));
    }
    public RewardResult blockMined(UUID player, String blockId, boolean serverValidated, boolean naturalBlock, boolean recentlyPlaced) {
        if (!serverValidated || !naturalBlock || recentlyPlaced) return RewardResult.INELIGIBLE;
        return repeatable(player, RewardSource.BLOCK_MINING, "mining:" + checked(blockId));
    }
    /** Called by a bounded scheduler using observed player activity, never by client packets. */
    public synchronized int recordActivePlaytime(UUID player, long observedActiveSeconds) {
        if (observedActiveSeconds <= 0 || activeSeconds.size() >= MAX_PLAYTIME_PLAYERS && !activeSeconds.containsKey(player)) return 0;
        long seconds = Math.min(3600, observedActiveSeconds);
        activeSeconds.merge(player, seconds, (a,b) -> Math.min(Long.MAX_VALUE, a + b));
        long interval = Math.multiplyExact((long) ledger.rewardConfiguration().activePlaytimeMinutes(), 60);
        if (activeSeconds.get(player) < interval) return 0;
        activeSeconds.merge(player, -interval, Long::sum);
        return repeatable(player, RewardSource.ACTIVE_PLAYTIME, "playtime:" + interval) == RewardResult.ISSUED ? 1 : 0;
    }
    public RewardResult external(ExternalRewardSource source) {
        Objects.requireNonNull(source, "source");
        return oneTime(source.verifiedPlayerId(), RewardSource.EXTERNAL_QUEST_JOB, "external:" + checked(source.identifier()) + ":" + checked(source.completedObjectiveId()));
    }
    public RewardResult starterGrant(UUID player, StarterGrantMode trigger) {
        RewardConfiguration configuration = ledger.rewardConfiguration();
        if (configuration.starterGrantMode() != trigger || configuration.starterGrant() == 0) return RewardResult.DISABLED;
        return issue(player, RewardSource.STARTER_GRANT, configuration.starterGrant(), "starter_grant:" + player);
    }
    private RewardResult oneTime(UUID player, RewardSource source, String event) { return issue(player, source, ledger.rewardConfiguration().amountFor(source), source.name().toLowerCase(Locale.ROOT) + ":" + player + ":" + event); }
    private RewardResult repeatable(UUID player, RewardSource source, String event) { return issue(player, source, ledger.rewardConfiguration().amountFor(source), ""); }
    private RewardResult issue(UUID player, RewardSource source, long amount, String claim) {
        if (amount == 0) return RewardResult.DISABLED;
        Optional<AccountSnapshot> account = ledger.findPersonal(player); if (account.isEmpty()) return RewardResult.UNKNOWN_PLAYER;
        return ledger.issueReward(UUID.randomUUID(), account.get().id(), player, source, amount, claim).toCompletableFuture().join();
    }
    private static String checked(String value) { if (value == null || value.isBlank() || value.length() > 128) throw new IllegalArgumentException("Invalid server reward identifier"); return value; }
}
