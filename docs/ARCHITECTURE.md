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
