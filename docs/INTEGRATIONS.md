# $crip | Supported Integration Plans

This file lists concrete sources selected for planning and desired user-facing behavior. P07-B implements the Starter Kit command bridge; other entries remain planned unless explicitly marked otherwise.

## Priority 1: Serilum Starter Kit
- Official: https://www.curseforge.com/minecraft/mc-mods/starter-kit ; exact 1.21.1 release: https://www.curseforge.com/minecraft/mc-mods/starter-kit/files/9259631 ; repository: https://github.com/Serilum/Starter-Kit .
- Universal `starterkit-1.21.1-9.0.jar` tagged Forge/NeoForge. Serilum itself requires Collective, but $crip must not.
- P07-B detects `starterkit` and `collective` independently through each loader's optional-mod registry. Their absence leaves $crip fully operational and prevents kit-triggered grants.
- The verified command-file context is a server command source with the kit recipient as `@s`. In `config/serilum/starterkit/commands/<kit>.txt`, use this line without a leading slash, replacing `250` with the configured amount:

  ```text
  scrip integration reward serilum-starter-kit starter @s 250
  ```

  The amount must exactly equal $crip's durable starter-grant amount and its mode must be `STARTER_KIT`. The bridge rejects console/no-entity context, a command entity that differs from the resolved recipient, an unavailable Starter Kit/Collective pair, a non-`starter` marker, and a mismatched amount. It derives `starter_grant:<player-uuid>` itself; do not use `%s` for identity or try to supply a UUID claim.
- Select exactly one $crip trigger mode. `FIRST_JOIN` pays only from $crip's verified join hook; `STARTER_KIT` pays only after the verified Starter Kit command. Both use the same permanent UUID claim, so reissue, retry, reconnect, restart, rename, reactivation, and future cleanup cannot pay twice.
- Source inspection verifies this command context and the documented `@s`/`%s` behavior. P07-B intentionally has no native Starter Kit dependency or event subscription. A pinned binary pair run with the exact target Starter Kit and Collective artifacts remains a release-matrix requirement before advertising full runtime compatibility.

## Priority 1: FTB Quests
- Project: https://www.curseforge.com/minecraft/mc-mods/ftb-quests-forge ; source: https://github.com/FTBTeam/FTB-Quests .
- Official command reward docs for 1.21.1: https://docs.feed-the-beast.com/mod-docs/1.21.1/mods/suite/Quests/Developer/Quests/Rewards/Command_Reward/ .
- P07-C supports **NeoForge 1.21.1 only** through FTB's Command Reward; it adds no FTB classes or dependency. Forge always rejects the `ftb-quests` source and is not advertised as compatible.
- The verified 1.21.1 Command Reward creates a command source from the claiming `ServerPlayer` and applies the configured permission level. Configure each once-only FTB Command Reward at permission level **2**, with a stable quest/reward ID pair and this command (example IDs and amount only):

  ```text
  /scrip integration reward ftb-quests 570F25BE1914AE96:0F1E2D3C4B5A6978 @s 250
  ```

  Use the immutable FTB quest ID, a colon, and the immutable FTB reward ID. `$crip` resolves `@s` from FTB's verified player command source and creates the durable identity `ftb-quests + player UUID + quest ID + reward ID`; never use `{p}` as an identity and never use a player name in the claim field.
- One-time rewards only: the same configured reward for the same player returns a durable duplicate after retries, restart, rejoin, or rename. Repeatable rewards are unsupported because Command Reward exposes no verified unique completion occurrence to this bridge.
- Team-wide payout is unsupported. The verified FTB reward implementation passes one claiming `ServerPlayer` to the command and provides team values only as command-format variables; it does not enumerate members. Do not assume or configure automatic payment to every team member until a separately approved policy and verified occurrence identity exist.
- Missing FTB Quests leaves the server operational and rejects this source. A pinned FTB Quests NeoForge binary run remains required before representing the runtime pair as fully verified.

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
