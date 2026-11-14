# $crip | Supported Integration Plans

This file lists concrete sources selected for planning and desired user-facing behavior. **No integration has been implemented or tested.**

## Priority 1: Serilum Starter Kit
- Official: https://www.curseforge.com/minecraft/mc-mods/starter-kit ; exact 1.21.1 release: https://www.curseforge.com/minecraft/mc-mods/starter-kit/files/9259631 ; repository: https://github.com/Serilum/Starter-Kit .
- Universal `starterkit-1.21.1-9.0.jar` tagged Forge/NeoForge. Serilum itself requires Collective, but $crip must not.
- Template uses server-authorized command-on-kit issuing the configured initial Coins grant, with permanent UUID claim marker shared with first join.
- Documentation must include server config modes, real verified command syntax, upgrade/absent-mod behavior, and instructions for disabling double-grant paths.

## Priority 1: FTB Quests
- Project: https://www.curseforge.com/minecraft/mc-mods/ftb-quests-forge ; source: https://github.com/FTBTeam/FTB-Quests .
- Official command reward docs for 1.21.1: https://docs.feed-the-beast.com/mod-docs/1.21.1/mods/suite/Quests/Developer/Quests/Rewards/Command_Reward/ .
- Exact named support target: NeoForge 1.21.1. Forge 1.21.1 FTB Quests version not verified in the supplied research.
- First milestone is documented secure once-only Coin reward via command, not compiled FTB dependency. Later native custom-reward UX is optional if documentation and tests show value.
- Team and repeatable-reward semantics require a separate verified identity and configuration policy.

## Priority 1: Future GOGLEOX shop
- Work on a provider API (`ShopEconomyPort`) and consumer tests only, so the future GOGLEOX shop mod can be started in another repository without migration of Coins.
- Shop owns inventory/items, offers, layout and delivery. $crip owns money, approvals, fees, auth, settlements and refunds. Document API versioning and consumer sample.
- Do not add a `/shop` feature to $crip now.

## Continuing: ChunkLock
- Make taxes optional and server-owned; type/period/payer must be consistent and idempotent. Obtain actual ChunkLock code/API before claiming supported version.

## Conditional targets
- Money & Sign Shops: NeoForge 1.21.1 potential provider, only if it exposes a real external ledger integration. https://modrinth.com/mod/money-sign-shops .
- DarkQuesting: both loaders reported, reward API not confirmed. https://www.curseforge.com/minecraft/mc-mods/darkquesting .
- SmartShopMode: both loaders reported but new and unproven. https://www.curseforge.com/minecraft/mc-mods/smartshopmode .

No other mod or library is a hard dependency of $crip. More adapter targets can be added through separate user decisions and evidence-backed test packets.

## Inactivity cleanup compatibility
- Serilum initial grant `starter_grant:<uuid>` is persistent even after dormant zero-balance account closure and reactivation.
- FTB once-only payout identities and any protected repeatable occurrence IDs cannot be discarded merely because player-visible history rolls over or account closure occurs.
- The future GOGLEOX shop may mark buyer/seller accounts as settlement-protected while unresolved purchase/refund operations exist; those accounts cannot be automatically cleaned.
- Cleaned previously-seen player UUIDs remain eligible offline payees, with durable account reactivation before incoming payment commit.
