# $crip | Optional-Client Protocol Status

## P08 status: transport blocker resolved; protocol not yet implemented

P08 requires actual Forge and NeoForge connections for both unmodified and matching-mod clients. The packaged dedicated-server probes now start and pass the unmodified-client reconnect probe when launched with the local Java option `-Djdk.net.unixdomain.tmpdir=.tools/java-tmp`. This option keeps temporary Unix-domain sockets within a short workspace-relative path.

No P08 payload, channel, client class, loader registration, or protocol-version advertisement has been added yet. The existing server-only command interface remains the supported path for unmodified clients.

## Approved implementation boundary after the blocker is resolved

- Use distinct loader adapters with a shared, versioned, bounded protocol model. Forge's optional channel compatibility must accept vanilla clients; NeoForge registration must be optional and must not send to an unnegotiated client.
- Treat every request as a server-authenticated request from the connection's player, never as an account-owner assertion supplied by the payload.
- Require a server-issued nonce for preview confirmation and reject replayed, expired, altered, or cross-player requests.
- Bound each decoded field and total packet size below loader limits, rate-limit per authenticated player, and schedule ledger access safely on the logical server.
- Filter wallet snapshots, history, and account selection through current server authorization. Return typed errors instead of disconnecting for ordinary authorization or validation failures.
- Keep client handlers in physical-client-only classes; dedicated servers may load only shared protocol values and server handlers.

## Required evidence to unblock

1. Forge dedicated server starts and accepts an unmodified reconnect probe.
2. NeoForge dedicated server starts and accepts an unmodified reconnect probe.
3. Matching client/server connections negotiate the protocol on both loaders.
4. Incorrect protocol, absent client payload support, malformed/oversized packets, replay, unauthorized account selection, restart, and dedicated-server startup are all exercised against those running servers.
