# $crip | Architectural Decisions

## Approved
- Phases 1–4 product baseline in master specification; Minecraft 1.21.1, Java 21, Forge+NeoForge first release; optional client installation and full existing economy functionality unchanged.
- **ADR-0001 ACCEPTED 2026-11-12:** Snapshot + durable append-only journal as v1 backend. Previous Phase 4 SQLite selection is superseded. SQLite is only a possible future optional backend.
- **ADR-0002 CAPABILITY ACCEPTED 2026-11-12:** Adjustable automatic stale-account cleanup and admin cleanup/journal commands; exact safety defaults recommended below.
- Serilum Starter Kit Forge+NeoForge, FTB Quests NeoForge 1.21.1, future GOGLEOX shop provider API, ChunkLock integration objective remain planned, not implemented.
- One-time starter/reward claims must survive cleanup; 100 visible transaction records per account do not set journal or claims retention.
- Private development until all v1 features pass real tests. P00 scaffold now exists; no economy implementation exists.

## Recommended maintenance safety defaults [not independently user-selected]
- Inactive for 365 days, auto check every 24 hours, zero-balance offline `PLAYER` accounts only.
- No automatic deletion of nonzero balances, frozen/held/settlement-related accounts, org accounts or organization-linked personal accounts.
- Forced durable account closure with claim-preserving tombstone; known UUID remains offline-payable. Safe compaction after validated snapshot and verified recovery point.
- Manual `preview/run/status/set` commands, journal `compact/verify` commands, backed-up bounded work, operator audit.

## Recommended architecture [not independent product approvals]
- `LedgerStore` interface with only journal/snapshot implemented for v1; Java NIO I/O, one bounded writer, replay tests, snapshot manifests and backup.
- Serilum command-on-kit recipe and immutable starter-grant identity; FTB Quests command reward on NeoForge; future shop settlement API and refund recovery; all adapters optional.
- P01 journal proof must precede P02 economic state. P03-M introduces maintenance controls, P10-M implements actual automated cleanup and compaction.

## Pending implementation policy decisions
- Fee destination (burn vs treasury), organization dissolution, ownership succession and account limits.
- FTB Quests team vs player reward, repeatable payout identity; Serilum fallback policy.
- Whether a separately retained audit register is needed beyond player-visible 100 history entries and required claims/settlements.
- Supported OS/filesystem and filesystem atomic-rename fallback; backup schedule/retention.
- Explicit nonzero-balance forfeiture/sweep requires separate user authorization and is not in v1.

## ADR-0003 | Product naming (UPDATED 2026-11-13; APPROVED DISPLAY NAME)
- Official marketing/display name: `$crip` (Scrip), superseding `$hillings`, with `$` substituting for capital S.
- Recommended accessible text fallback: `Scrip`.
- Proposed safe technical identity: `scrip` mod ID, resource namespace, file prefix and `/scrip` command.
- Existing world currency configuration default `Coins` is unchanged; currency renaming was not requested.
- Scope: documentation naming, integration/command surface, future branding. No code or saves to migrate.
- See `docs/ADR-0003-PRODUCT-NAMING.md` for release checks.

**Previous naming selection:** `$hillings` was planning-only and is superseded. No persistent data migration is needed absent implementation.

## P00 implementation decisions | 2026-11-13

- The explicit $crip task and master specification govern this workspace. Earlier Worldloom instructions describe a separate project; no World State Bus or simulation architecture is introduced here.
- Implement only `common`, `forge`, and `neoforge`. Package shared compiled Java classes inside each loader jar; the common jar is not a third required runtime mod. No cross-loader or third-party integration dependencies.
- Pin Java 21, Minecraft 1.21.1, Gradle 8.14 with SHA-256 verification, Forge 52.0.30 / ForgeGradle 6.0.36, and NeoForge 21.1.172 / ModDevGradle 2.0.107. These are reproducible development baselines, not claims about the newest supported versions. The initial Forge 52.0.28 probe exposed its NightConfig shutdown hang; 52.0.30 includes the upstream fix (#10215), so it is also the minimum supported Forge version.
- `gradle.properties:mod_version` is the only product-version authority. P00 uses `0.1.0-p00`, distinct from a completed v1 release. Both metadata descriptors and artifact names derive from it.
- Technical identity `scrip` is now explicitly authorized. Display name is the literal `$crip`; `Coins` remains the default currency identity. Java package follows the specification's `com.gogleox.scrip` recommendation; ownership/public-listing review remains a release gate.
- P00 domain values are an opaque UUID `AccountId` and a nonnegative `long` `Balance`; no arithmetic, account allocation, persistence or authority decisions. `EconomyFacade` is an empty reserved boundary. `LedgerStore` belongs to P01 and is deliberately not implemented.
- Empty networking is the early optional-client baseline. Forge uses `IGNORE_ALL_VERSION` display testing. NeoForge registers no required custom payloads. No rendering/client classes or gameplay registries are linked from either entry point. This does not waive future P08 capability checks.
- A pinned test-only vanilla protocol client probes play login and reconnect without a client mod. This is narrower evidence than a graphical vanilla client or the future full client/server matrix.
- Local test-server EULA acceptance was explicitly authorized. Offline smoke servers bind only to loopback and use ignored fixture worlds. No public hosting or release is authorized.
- Project metadata follows the linked repository's existing MIT license and identifies it as MIT in both loader descriptors.
- No persisted format, journal schema or save migration is introduced in P00. Do not begin P01 until the P00 gate is resolved and the user authorizes further work.
