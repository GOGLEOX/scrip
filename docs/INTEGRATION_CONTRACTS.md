# $crip | Integration Contracts

**Scope:** Minecraft 1.21.1, Java 21, Forge+NeoForge runtime core.

## P07-A implemented contract

`com.gogleox.scrip.api.EconomyService` is the stable, loader-independent version-1 contract. It exposes immutable UUID/account-ID request and response objects through `CompletionStage`; it never exposes `AccountLedger`, journal storage, or mutable account state. It supports personal/account lookup, organization lookup, bounded history, authorization checks, payment preview/execution, administrator-gated adjustments, and trusted integration reward issuance. `EconomyPostCommitEvent` listeners run only after the underlying forced journal operation has returned a durable receipt.

`TrustedRewardSourceRegistry` is server-owned. An unregistered source, a source over its configured maximum amount, an invalid claim, or an unknown recipient is rejected. Integration reward replay stores the source, claim ID, recipient, amount, durable operation ID, and sequence as one journal operation: an identical retry returns `DUPLICATE`; a changed recipient or amount returns `CONFLICT`, including after restart.

The restricted command bridge is `/scrip integration reward <source> <claim-id> <player> <amount>`. It requires server permission level 2 and accepts only a command-enabled registered source. P07-A provides the bounded built-in `command` source; future optional adapters must register their own server-owned source and do not gain journal access.

## 1. Trust boundary
- Only logical server code can authorize balance-changing operations.
- Plugins/mods must use `EconomyService` and never write journal/snapshot files directly.
- Allowlist reward source IDs, permissions, positive whole-number amounts, rate limits, maximum grant values, and deterministic claim IDs. Player/client commands cannot mint money.
- Reject an idempotency ID reused with different payload. Retrying an identical committed request returns the original result.
- Events fire only after confirmed durable commit and carry no mutable account references.

## 2. Generic reward command bridge
**Proposed shape (not confirmed on third-party syntax):**

```text
/scrip integration reward <source> <claim-id> <player> <amount>
```

- For starter grant, use permanent claim `starter_grant:<player_uuid>` independent of kit ID. First-join and kit-trigger modes share this key.
- Once-only quest reward: world + quest + reward + recipient UUID/team policy. A repeated claim returns the original operation result.
- Repeatable quest rewards: **require an authenticated unique occurrence ID**; static claim ID will either suppress valid repeat or allow duplicate payouts if indiscriminately changed. Until a stable occurrence ID is available, document only once-only payout mode.
- Determine what Serilum and FTB actually substitute for player/UUID/quest IDs in pinned version; don't publish guessed command recipes.

## 3. Serilum Starter Kit integration
Source: https://www.curseforge.com/minecraft/mc-mods/starter-kit/files/9259631 .
- Serilum 1.21.1-9.0 universal jar tagged Forge + NeoForge, requires Collective when Serilum installed.
- P07-B uses Starter Kit's documented per-kit command file. Its verified source path creates a server command source at the recipient entity; the configuration line uses `@s`, never an unverified display-name substitution, and $crip verifies that source entity matches the resolved target.
- Canonical claim key is `starter_grant:<player-uuid>`, calculated by $crip. `FIRST_JOIN` and `STARTER_KIT` are mutually exclusive durable configuration modes; no fallback occurs when Starter Kit or Collective is absent.
- Exact configuration and evidence limitations are maintained in `docs/INTEGRATIONS.md`. No native Serilum event dependency is taken.

## 4. FTB Quests integration
Docs: https://docs.feed-the-beast.com/mod-docs/1.21.1/mods/suite/Quests/Developer/Quests/Rewards/Command_Reward/ .
- P07-C target is **NeoForge 1.21.1 only**; Forge explicitly keeps this source disabled. FTB's verified Command Reward uses `player.createCommandSourceStack()`, then may elevate to the configured permission level. The $crip recipe therefore uses `@s` and requires FTB permission level 2.
- The static command field contains `quest-id:reward-id`; `$crip` validates the hex pair and appends the verified command player's UUID before calling the trusted reward API. This creates a durable once-only identity without trusting FTB's `{p}` display-name formatter.
- FTB's Command Reward obtains team variables but invokes its command once for the supplied claiming player and does not enumerate team members. Team-wide distribution and repeatable rewards remain unsupported policy blockers.
- No native FTB custom reward API is used or inferred. The exact NeoForge binary pair remains a compatibility-matrix release test.

## 5. First-party shop provider
An eventual GOGLEOX shop mod/plug-in will call a **stable neutral port** from the same server-side $crip process or an explicit separate protocol if built for another platform. First version is **API contract + sample consumer**, not shop mod implementation.

```java
// Architectural sketch only.
interface ShopEconomyPort {
    CompletionStage<PaymentQuote> quote(ShopActor actor, AccountId buyer, AccountId seller, long amount);
    CompletionStage<PaymentReservation> reserve(String settlementId, PaymentQuote quote);
    CompletionStage<SettlementResult> settle(String settlementId, DeliveryEvidence delivery);
    CompletionStage<SettlementResult> cancelOrRefund(String settlementId, FailureReason failure);
    CompletionStage<SettlementResult> getSettlement(String settlementId);
}
```

Suggested monotonic states: `QUOTED`, `RESERVED`, `SETTLEMENT_PENDING`, `SETTLED`, `REFUND_PENDING`, `REFUNDED`, `CANCELLED`, `NEEDS_ADMIN_REVIEW`. Fee rules and balance confirmation remain enforced by $crip, not shop. A ledger commit does **not** atomically deliver Minecraft items. Durable settlement state, expiry/recovery and compensating refunds are required. Never presume a plugin (Paper/Bukkit) can invoke the Forge/NeoForge Java interface without an explicit bridge design.

## 6. Future ChunkLock
- Optional tax debit and payout API, not direct storage or required $crip module. Specific ChunkLock loader and version must be pinned before implementation.

## 7. Adapter packaging and release
- Reflection or guarded classloading where appropriate; no target class touched at mod initialization if optional target absent.
- Advertise only tested names, target versions, exact loader, expected config, verified API level and limitation.
- No libraries/repackaged third-party assets copied unless licensed and technically necessary.

## 8. Maintenance integration invariant
- Adapter-visible account IDs remain stable across `ACCOUNT_CLOSED_INACTIVE` closure; known offline UUIDs must still accept payment through safe on-demand reactivation.
- Closing an inactive account cannot delete first-join, kit, quest, reward or settlement claim keys, and cannot invalidate a pending shop settlement.
- No adapter obtains a privileged journal-cleanup or account-purge API. All maintenance is server/console-administrator authorized and auditable.
- A protected integration payout may retry after recovery or compaction without replaying an already committed grant.
