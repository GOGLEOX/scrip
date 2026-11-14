# $crip | GOGLEOX Foundry

**Status:** Public P00 development scaffold. `$crip` is planned as a lightweight Minecraft economy; accounts, payments, and integrations are not yet implemented. See `docs/STATUS.md` for executed checks and the next gate.

The planned product aims to support payments between players, shared accounts, configurable Coins rewards, and optional shop and quest connections. These economy features remain future work.

**Brand:** GOGLEOX Foundry  
**Product name:** `$crip` (pronounced Scrip); technical mod ID: `scrip`; default currency remains `Coins`.  
**Tagline:** Forged in Fire, Crafted in Utility.  
**Target:** Minecraft Java 1.21.1 / Java 21 / Forge and NeoForge  
**Document baseline:** v1.4 (approved name `$crip`, durable journal and stale-account cleanup; supersedes `$hillings` v1.3)  
**Storage approval:** Snapshot + append-only durable journal (ADR-0001 ACCEPTED). SQLite is a future optional backend only.  
**Maintenance approval:** Configurable automated stale-account cleanup, administrative cleanup/compaction commands (ADR-0002 capability approved; detailed defaults are recommended).

## Project documentation

- `docs/ADR-0001-LEDGER-STORAGE.md`: accepted journal decision superseding SQLite.
- `docs/ADR-0002-ACCOUNT-CLEANUP.md`: scope, safety policy, and explicit exclusions.
- `docs/MAINTENANCE_AND_CLEANUP.md`: command/config design, safe eligibility, compaction protocol, test matrix.
- `docs/INTEGRATION_CONTRACTS.md`: secure reward and future GOGLEOX shop APIs.
- `docs/INTEGRATIONS.md`, `docs/COMPATIBILITY_MATRIX.md`: named research targets vs actual runtime evidence.
- `docs/ARCHITECTURE.md`, `docs/DECISIONS.md`, `docs/STATUS.md`: implementation status and design decisions.
- `docs/ADR-0003-PRODUCT-NAMING.md`: approved display name, safe identifiers, preserved currency defaults, and branding release gates.

## Important limitation
Named integration compatibility remains research-based. The GitHub repository's existing `LICENSE` is MIT.

## P00 developer workspace

Install a Java 21 JDK. Use the checked-in Gradle wrapper (8.14); no system Gradle is required.

```sh
./gradlew build verifyArtifacts
./gradlew :common:test
./gradlew :forge:runServer
./gradlew :neoforge:runServer
```

On Windows use `./gradlew.bat`. The first build requires network access to official Gradle, Minecraft, Forge, NeoForge and Maven repositories. Loader versions and the single product version authority are in `gradle.properties`; plugin versions are pinned in the loader build files.

`common` contains Java-only immutable account ID and balance values plus an empty API boundary. Each loader packages those classes into its own jar. Output names are `scrip-1.21.1-forge-v0.1.0-p00.jar` and `scrip-1.21.1-neoforge-v0.1.0-p00.jar` under the respective `build/libs` directory. Do not install both loader jars together or distribute the common development jar.

The server hooks report readiness without creating accounts, writing a ledger or registering commands. Default currency identity remains `Coins`. Client rendering and custom payloads are absent in P00. This is an early optional-client probe baseline, not evidence that the future GUI protocol works.

For a bounded local smoke test, after personally accepting the [Minecraft EULA](https://aka.ms/MinecraftEULA):

```sh
python scripts/server-smoke.py forge --accept-eula
python scripts/server-smoke.py neoforge --accept-eula
```

The smoke runner creates loopback-only offline test servers in ignored test directories, waits for both the mod startup hook and Minecraft readiness, sends `stop`, and checks clean process exit. Never use these fixture settings for a public server. Sanitized summaries go to `build/verification`. Dedicated-server startup alone does not prove a client connection.

For the early absence-of-client probe, install Node.js and run `npm ci --prefix scripts/probe`, then append `--probe` to each smoke command. This test-only vanilla protocol client must reach play login, stay connected briefly, and reconnect. It does not replace graphical vanilla/client-mod testing or P08 protocol coverage.

If Windows Java reports `Unable to establish loopback connection` with `UnixDomainSockets.connect`, use a short writable temporary directory for Java's `jdk.net.unixdomain.tmpdir` system property. This is an environment workaround, not a mod JVM requirement; do not commit machine paths.

Both loader descriptors identify the repository's MIT license.
# scrip
$crip brings a simple, lightweight economy to Minecraft. Send money to friends, manage shared accounts, earn Coins through gameplay, and connect with shops and quest mods. Whether you're playing solo or running a server, $crip gives you the tools to build an economy your way, without unnecessary complexity.
