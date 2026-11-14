# ADR-0001 | $crip ledger persistence

**Decision: ACCEPTED**  
**Approved by user:** 2026-11-12  
**Supersedes:** Phase 4 embedded SQLite backend selection.  
**Status:** Planning only; no code or tests.

## Decision
Adopt a per-world server-owned in-memory ledger persisted through a **durable append-only journal and versioned atomic snapshots** for v1. Minecraft SavedData alone is not accepted as the transaction commit boundary. Remove JDBC/SQLite from mandatory builds and dependency tests. Retain `LedgerStore` for a possible future optional SQLite or remote backend.

## Reasons
For one local currency, bounded 100-entry visible account history, small/medium servers and integrated singleplayer, a pure-Java snapshot/journal offers less packaging and native-library overhead. It does **not** eliminate financial safety work: test forced durability, replay idempotency, checkpoint consistency, backup, corruption, and interruption exhaustively. This choice is approved despite the implementation burden of a custom persistence engine.

## Non-negotiable durability contract
1. One immutable journal operation includes every money delta, fee, reward claim, settlement identity and required metadata.
2. Serialize writes; use framed records with format version, monotonically increasing sequence and checksum. Force journal storage before acknowledging success or making the result externally visible.
3. Replay strictly after verified snapshot checkpoint. Quarantine torn invalid tail; stop on missing/corrupt middle records. Never silently reset accounts.
4. Snapshots are written as complete temporary files, forced, checksummed and atomically moved if supported. Retain prior valid recovery anchor and handle unavailable `ATOMIC_MOVE` safely.
5. Retire journal segments only when fully covered by verified durable checkpoint. Never truncate the active journal based on age alone.
6. Retain first-join/one-time reward claims and all unresolved shop settlement IDs independently of 100 visible history records.
7. Backup and restore include versioned manifest and consistent snapshot/committed journal sequence. All changes fail closed when storage is uncertain.

## SQLite status
**Not selected for $crip v1.** It remains a possible future alternate behind `LedgerStore` when scale or administrative SQL reporting justifies new dependencies. Do not implement JDBC in P00–P11.

## Follow-on approved maintenance scope
User also approved configurable automated stale-account cleanup and operator command. `docs/MAINTENANCE_AND_CLEANUP.md` defines safe zero-balance-only automatic closure, durable tombstones, replay-preserving compaction and command/config defaults. Product capability approved; specific default values are engineering recommendations.

## Acceptance
P01 must prove append/force/replay/checkpoint under fault injection. P10 must prove backup/restore and safe journal rotation. P03-M and P10-M cover maintenance controls and automation. Both Forge and NeoForge 1.21.1 must pass equivalent tests.

References: Java 21 `FileChannel.force()` and `Files.move(..., ATOMIC_MOVE)`, Minecraft Forge/NeoForge world lifecycle APIs. Historical SQLite comparison is recorded in v1.1 only.
