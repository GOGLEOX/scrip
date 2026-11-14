# $crip | Implementation Status

**Product:** `$crip` / technical ID `scrip` / in-game default currency `Coins` (unchanged).
**Current:** P00 PASS — dual-loader scaffold, artifact checks, tests and packaged dedicated-server checks completed. No economy implemented.  
**Technical baseline:** Minecraft Java 1.21.1, Java 21, Forge and NeoForge.
**Next permitted packet:** P01 durability and recovery feasibility. P02 is gated on P01 passing.  
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

Build environment required a local `jdk.net.unixdomain.tmpdir` override after Java's default temporary socket location failed with `Unable to establish loopback connection`. This setting is local only; no machine-specific paths are in project configuration. The wrapper pins Gradle 8.14 and its official SHA-256 checksum. Initial Minecraft dependency downloads are large.

No persistence format/schema, migration, account operations or integration adapter exists. Follow `P00_VERIFICATION.md` to reproduce the checks.

## Stop conditions
- P00: loader jars or optional-client viability unverifiable.
- P01: forced append+checksum+replay/checkpoint cannot survive fault tests.
- P03-M: non-admin can invoke maintenance or preview mutates state.
- P10-M: cleanup can drop Coins/claims/settlements or journal segment removed before verified checkpoint.
- P07-B: Serilum lacks secure actual kit-claim command context.
- P07-C: FTB Quests lacks stable claim identity or execution authorization for chosen payout semantics.
- P07-D: payment and inventory delivery cannot reconcile safely.

## Development handoff rules
Record changed files, exact test commands/results, blockers, format/schema changes and next authorized packet. Do not invent test results. P00 is complete. P01 is next; P02 cannot begin until P01's durability and recovery gates pass.
