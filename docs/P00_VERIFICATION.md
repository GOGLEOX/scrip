# P00 verification procedure

## Automated gates

- `./gradlew build verifyArtifacts`: common tests, both loader builds, descriptor and artifact parity, Java 21 bytecode, common class inclusion, no foreign-loader classes or client package references.
- `./gradlew :common:test --configure-on-demand`: isolated common tests when loader setup is unavailable.
- `npm ci --prefix scripts/probe`: locked test-only protocol tooling, excluded from all jars.
- Install the pinned official server for each loader under ignored `.tools/production-<loader>`, then run `python scripts/server-smoke.py <loader> --accept-eula --production --probe`: bounded dedicated-server startup with the packaged jar, vanilla protocol login/reconnect, graceful stop. Explicit EULA acceptance is required. Forge development runs also passed readiness and protocol checks using Forge 52.0.30.

Do not infer a pass from a successful dependency download, source inspection, server-list ping, or login acknowledgement before the play phase. The protocol probe must receive play login twice. Smoke output is allowlisted to avoid including local paths or machine details. Raw local loader logs and test worlds are ignored.

## Still required for complete installation compatibility

Test a real vanilla 1.21.1 client, a matching loader with no Scrip, and a matching Scrip client against each production dedicated-server artifact. Verify singleplayer and disconnection/reconnection. P08 adds absent/incompatible protocol negotiation coverage when payloads exist. P00's empty-network probe establishes only baseline feasibility.

## API evidence used

- [Forge 1.21.1 MDK 52.0.28](https://maven.minecraftforge.net/net/minecraftforge/forge/1.21.1-52.0.28/forge-1.21.1-52.0.28-mdk.zip): ForgeGradle 6 setup, official runtime mappings, Java 21. Forge 52.0.30 fixes the development-server shutdown hang identified in 52.0.28.
- [Forge sidedness](https://docs.minecraftforge.net/en/1.21.x/concepts/sides/): dedicated-server class isolation and display-test policy.
- [NeoForge 1.21.1 getting started](https://docs.neoforged.net/docs/1.21.1/gettingstarted/): loader setup.
- [ModDevGradle](https://docs.neoforged.net/toolchain/docs/plugins/mdg/): runs and source-set bindings.
- [NeoForge 1.21.1 payloads](https://docs.neoforged.net/docs/1.21.1/networking/payload/): future optional payload registration. No payload API is implemented in P00.

Source-derived design evidence is not runtime test evidence. See `STATUS.md` for actual executed results.
