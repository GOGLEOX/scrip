# $crip | Optional-Client Protocol Status

## P08 status: verified

P08 requires actual Forge and NeoForge connections for both unmodified and matching-mod clients. The packaged dedicated-server probes start and pass the unmodified-client reconnect probe when launched with the workspace-local Java temporary-socket override. This keeps temporary Unix-domain sockets within a short workspace-relative path.

P08 now has a shared protocol version, bounded request model, per-player negotiated capability/replay/rate state, and a server dispatcher. The dispatcher takes its actor only from the loader connection, permission-filters account selection and history, and routes payment previews, confirmations, and cancellation through the existing payment service. It cannot mutate balances directly.

Forge and NeoForge each register optional hello, request, and response transport. Both use protocol version 1 and load no physical-client class on a dedicated server. The shared wire codec bounds each packet, rejects malformed or trailing data, and carries no client-asserted identity. Ordinary client preferences remain client-local; they are not ledger data.

The existing server-only command interface remains the supported path for unmodified clients. Matching Forge and NeoForge client/server runs have exercised protocol negotiation, a wallet-open request, and a `WALLET_SNAPSHOT` response against packaged servers. Forge development runs load the assembled JAR and pin the Minecraft 1.21.1 `jopt.simple` module version, preventing duplicate development modules and launcher resolution failures.

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
3. Matching client/server connections negotiate the protocol and complete a wallet-open request/response on both loaders. **Complete.**
4. Shared protocol and codec tests cover incorrect version, absent negotiation, malformed and oversized data, replay, rate limiting, and unauthorized account selection. Restart and dedicated-server startup are covered by the packaged-server smoke runs. **Complete.**
