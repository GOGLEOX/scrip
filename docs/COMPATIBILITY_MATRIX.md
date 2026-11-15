# $crip | Integration Compatibility Matrix

**As of:** P07-E research on 2026-11-14. Rows marked research are evidence records, not runtime compatibility claims. An adapter changes status only after a pinned-artifact runtime test.

| Target | Forge 1.21.1 file | NeoForge 1.21.1 file | Planned integration type | $crip tested? | Notes |
|---|---|---|---|---|---|
| Serilum Starter Kit 1.21.1-9.0 | Source-verified optional command bridge | Source-verified optional command bridge | Trusted starter grant command with one-time claim | **PARTIAL** | $crip Forge/Neo builds and absent-dependency smoke checks pass; exact Starter Kit+Collective binary pair remains a release-matrix test |
| FTB Quests 1.21.1 | Explicitly unsupported | Source-verified optional command bridge | Once-only Command Reward bridge | **PARTIAL** | Forge rejects `ftb-quests`; NeoForge uses `@s`, ID-pair claim validation, and permission level 2; exact FTB binary run remains required |
| Future GOGLEOX shop | Not developed | Not developed | First-party shop settlement API + sample consumer | **NO** | Not a released mod; no actual shop feature in $crip |
| ChunkLock property tax | No target Forge 1.21.1 artifact verified | No target NeoForge 1.21.1 artifact verified | Loader-neutral `TaxPaymentPort` contract only | **PARTIAL** | Public “Chunk Lock” listing examined: `chunklock-1.20.1-forge-v1.0.0.jar` targets Forge 1.20.1. No compatible 1.21.1 artifact or public tax hook was found, so no compiled adapter or dependency exists. |
| Money & Sign Shops `dicemcmm-1.0.2.jar` | Not published for target | NeoForge, MC 1.21–1.21.1 | **NOT SUPPORTED AS DIRECT ECONOMY ADAPTER** | **NO** | Upstream revision `72502c56625a984d7803339ced2acc9d37a1ab59` exposes direct mutable operations over its own `MoneyWSD`; no provider/delegation API. No binary loaded and no adapter added. |
| DarkQuesting | Research: yes | Research: yes | Research/watchlist | **NO** | Public reward hook unverified |
| Q Shop `qshop-neoforge-1.21.1-1.8.5.jar` | Forge 1.20.1 only; not target Forge | NeoForge, MC 1.21.1 | **NOT SUPPORTED AS DIRECT ECONOMY ADAPTER** | **NO** | Public docs describe custom currencies and wallet data; no documented external provider or delegated settlement API. No binary loaded and no adapter added. |
| SmartShopMode `shopmod-1.8.0-forge-1.21.1.jar` | Forge, MC 1.21.1 | — | **NOT SUPPORTED AS DIRECT ECONOMY ADAPTER** | **NO** | Separate loader file; public docs expose no economy-provider or delegated settlement API. No binary loaded and no adapter added. |
| SmartShopMode `shopmod-1.8.0-neoforge-1.21.1.jar` | — | NeoForge, MC 1.21.1 | **NOT SUPPORTED AS DIRECT ECONOMY ADAPTER** | **NO** | Separate loader file; public docs expose no economy-provider or delegated settlement API. No binary loaded and no adapter added. |

Every transition from Research to Verified requires exact artifact file ID and hash, test command, console log, $crip version, observed result, and limitations. Keep this matrix updated after each development packet.
