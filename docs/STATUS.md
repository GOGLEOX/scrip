# $crip | Implementation Status

**Product:** `$crip` / technical ID `scrip` / in-game default currency `Coins` (unchanged).
**P03 authoritative update (2026-11-13):** P03 PASS. Server-side player and administrative commands, durable administrative audit, and frozen-account enforcement are verified.
**P04 authoritative update (2026-11-13):** P04 organization banking implementation is complete and verified.
**P03-M authoritative update (2026-11-14):** P03-M administrative maintenance configuration and read-only inspection controls are complete and verified.
**P05 authoritative update (2026-11-14):** P05 fees and server-authoritative payment confirmations are complete and verified.
**P06 authoritative update (2026-11-14):** P06 gameplay reward and starter-grant foundation is complete and verified.
**P07-A authoritative update (2026-11-14):** P07-A public economy API and integration bridge is complete and verified.
**P07-B authoritative update (2026-11-14):** P07-B optional Serilum Starter Kit command bridge is complete. Exact Starter Kit+Collective binary-pair testing remains a release-matrix blocker.
**Current:** P07-B PASS with a documented binary-matrix blocker. Stop after P07-B.
**Technical baseline:** Minecraft Java 1.21.1, Java 21, Forge and NeoForge.
**Next permitted packet:** A separately authorized packet only.
**Approved backend:** Durable in-memory snapshot + append-only forced journal (ADR-0001 ACCEPTED); SQLite not in v1.  
**Approved maintenance scope:** Configurable automatic stale-account cleanup and admin preview/run/status/config, journal compact/verify (ADR-0002).  
**Named integrations:** Serilum Starter Kit Forge+NeoForge, FTB Quests NeoForge 1.21.1, future first-party GOGLEOX shop hook both, ChunkLock objective and generic bridge.

## Suggested settings, not yet code
365-day inactivity, every 24 hours, auto enabled, bounded batch; zero-balance personal accounts only. Positive-balance and organization-related accounts are not automatically closed. Journal segments compacted only after verified durable snapshot. See `docs/MAINTENANCE_AND_CLEANUP.md`.

## Verified implementation evidence

Executed locally on Java 21 (2026-11-13):

- `./gradlew.bat :common:test --configure-on-demand --console=plain`: **PASS**, nine tests, zero failures. Actual output included `BUILD SUCCESSFUL in 22s` and `3 actionable tasks: 3 executed`.
- `npm ci --prefix scripts/probe --no-audit --no-fund`: **PASS**, pinned test-only vanilla protocol dependency installation.
- `node --check scripts/probe/vanilla.cjs` and `python -m py_compile scripts/server-smoke.py`: **PASS**, syntax checks only.
- `./gradlew.bat build verifyArtifacts --console=plain`: **PASS** after pinning Forge 52.0.30 and NeoForge 21.1.172. Both artifacts reported matching `$crip`/`scrip` metadata and `0.1.0-p00`, Java 21 bytecode, shared classes, and loader isolation. Output: `BUILD SUCCESSFUL in 50s`.
- `python scripts/server-smoke.py forge --accept-eula --probe --production --timeout 180`: **PASS** on the packaged Forge jar; observed server hook, Minecraft `Done` marker, two vanilla protocol logins without a client mod, clean world save and process exit.
- `python scripts/server-smoke.py neoforge --accept-eula --probe --production --timeout 180`: **PASS** with the same criteria on the packaged NeoForge jar.
- `python scripts/server-smoke.py neoforge --accept-eula --probe --timeout 180`: **PASS** development-run check. Forge 52.0.30 development run also passed earlier. Its initial 52.0.28 smoke reached readiness but hung at shutdown; the Forge release changelog identifies this as the NightConfig FileWatcher issue fixed in 52.0.30. P00 pins the fixed release.
- CI workflow exists but has not run on a remote service. P00 is committed as a development checkpoint on `foundry/p00-foundation`; no release artifact is published.
- `./gradlew.bat :common:test --console=plain`: **PASS**, 21 tests with zero failures/errors: 12 journal/snapshot tests and 9 P00 domain tests. Journal evidence includes forced-restart replay, concurrent serialization, duplicate operation rejection, append/force/disk-full/read-only injected failures, torn-tail repair, corrupt-record and snapshot-checksum fail-closed behavior, checkpoint replay, and interrupted snapshot creation/replacement.
- `./gradlew.bat build verifyArtifacts --console=plain`: **PASS**. Both loader jars compiled and passed metadata, Java 21 bytecode, shared-class, and loader-isolation verification. Output: `BUILD SUCCESSFUL in 6s`.
- Packaged Forge and NeoForge dedicated-server checks both passed startup and the vanilla protocol reconnect probe after P01 packaging.

Build environment required a local `jdk.net.unixdomain.tmpdir` override after Java's default temporary socket location failed with `Unable to establish loopback connection`. This setting is local only; no machine-specific paths are in project configuration. The wrapper pins Gradle 8.14 and its official SHA-256 checksum. Initial Minecraft dependency downloads are large.

P01 persistence format version 1 exists: framed journal operations plus verified snapshots and manifests. It deliberately carries opaque payloads and does not define accounts, balances, transfers, claims, or configuration. Follow `P00_VERIFICATION.md` for loader setup and the common test suite for storage fault cases.

## Stop conditions
- P00: loader jars or optional-client viability unverifiable.
- P01: forced append+checksum+replay/checkpoint cannot survive fault tests.
- P03-M: non-admin can invoke maintenance or preview mutates state.
- P10-M: cleanup can drop Coins/claims/settlements or journal segment removed before verified checkpoint.
- P07-B: Serilum lacks secure actual kit-claim command context.
- P07-C: FTB Quests lacks stable claim identity or execution authorization for chosen payout semantics.
- P07-D: payment and inventory delivery cannot reconcile safely.

## Development handoff rules
Record changed files, exact test commands/results, blockers, format/schema changes and next authorized packet. Do not invent test results. P01 is complete. P02 may define account operations and use `LedgerStore`; it must preserve the P01 force-before-acknowledgement boundary.

## P02 implementation record

**P02 verification: PASS. Next permitted packet: P03 commands and administrative controls.**

`./gradlew.bat :common:test --no-daemon --console=plain` passed with 26 tests and zero failures. `./gradlew.bat build verifyArtifacts --no-daemon --console=plain` passed and verified both P02 jars. Packaged Forge and NeoForge dedicated-server probes both passed startup, clean shutdown, and two vanilla-client reconnects.

Historical pre-verification note (superseded by the P02 PASS record above): `AccountLedger` provides UUID-keyed personal accounts, durable verified-player registry entries, indexed account lookup, one world-local currency definition, lifecycle representation, bounded per-account history, permanent claim markers, and replay reconstruction. Transfers and internal adjustments use checked signed 64-bit arithmetic and a single serialized writer. A transfer becomes visible only after the journal receipt and carries its complete debit-and-credit intent in one operation. Exact operation retries are idempotent; conflicting reuse is rejected.

Historical preliminary test note (superseded by the P02 PASS and P03 verification records): the common production and test sources compiled with Java 21, and the JUnit launcher executed 26 tests with 26 successes and zero failures before the later complete Gradle and dedicated-server verification.

## P03 implementation record

**P03 verification: PASS. Phase 4 is not authorized.**

The command tree supplies `/scrip balance`, `/scrip balance <player>`, `/scrip pay <player> <amount>`, and `/scrip history`, plus permission-level-2 `/scrip admin balance|grant|deduct|freeze|unfreeze|audit`. Player profiles are validated by the logical server and then by known UUID account identity, so unknown recipients are rejected while previously joined offline recipients and renamed players remain resolvable. Every balance mutation reaches `AccountLedger`; administrative mutations use durable attributable records. Frozen source accounts cannot pay.

Aliases use the bounded `scrip.command.aliases` JVM property. Conflicting commands are left untouched and reported as warnings. No GUI, HUD, organization banking, confirmation flow, rewards, cleanup execution, or journal compaction was introduced.

Verified locally on Java 21 (2026-11-13): common tests passed with 29 tests and zero failures after applying the documented local loopback transport override. The full build and artifact verification compiled both loader command adapters and passed. Production dedicated-server smoke checks for Forge and NeoForge each reached readiness, shut down cleanly, and completed two vanilla-client reconnects with no client mod installed. A development Forge run remains blocked by a local ModLauncher `jopt.simple` module-resolution fault; the packaged Forge dedicated-server check passed.

## P04 implementation record

**P04 verification: PASS. Phase 5 is not authorized.**

Organization creation, owner assignment, invitations, acceptance, deposits, manager spending, membership changes, role boundaries, direct-API authorization rejection, restart replay, failed-transfer consistency, and concurrent organization payments are covered by common tests. Organizations are immutable with respect to ownership transfer and dissolution in this packet: the owner cannot be removed or demoted, and an ownership-transfer request returns a documented policy blocker without state change.

P04 adds no GUI, custom permissions, loans, interest, taxation, shop inventory, or automatic organization deletion. Common tests passed with 32 tests and zero failures. Forge and NeoForge P04 artifacts both passed build verification and packaged dedicated-server smoke checks, including the vanilla-client reconnect probe.

## P03-M implementation record

**P03-M verification: PASS. Stop after P03-M.**

Implemented administrator-only maintenance controls for durable cleanup configuration, read-only eligibility previews, and journal inspection. Defaults are enabled, 365 days inactivity, 24 hours interval, and 100 accounts per preview. The preview is bounded and reports eligibility plus explicit exclusions; it does not write or alter ledger data. `cleanup run` and `journal compact run` return `NOT_IMPLEMENTED`.

Maintenance regression coverage verifies configuration validation and restart persistence, cutoff boundaries, positive-balance/frozen/organization exclusions, bounded large-account previews, and no-change preview/journal inspection. Common tests passed with 35 tests and zero failures; packaged Forge and NeoForge dedicated-server smoke checks both passed with the vanilla-client reconnect probe. Actual cleanup, deletion, scheduler work, compaction, balance forfeiture, and Phase 6 work remain excluded.

## P05 implementation record

**P05 verification: PASS.**

P05 adds disabled-by-default flat or rounded-percentage fees, durable fee reserve accounting, server-generated payment previews, single-use expiring confirmation nonces, and `/scrip confirm` fallback. Preview and confirmation bind actor, source, recipient, amount, fee, policy revision, and expiry, then revalidate authorization and balance at settlement. No loan, interest, transfer-limit, combined-fee, GUI, or shop handling was added.

## P06 implementation record

**P06 verification: PASS.**

P06 adds durable, disabled-by-default configuration for advancement, verified mob-kill, conservative mining, observed active-playtime, and optional external quest/job reward sources. Issuance is ledger-owned and atomic with its source record and permanent one-time claim. Starter grants are UUID-claimed, default to zero, and support mutually exclusive first-join and future starter-kit trigger modes. First-join processing runs after the server has registered a verified player account; kit mode withholds payment until a compatible trusted trigger is supplied.

Verification on Java 21: `:common:test` passed 40 tests with zero failures; `build verifyArtifacts` passed and verified the Forge and NeoForge P06 jars; packaged Forge and NeoForge dedicated-server smoke checks each reached readiness and passed the vanilla protocol reconnect probe without a client mod. Common tests cover duplicate advancement prevention, invalid kill and placement-derived mining exclusion, interval playtime issuance, disabled categories, starter triggers, duplicate grant prevention, restart replay, and rename-safe UUID claims. No FTB Quests or Serilum adapter, GUI, jobs system, shop functionality, or P07 work is included.

## P07-A implementation record

**P07-A verification: PASS. Stop after P07-A.**

The public version-1 `EconomyService` provides asynchronous immutable account, organization, history, authorization, transfer, adjustment, and trusted reward operations without exposing the ledger. Trusted reward claims are durable and parameter-bound; identical retries do not issue again, conflicting reuse fails, and post-commit listeners fire only after journal durability. `/scrip integration reward` is permission-level-2-only, validates its source, claim, known recipient, positive amount, and source maximum.

The integration-consumer regression suite uses only the public API and covers transfer, frozen-account denial, organization authorization, unauthorized adjustments, durable duplicate/conflicting rewards, and restart replay. No shop, FTB, Serilum, ChunkLock, GUI, or further P07 work is included.

Verification on Java 21: `:common:test` passed 44 tests with zero failures; `build verifyArtifacts` verified both P07-A loader artifacts; packaged Forge and NeoForge dedicated-server checks each reached readiness and passed the vanilla protocol reconnect probe without a client mod.

## P07-B implementation record

**P07-B verification: PASS with release-matrix blocker. Stop after P07-B.**

P07-B implements an optional, command-on-kit bridge without compiling against Serilum. Both loaders detect the `starterkit` and `collective` pair; a missing pair rejects kit grants and leaves normal server operation unchanged. The only accepted kit path is the documented server command context with `@s` resolving to the recipient entity. It validates that context, the exact configured amount, `STARTER_KIT` mode, and a fixed marker before using the permanent `starter_grant:<UUID>` claim.

Verification on Java 21: `:common:test` passed 45 tests with zero failures; `build verifyArtifacts` verified both P07-B loader artifacts; packaged Forge and NeoForge dedicated-server checks passed without Starter Kit or Collective and completed the vanilla reconnect probe. Common regression coverage verifies absent dependency rejection, recipient-context mismatch rejection, amount mismatch rejection, issue, duplicate reissue, restart replay, and canonical claim persistence. Source inspection verifies the Starter Kit command context; running the exact pinned Starter Kit+Collective artifacts on both loaders remains required before advertising binary compatibility.
