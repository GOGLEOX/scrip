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

## P01 persistence decisions | 2026-11-13

- `LedgerStore` is now the only persistence boundary for future monetary operations. Its P01 operations use opaque immutable payloads because account, claim, fee, and settlement schemas are not authorized until later packets.
- Journal format version 1 uses a length-prefixed frame containing a magic value, format version, positive sequence, UUID operation ID, UTF-8 operation type, bounded payload, and CRC-32 checksum. Replaying duplicate operation IDs, non-increasing sequences, invalid sizes, and invalid checksums fails closed.
- Journal writes use a single bounded writer and `FileChannel.force(true)`. A commit receipt is completed only after force succeeds. A failure after a write begins marks the running instance recovery-required, preventing further acknowledgements until a restart/recovery pass.
- Snapshot format version 1 and a checksummed checkpoint manifest are written through forced temporary files and published with atomic move where available, falling back to replacement move only when the platform cannot provide atomic move. Snapshot filenames carry their checkpoint sequence, so a previous verified snapshot remains available while a later checkpoint is prepared.
- A torn final journal frame is quarantined by truncating only that incomplete tail and forcing the repaired file. Corruption in a complete record, snapshot, or manifest is an operator-visible storage failure; no empty ledger is created.
- P01 adds no account, balance, claim, command, configuration, cleanup, or journal-deletion behavior. P10 remains responsible for backup, compaction and retention. P02 owns account operation payloads and transaction semantics.

## P02 account and transaction decisions | 2026-11-13

- Personal accounts use player UUID as the durable account identity. A verified name is a mutable registry attribute and is never an account key.
- `AccountLedger` owns all balance changes and uses `LedgerStore` as its only persistence boundary. It validates a request, commits one immutable operation, waits for the durable receipt, then changes both in-memory balances together.
- A transfer has a caller-supplied UUID operation identifier. Exact retries return the original receipt; reuse with different source, destination, or amount is rejected. Restart recovery rebuilds this information from the journal.
- One named currency is established by the first durable account-ledger record for a world. Reopening with another name fails rather than silently changing currency semantics.
- Account-facing history is capped at 100 entries. Permanent claim identifiers are separate durable state. Tombstone and organization type forms are represented without enabling later behavior.

## P03 commands and administrative controls | 2026-11-13

- `/scrip` is the permanent primary command namespace. It is registered independently through Forge and NeoForge command events; an existing root command is never overwritten.
- Command targets are Minecraft server-resolved game profiles and are accepted only if their UUID already maps to a ledger-known account. Names are display and lookup aids only; no command accepts a user-supplied UUID.
- Administrative commands require server permission level 2. The command adapter derives the acting player identity from the authenticated command source; non-player administrative execution records the fixed `console` principal.
- P03 introduces durable `admin-adjust` and `account-freeze` journal operations rather than any command-owned balance or freeze state. Administrative audit history is bounded to 1,000 newest records. This does not authorize maintenance cleanup, compaction, organization banking, or integration rewards.
- Aliases are bounded, opt-in server startup configuration through `scrip.command.aliases`. Invalid labels, the protected `scrip` label, duplicates, and existing root-command collisions cannot replace another mod command; collisions produce a server warning.

## P04 organization banking | 2026-11-13

- Organization IDs and their account IDs are independently generated UUIDs. Organization balances are ledger-owned assets and are never derived from, inherited by, or swept to an owner's personal account.
- P04 has exactly three fixed roles: `OWNER`, `MANAGER`, and `MEMBER`. The ledger enforces all role checks itself; external callers and future clients may request an action but cannot provide or select their own authority.
- Generic `transfer` rejects organization endpoints. Only `depositToOrganization` and `spendOrganizationFunds` can reach an organization account, and each checks durable membership immediately before invoking the same atomic transfer engine.
- Creation, invitation, acceptance, removal, and role changes are forced journal operations with a bounded organization administrative audit. The sole owner cannot be removed or demoted, preventing an orphaned active organization account.
- Ownership succession and organization dissolution remain explicitly unresolved. P04 validates a transfer request but returns `POLICY_UNRESOLVED` without persisting a role or balance change. No irreversible fallback is authorized.

## P03-M administrative maintenance controls | 2026-11-14

- Maintenance configuration is durable ledger state, not a client setting or an ad hoc file. Defaults are enabled, 365 inactivity days, 24 interval hours, and a 100-account bounded preview batch; supported input ranges follow `MAINTENANCE_AND_CLEANUP.md`.
- P03-M preview and journal commands are read-only. They never close accounts, change balances, write snapshots, compact journal data, schedule work, or run cleanup automatically.
- The command layer requires server permission level 2 and routes to ledger-owned configuration, preview, and verification methods. Reserved `run` commands explicitly return `NOT_IMPLEMENTED` until P10-M.
- Current exclusion evidence is limited to states implemented by the server ledger. Protected, pending-settlement, and financial-hold reasons are retained as explicit report categories with zero count until their producers are separately authorized and implemented.

## P05 fees and payment confirmation | 2026-11-14

- Payment policy permits exactly one fee mode: disabled, flat, or percentage basis points. Fee combinations remain disabled until separately approved.
- A durable non-spendable fee reserve records collected fees so payment settlement conserves value without choosing the pending burn-versus-treasury policy.
- Confirmations are server-generated, memory-resident, single-use previews. They are not persisted or client-created; a crash safely requires a new preview while any committed payment is recovered from the journal.
- The confirmation threshold is 2,000 Coins based only on payment amount. Policy revision changes, account lifecycle changes, organization-role changes, balance changes, expiration, and nonce reuse invalidate final settlement.
