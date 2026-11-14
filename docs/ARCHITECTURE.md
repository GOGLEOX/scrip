# $crip | Architecture Snapshot

## Implemented in P00

| Module | Responsibility | Boundary |
|---|---|---|
| `common` | Product identity, immutable UUID account ID, nonnegative whole-unit balance, empty `EconomyFacade` | Java 21 only; no Minecraft or loader API |
| `forge` | Forge 52.0.30 entry point and server-started readiness log | Depends only on common and Forge; no NeoForge/client references |
| `neoforge` | NeoForge 21.1.172 entry point and server-started readiness log | Depends only on common and NeoForge; no Forge/client references |

Root Gradle orchestrates both independent loader artifacts. Each includes common compiled classes and only its own descriptor. Product version is read from `gradle.properties`, including archive filenames. No publication task or external repository is configured. Java toolchains and compiler release are both 21. `verifyArtifacts` checks the packaged boundary, metadata and bytecode, while JUnit exercises common value invariants.

Bootstrap registers only a server-started event listener and logs a bounded readiness message. It creates no accounts, mutable economic state, commands, networking, files, ticks, threads or gameplay registrations. No shared mutable state exists.

Optional-client design: no custom channels or payloads yet; Forge's display test tolerates absent client installation, and NeoForge has no required Scrip payload registration. Future physical-client code must live behind loader-specific side-safe entry points. Future server sends must require negotiated client capability, with command fallback. The test-only protocol probe is outside the production classpath. See `P00_VERIFICATION.md` and `STATUS.md` for the limits of the evidence.

CI builds both jars and runs checks on Java 21. The local bounded smoke runner observes both Minecraft and Scrip readiness, optionally probes a client connection twice, requests shutdown, and requires exit code zero. It writes only sanitized evidence. No CI run is implied by checking in the workflow.

## Implemented in P01

`common` now owns the loader-neutral `LedgerStore` persistence boundary. `JournalFileLedgerStore` stores opaque immutable `JournalOperation` records, so future account logic can define its own payload schema without writing around the durability layer. Each record has a format version, monotonic sequence, UUID operation ID, bounded payload, byte length, and CRC-32 checksum.

All commits are serialized through one bounded, daemon writer. The writer appends a complete frame with Java NIO `FileChannel`, calls `force(true)`, and only then returns `CommitReceipt`. A write that begins and then fails places the instance in recovery-required state; it cannot acknowledge the operation or accept further writes. The next process may recover the durable record if it exists, but P02 must use operation IDs to reconcile that ambiguous caller outcome safely.

Recovery validates a checkpoint manifest and its versioned snapshot before replaying only journal entries after the snapshot sequence. A short final frame is truncated and forced as a torn tail. A bad checksum, invalid length, non-monotonic sequence, duplicate operation ID, invalid manifest, or mismatched snapshot fails closed. No state is initialized from corrupt storage.

Checkpoints write and force a new versioned snapshot file, validate it, then replace a forced and verified manifest. The preceding checkpoint file stays on disk as a recovery anchor. P01 does not delete or compact journal entries; P10 owns segment retirement after a verified recovery process.

## Implemented in P02

`AccountLedger` is the common server-side account service. It owns indexed personal accounts keyed by stable player UUID, a known-player registry, account lookup, bounded account history, and permanent claim identifiers. A rename updates only the verified name record; it does not change account identity.

Account opening, player activity, transfers, internal adjustments, claims, and reserved tombstones are typed immutable journal payloads replayed through `LedgerStore`. The world currency name is initialized once in the journal and must match on later opens. The account writer validates a transfer, commits its complete debit-and-credit intent as one operation, waits for the durable receipt, and then publishes both balance changes. Checked signed 64-bit arithmetic rejects negative balances and overflow. Exact operation retries return the original sequence; conflicting reuse is rejected. Each account retains its newest 100 records, independent of permanent claim retention.

Organization account type and tombstone lifecycle forms are reserved in the shared domain model only. Organization behavior, rewards, fees, commands, GUI, shop behavior, and cleanup execution are not part of P02.

## Approved future architecture (not implemented)

- Target Minecraft Java 1.21.1, Java 21; separate Forge and NeoForge jars from shared domain source.
- All money operations run through server-owned `EconomyService`, role/fee/reward/claim rules, and backend-neutral `LedgerStore`.
- **Approved v1 backend:** In-memory canonical state with versioned durable append-only journal, forced commits, and atomic verified snapshots. No SQLite/JDBC dependency in v1. `docs/ADR-0001-LEDGER-STORAGE.md` is ACCEPTED.
- Each logical transfer/claim is a single durable framed operation with checked sequence and checksum. Success only after forced durable commit and in-memory publication.
- Startup validates snapshot manifest, replays journal and fails closed on middle corruption. Snapshot compaction retires only fully checkpointed segments and retains recovery anchors.
- **Approved cleanup:** configurable automated stale-account closure + admin command. Default proposal: auto daily at 365 days inactivity, zero-balance personal accounts only. Durable tombstones preserve claim/idempotency and known-player offline payee status. Full details `docs/MAINTENANCE_AND_CLEANUP.md`.
- Client features (industrial GUI, optional HUD, keybind, sound) are isolated, enabled by safe optional protocol negotiation. Nonmodded clients require text command fallback.
- Server-owned `IntegrationRewardPort` supports verified authorized server command reward sources, static once-only grants, and replay-safe IDs.
- Serilum Starter Kit v9.0 uses a universal Forge/NeoForge 1.21.1 jar; Collective is required for Serilum, not for $crip.
- FTB Quests 1.21.1 is a NeoForge-only named test target. No Forge 1.21.1 native adapter is promised.
- Future GOGLEOX shop uses `ShopEconomyPort` and durable settlement IDs; shop itself is separate and not implemented here.
- ChunkLock optional property tax may use the economy API, without direct store modification.
- No third-party mod is required. Never declare an integration compatible until pinned artifact tests pass.
