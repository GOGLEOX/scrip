## v1.4 | 2026-11-13 | Approved rename to `$crip`
- The user selected **`$crip`** (pronounced Scrip) to supersede `$hillings` as the official product display name.
- Recommended safe technical identity is `scrip`, command root `/scrip`, package prefix `com.gogleox.scrip`, loader jar prefix `scrip-1.21.1-...`.
- Updated active specification references, development packets and planning handoff documents; ADR-0003 is revised.
- Kept the previously approved in-game currency default `Coins`, journal+snapshot ledger, safe automated cleanup, and every integration target unchanged.
- No source code, builds, runtime migrations or deployment steps performed.

## v1.3 | 2026-11-13 | Former product naming decision (superseded)
- User originally selected `$hillings` to replace the working name `ForgeCoin`; the proposed technical identifier was `shillings`.
- That decision is superseded by the user's v1.4 selection of `$crip`.
- Product scope, journal persistence, account cleanup, and integration plans were not affected.

# Planning document changelog

## v1.2 | 2026-11-12 | Approved architecture and maintenance
- ADR-0001 accepted: replace previously approved embedded SQLite v1 backend with pure-Java in-memory canonical ledger, forced append-only journal, verified atomic snapshots. SQLite future optional.
- Add FC-23: configurable automated stale-account cleanup, explicit manual cleanup commands, and safe journal compaction/verification.
- Add ADR-0002 and detailed `MAINTENANCE_AND_CLEANUP.md`; recommend 365-day inactivity, daily checks, only zero-balance personal accounts without obligations and durable tombstones/claim preservation.
- Rewrite P01 and P10 around journal durability, fault injection, backup and recovery; add P03-M admin preview/config and P10-M safe automated cleanup/compaction.
- Preserve all prior approved product scope and Serilum/FTB/future GOGLEOX shop and ChunkLock integration plans. No code built.

## v1.1 | Research integrations
- Named Serilum Starter Kit, FTB Quests (NeoForge) and future first-party GOGLEOX shop; SQLite still approved while proposed journal pending.

## v1.0 | Initial product planning
- Approved Phases 1–4 requirements, SQLite storage, dual-loader roadmap.
