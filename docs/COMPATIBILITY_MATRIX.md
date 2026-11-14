# $crip | Integration Compatibility Matrix (Research, Not Tested)

**As of:** user-provided research covering Minecraft 1.21.1. This matrix is planning evidence, not actual $crip compatibility test results. No source code or binaries have been produced.

| Target | Forge 1.21.1 file | NeoForge 1.21.1 file | Planned integration type | $crip tested? | Notes |
|---|---|---|---|---|---|
| Serilum Starter Kit 1.21.1-9.0 | Research: yes (universal jar) | Research: yes (same universal jar) | Trusted starter grant command with one-time claim | **NO** | Serilum requires Collective; optional to $crip |
| FTB Quests 1.21.1 | Research: NOT verified / appears absent | Research: yes | Command Reward bridge; native only if justified | **NO** | Do not advertise FTB integration on Forge 1.21.1 |
| Future GOGLEOX shop | Not developed | Not developed | First-party shop settlement API + sample consumer | **NO** | Not a released mod; no actual shop feature in $crip |
| ChunkLock | Unknown (pin target) | Unknown (pin target) | Generic Economy API and tax-hook contract | **NO** | Needs ChunkLock-side version/hook agreement |
| Money & Sign Shops | Not verified | Research: yes | Conditional provider adapter | **NO** | Its own wallet may prevent true external economy delegation |
| DarkQuesting | Research: yes | Research: yes | Research/watchlist | **NO** | Public reward hook unverified |
| SmartShopMode | Research: yes | Research: yes | Experimental smoke-test only | **NO** | New project; API/maturity gap |

Every transition from Research to Verified requires exact artifact file ID and hash, test command, console log, $crip version, observed result, and limitations. Keep this matrix updated after each development packet.
