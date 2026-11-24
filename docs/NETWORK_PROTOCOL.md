# $crip | Optional-Client Protocol Status

## P08 status: server protocol foundation implemented; connection matrix pending

P08 requires actual Forge and NeoForge connections for both unmodified and matching-mod clients. The packaged dedicated-server probes now start and pass the unmodified-client reconnect probe when launched with the local Java option `-Djdk.net.unixdomain.tmpdir=.tools/java-tmp`. This option keeps temporary Unix-domain sockets within a short workspace-relative path.

P08 now has a shared protocol version, bounded request model, per-player negotiated capability/replay/rate state, and a server dispatcher. The dispatcher takes its actor only from the loader connection, permission-filters account selection and history, and routes payment previews, confirmations, and cancellation through the existing payment service. It cannot mutate balances directly.

Forge registers an optional server-bound handshake channel and NeoForge registers an optional server-bound custom payload. Both use protocol version 1 and load no physical-client class on a dedicated server. Ordinary client preferences remain client-local; they are not ledger data.

The existing server-only command interface remains the supported path for unmodified clients. Client-originated request/response payload codecs and actual matching-mod connection evidence are still required before optional-client features may be advertised as complete.

## Approved implementation boundary after the blocker is resolved

- Use distinct loader adapters with a shared, versioned, bounded protocol model. Forge's optional channel compatibility accepts vanilla clients; NeoForge registration is optional and sends nothing to an unnegotiated client.
- Treat every request as a server-authenticated request from the connection's player, never as an account-owner assertion supplied by the payload.
- Require a server-issued nonce for preview confirmation and reject replayed, expired, altered, or cross-player requests.
- Bound each decoded field and total packet size below loader limits, rate-limit per authenticated player, and schedule ledger access safely on the logical server.
- Filter wallet snapshots, history, and account selection through current server authorization. Return typed errors instead of disconnecting for ordinary authorization or validation failures.
- Keep client handlers in physical-client-only classes; dedicated servers may load only shared protocol values and server handlers.

## Required evidence to unblock

1. Forge dedicated server starts and accepts an unmodified reconnect probe.
2. NeoForge dedicated server starts and accepts an unmodified reconnect probe.
3. Matching client/server connections negotiate the protocol on both loaders.
4. Client payload codecs exercise incorrect protocol, absent payload support, malformed/oversized packets, replay, unauthorized account selection, restart, and dedicated-server startup against those running servers.
