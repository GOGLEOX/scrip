# ADR-0003 | Product name and technical identifiers

**Updated:** 2026-11-13  
**Status:** DISPLAY NAME APPROVED; technical identifiers recommended, subject to release validation  
**Supersedes:** Previous proposed display names `ForgeCoin` and `$hillings`.

## Decision

The user selected **`$crip`** as the official branded display name of GOGLEOX Foundry's lightweight Minecraft economy mod. Pronounce it **Scrip**; the initial literal dollar-sign character (`$`) substitutes for the capital `S`. This is a deliberate visual wordmark, not a currency symbol requirement.

## Product and technical identity

| Surface | Name/ID | Status |
|---|---|---|
| Official product display name | `$crip` | **Approved** |
| Human-readable / accessibility fallback | `Scrip` | Recommended |
| Technical mod ID / resource namespace | `scrip` | Recommended, release uniqueness pending |
| Primary command root | `/scrip` | Recommended; collision-check aliases |
| Java package prefix | `com.gogleox.scrip` | Recommended; confirm namespace ownership |
| Config file/folder prefix | `scrip` | Recommended |
| Artifact names | `scrip-1.21.1-forge-vX.Y.Z.jar`, `scrip-1.21.1-neoforge-vX.Y.Z.jar` | Recommended |
| Default in-game currency | `Coins` | **Previously approved, unchanged** |
| Currency symbol | Configurable Unicode coin glyph with `Coins` text fallback | Previously approved, unchanged |

The mod name `$crip` does **not** change the underlying currency default to Scrip. Currency is independently server-configurable.

## Invariants and release checks

1. Use `$crip` for the launcher display name, mod listing, GUI heading, and marketing where permitted. Use ASCII `scrip` in Java identifiers, file paths, resource keys, commands, and build artifacts.
2. Provide a readable `Scrip` fallback for accessibility, fonts lacking `$`, and places where storefront or platform input validation rejects `$crip`.
3. Keep GOGLEOX Foundry's exact tagline: **Forged in Fire, Crafted in Utility.**
4. Retain Minecraft Java 1.21.1/Java 21, dual-loader Forge and NeoForge launch, optional client installation, journal + snapshot persistence, configurable safe stale-account cleanup, FTB Quests, Serilum Starter Kit, ChunkLock, and the future first-party shop API. No approved feature changes.
5. Before listing, check available CurseForge/Modrinth project slugs, package IDs, marketplace name collisions, and trademark concerns. A preliminary web search did not prove an exact Minecraft mod match; uniqueness is not guaranteed.
6. No implementation artifacts have been produced. This is a documentation-level rename only, not a runtime data migration. If legacy names/IDs are later found in an existing implementation, require an explicit migration for saved namespaces and configuration before rebranding a released jar.

## Acceptance tests to add to P00 and release validation

- Both loader descriptors show `$crip` with mod ID `scrip`.
- Commands register `/scrip`, and no command alias silently overwrites another mod's command.
- File/resource/config prefixes use safe ASCII names, not `$`.
- `Coins` remains the default in-game currency until specifically changed.
- Text, GUI, HUD, notifications, and metadata support correct wordmark and fallback.
- Test scripts, CI, documentation and artifact names use current `Scrip_*_v1.4` planning references.

**Status: naming/documentation only; no code, compiled jars or running integrations created.**
