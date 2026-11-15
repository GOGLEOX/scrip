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
P07-D implements `ShopEconomyPort` version 1 as a stable neutral provider contract for a separate server-side shop product. It exposes `quote`, `reserve`, `settle`, `cancelOrRefund`, and `getSettlement` through immutable asynchronous values. It does not expose catalog, stock, inventory, or GUI behavior.

`ShopQuote` binds authenticated buyer actor, buyer/seller typed accounts, server-provided price reference, calculated fee, policy revision, expiry, and confirmation requirement. A reservation is durable, but deliberately does not lock Coins; settlement revalidates balance, freeze state, organization role, and fee policy. This prevents a reservation from claiming that payment has already occurred.

Durable states are `QUOTED`, `RESERVED`, `SETTLED`, `CANCELLED`, `REFUND_PENDING`, `REFUNDED`, and `REQUIRES_RECONCILIATION`. A stable settlement ID carries stable payment and refund operation IDs. If a crash occurs after payment before the `SETTLED` marker, retry safely observes the idempotent payment operation and completes state recovery. A failed external delivery after payment enters `REFUND_PENDING`, then issues a compensating amount-and-fee refund; insufficient seller funds leave `REQUIRES_RECONCILIATION` for operator recovery.

Payment confirmation remains server-authoritative: a confirmation-required quote cannot reserve without the authenticated server consumer declaring its buyer-confirmed step. A real shop must acquire that approval through its own server-side interaction; no client flag or inventory callback is trusted directly by $crip. A successful Coin debit is never inventory-delivery evidence, and there is no cross-system atomicity claim.

## 6. Future ChunkLock
- Optional tax debit and payout API, not direct storage or required $crip module. Specific ChunkLock loader and version must be pinned before implementation.

## 7. Third-party shop adapter eligibility
- A third-party shop is eligible only when its pinned target artifact documents a server-side external-provider boundary that delegates balance authority and has a recovery-safe settlement lifecycle. The adapter must retain `$crip` as the sole wallet and use `ShopEconomyPort` or an equivalently strict server-side boundary.
- A mutable internal wallet API, shop command surface, capability, event, or scripting hook is not by itself an economy-provider API. $crip will not mirror balances, mutate a third-party wallet, or synchronize two balance stores.
- P07-E records Money & Sign Shops, Q Shop, and SmartShopMode as **NOT SUPPORTED AS DIRECT ECONOMY ADAPTER**. Their inspected public surfaces do not document the required delegation boundary. See `docs/INTEGRATIONS.md` and `docs/COMPATIBILITY_MATRIX.md` for pinned research targets and limitations.

## 8. Adapter packaging and release
- Reflection or guarded classloading where appropriate; no target class touched at mod initialization if optional target absent.
- Advertise only tested names, target versions, exact loader, expected config, verified API level and limitation.
- No libraries/repackaged third-party assets copied unless licensed and technically necessary.

## 9. Maintenance integration invariant
- Adapter-visible account IDs remain stable across `ACCOUNT_CLOSED_INACTIVE` closure; known offline UUIDs must still accept payment through safe on-demand reactivation.
- Closing an inactive account cannot delete first-join, kit, quest, reward or settlement claim keys, and cannot invalidate a pending shop settlement.
- No adapter obtains a privileged journal-cleanup or account-purge API. All maintenance is server/console-administrator authorized and auditable.
- A protected integration payout may retry after recovery or compaction without replaying an already committed grant.
