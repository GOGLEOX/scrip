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

## Implemented in P03

`/scrip` is registered during each loader's native command-registration lifecycle and executes only on the logical server. The shared Minecraft command tree is compiled into each loader artifact, while `common` remains free of Minecraft and loader APIs. Forge and NeoForge adapters open one world-local `AccountLedger` at server start, register verified joining-player UUID/name pairs, and close it during server stop.

Player operations are `balance`, `balance <player>` (operator inspection), `pay <player> <amount>`, and `history`. Targets are resolved from Minecraft's server-authoritative game-profile argument and accepted only when their UUID has a known personal account; this supports previously joined offline players and a changed name without accepting arbitrary names or UUID strings.

Administrative operations live beneath `/scrip admin`: `balance`, `grant`, `deduct`, `freeze`, `unfreeze`, and `audit`. They require server permission level 2. The audit command reports both account transactions and administrative records. Grants, deductions, freezes, and unfreezes are durable ledger operations carrying the authenticated player actor or the console principal, bounded reason, account, amount where applicable, and durable sequence. The ledger retains the newest 1,000 administrative audit records in addition to the per-account transaction history.

Frozen accounts cannot originate transfers, though their balances can still receive a server-authorized administrative correction. No command writes balance state directly. Optional aliases come from the bounded `scrip.command.aliases` JVM property; invalid labels are ignored, collisions are logged and skipped, and neither aliases nor the `/scrip` root overwrite another registered command. Commands use only server text feedback and require no client-side mod or payload.

## Implemented in P04

The common `AccountLedger` now owns durable organization state alongside personal accounts. Each organization receives a random stable organization ID, a distinct organization account ID, one permanent active owner, a bounded name, memberships, and pending invitations. An organization account is never an alias for its owner's personal account.

Membership roles are fixed: members can inspect their organization and deposit their own funds; managers can also invite or remove ordinary members and spend organization funds; owners alone can promote or demote managers. Every authorization check happens in the server-side ledger before a transition is journaled. Direct generic transfers involving an organization account are rejected, so callers cannot bypass role checks by supplying an account ID.

Personal deposits and authorized organization spending call the same atomic transfer path used by personal payments. Organization membership transitions and their administrative audit records are durable journal operations replayed at restart. Account-access lists are derived from current membership for future GUI selection, but account selection grants no authority and every action is re-authorized.

Ownership transfer deliberately performs no mutation and reports a policy-unresolved result. Organization dissolution, automatic deletion, custom permissions, loans, interest, taxes, and shops remain outside P04. This preserves the owner-to-account binding and prevents orphaned organization balances until an explicit succession or dissolution policy is approved.

## Implemented in P03-M

Maintenance controls are server-only children of `/scrip admin`. `cleanup preview [days]`, `cleanup status`, and the three `cleanup set` controls operate on one durable world-local maintenance configuration. `journal compact preview` and `journal verify` call the existing non-mutating ledger verification boundary. Both execution commands are deliberately reserved and return `NOT_IMPLEMENTED`.

Preview work is bounded by the persisted maximum batch (default 100), reports scanned and unscanned counts, and classifies every scanned personal account as eligible or excluded. It uses persisted account timestamps, server-observed online identities, balance, lifecycle, and organization membership; it never changes accounts, balances, history, claims, journal records, or snapshots. Settlement, hold, and protection exclusions remain visible zero-count categories until their server-side state producers exist.

## Implemented in P05

The ledger persists one payment policy with a monotonically advancing revision. Fees are disabled by default and may be either a flat whole-unit amount or a percentage in basis points; combined fees are not represented. Percentage calculation rounds upward using checked integer arithmetic. Confirmed payments commit payment amount, calculated fee, policy revision, and both account changes as one forced journal operation.

Fees enter a durable non-spendable fee reserve, preserving ledger value without selecting the still-unapproved burn or treasury destination. `PaymentService` creates server-only, bounded, single-use previews that bind actor, source, destination, amount, fee, revision, and expiry. Confirmation revalidates all bindings, authorization, policy revision, lifecycle, and balance immediately before journal settlement. Previews disappear on restart; a committed payment replays safely from its durable operation.

`/scrip pay` displays amount, fee, and total debit. Payments at or above 2,000 Coins require `/scrip confirm <nonce>`; lower payments execute after the same server-side preview and revalidation. No client payload or GUI is required.

## Implemented in P06

P06 adds a ledger-owned `RewardConfiguration`, disabled-by-default whole-unit payouts, and an atomic `reward-issuance` journal operation. The operation records source, target, amount, optional permanent claim identifier, and account mutation together, so replay cannot retain a one-time claim without its issued balance. Starter grants use the permanent UUID-based `starter:<player UUID>` claim and are therefore rename-, reconnect-, restart-, reactivation-, and journal-replay-safe.

`RewardService` is server-side only. Its advancement, kill, mining, playtime, and external entry points accept server-validated facts; it never consumes a client-selected source, amount, or target. The playtime accumulator is bounded, accepts observed active seconds only, and emits at most one scheduled interval payout per call instead of writing on every tick. Mining requires a verified natural, non-recently-placed block; kill rewards require a verified eligible server kill. Optional integrations are represented only by `ExternalRewardSource`; no loader-specific FTB Quests or Serilum calls exist in this packet.

## Implemented in P07-A

The public API is `EconomyService` version 1 in the loader-neutral common module. It returns immutable values through `CompletionStage`, accepts UUID identities and typed account IDs, and delegates every mutation to the existing payment or ledger transaction boundary. API consumers receive account/organization views and bounded history, never a ledger or storage reference. Authorization is derived from durable personal ownership and organization roles; administrative adjustment requires a host-supplied server authorization policy.

Trusted integration rewards have a separate durable idempotency tuple rather than a best-effort claim marker. Source, claim ID, recipient, amount, operation ID, and durable sequence replay together. Post-commit listeners run only after the operation is durable. The administrative command bridge has no client path and accepts only registered command-enabled sources with bounded issue limits. No third-party integration API is loaded or called.

## Implemented in P07-B

P07-B keeps Starter Kit optional and dependency-free. Loader adapters check for both `starterkit` and `collective`; only then may the command bridge accept the `serilum-starter-kit` source. The bridge requires Starter Kit's server command entity to be the same verified recipient resolved from `@s`, requires the fixed `starter` marker and configured amount, and derives the permanent `starter_grant:<UUID>` key internally. This shares the existing P06 atomic issuance and claim boundary with first-join grants.

No Serilum class is linked or loaded, no native event is subscribed, and an absent or incomplete Serilum installation cannot change $crip behavior. The supported integration surface is the documented kit command-file execution context only.

## Implemented in P07-C

P07-C uses no FTB classes. NeoForge optionally detects `ftbquests`, while Forge leaves the FTB source disabled. FTB Command Reward creates its command source from the claiming player; `$crip` requires that verified source entity to equal `@s`, validates a static hex `quest-id:reward-id` pair, and derives the recipient UUID portion of the durable claim itself. The resulting reward is issued only through the P07-A trusted API boundary.

The bridge intentionally supports only one-time individual command rewards. Repeatable rewards lack a verified occurrence identifier, and team-wide distribution lacks an approved recipient policy. Both remain excluded rather than inferred from FTB's command-format team variables.

## Implemented in P07-D

`ShopEconomyPort` version 1 is a separate-product money provider boundary. It records immutable quotes and durable settlements but does not contain inventory, catalog, stock, GUI, or delivery logic. Reservations retain payment details and stable payment/refund operation identifiers; they do not reserve an account balance. Settlement reuses the atomic payment engine and revalidates actor, organization role, lifecycle, balance, policy revision, and fee immediately before debit.

The ledger journals reserve/state/refund operations. A payment committed before its settlement-state marker is recovered by replaying the stable payment operation ID; delivery failure after payment starts a compensating refund. If the seller can no longer fund the compensation, the durable result is `REQUIRES_RECONCILIATION`, never a fabricated delivery or silent loss. Shop clients must retain their own delivery evidence and recovery workflow.

## Implemented in P07-E

P07-E adds no shop code, dependencies, or wallet synchronization. Evidence-based review found no documented server-side external-provider boundary in the pinned Money & Sign Shops, Q Shop, or SmartShopMode targets. They remain **NOT SUPPORTED AS DIRECT ECONOMY ADAPTER**: each would otherwise require `$crip` to accept or synchronize an independent wallet, violating the single-ledger authority and recovery model.

Future adapters must isolate target classes by loader and optional discovery, then delegate all monetary authority through `ShopEconomyPort` (or an equally strict server-side provider contract). Shop UI, inventory mutation, stock, pricing, and delivery remain outside `$crip`.

## Implemented in P07-F

`TaxPaymentPort` version 1 is a loader-neutral, optional consumer boundary for a separate property-tax authority. A request carries only immutable financial facts: verified actor, typed source and recipient accounts, amount, stable invoice ID, stable period ID, and authenticated confirmation state. It does not represent claims, parcels, rates, billing schedules, grace, arrears, or penalties.

The ledger commits a tax invoice identity, calculated fee, balance debit, recipient credit, and operation receipt as one forced journal entry. The durable invoice/period key rejects changed-payload reuse and makes exact retries safe through restart. Personal ownership, organization manager/owner authority, frozen lifecycle, fee-policy balance, and account existence are revalidated immediately before the commit. An absent or disabled tax authority has no effect on `$crip`; no ChunkLock class is referenced.

## P08 optional-client networking status

P08 defines one loader-neutral version-1 wallet protocol with an 8 KiB inbound bound, negotiated capability record, bounded replay identifiers, and per-player rate limit. A strict shared wire codec rejects oversized, malformed, and trailing data before dispatch. The server derives the actor from the connection, rechecks account visibility for selection/history, and reaches the established payment preview/confirmation/cancellation service instead of mutating balance state. Forge and NeoForge install optional request/response transports; neither adapter references a physical-client type on a dedicated server. Unmodified clients remain supported through commands and receive no optional payload.

Physical-client-only login listeners negotiate then issue a bounded wallet-open request. Real Forge and NeoForge client-to-packaged-server connections have exercised the negotiated request/response path and received a wallet snapshot. `docs/NETWORK_PROTOCOL.md` records the verification evidence. No GUI or client-authoritative rule is introduced.

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
