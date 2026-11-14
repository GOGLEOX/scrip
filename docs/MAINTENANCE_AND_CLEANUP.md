# $crip | Maintenance, Stale-Account Cleanup and Journal Compaction

**Design status:** Approved scope from user on 2026-11-12; configuration values and safety rules are implementation recommendations pending testing. **No code has been implemented.**
**Backend:** Versioned per-world in-memory canonical ledger, append-only forced durable journal and atomic snapshots. SQLite is not a v1 dependency. This document is normative for the cleanup feature design.

## Scope and two separate operations

1. **Stale-account cleanup** closes and removes eligible inactive personal account objects from active ledger indexes/snapshots. It must not destroy value or interfere with reactivation, offline recipients, organizational ownership, unclaimed obligations, or reward claim protection.
2. **Journal compaction** checkpoints committed state to a verified durable snapshot and retires fully checkpointed journal segments. It must never delete arbitrary records from the live journal, discard outstanding settlements, omit a cleanup tombstone, or truncate records ahead of the latest verified snapshot.

The 100 player-facing history entries per account are a display retention setting, *not* the journal, claim registry or anti-duplication retention. Backups use an independently configured retention schedule. Account cleanup does not delete old backup archives.

## Suggested configuration (NOT user-approved specific values)

```toml
[maintenance]
  enabled = true                  # automated stale-account checks enabled by default
  inactivityDays = 365            # adjustable; minimum 30, maximum 3650 recommended
  intervalHours = 24              # adjustable; minimum 1, maximum 168
  maxAccountsPerRun = 100         # bounded work; adjustable
  maxScanPerCycle = 1000          # scan incrementally, never full world scan on tick
  requireVerifiedBackup = true   # fail closed if backup or verification fails
  zeroBalanceOnly = true          # mandatory for automatic deletion
  compactAfterCleanup = true     # schedule compaction, not immediate extra writes per account
  previewLog = true              # aggregate counts, not noisy per-account messages
[journal]
  compactAfterMegabytes = 32     # example threshold, not a product guarantee
  compactAtShutdown = false      # no blocking/shutdown-only compaction guarantee
  preserveVerifiedSnapshots = 2 # maintain old verified recovery anchors
```

`enabled=true` means the server runs daily *eligibility checks*, not that any nonzero balances are discarded. Admins may disable scheduling. Changes to config should be validated, written atomically, surfaced in `/scrip admin cleanup status`, and may be reloaded without an unsafe mid-commit policy transition. Server-local wall-clock changes must not make accounts falsely eligible; use persisted timestamps and suspicious clock-jump safeguards.

## Inactivity and eligibility [recommended default rules]

Define inactivity as elapsed calendar time since **max(last authenticated login, last committed economic activity, account creation)**. Record last-login on successful player authentication, and last-economic-activity on durable transaction commit. Last-login changes must be persisted reliably, or an uncertain/recent session must make an account ineligible; a crash must never roll back the activity clock so far that it causes a false cleanup. Source-only display requests do not refresh the economic clock. Do not use display name or recent client packets to identify an account. Only a player UUID-backed `PLAYER` account can be automatically closed.

Eligible only if *every* condition is met at the commit point:
- Player is offline; account is older than configured inactivity duration.
- Current balance == 0 and no pending/deferred positive or negative balance obligations or reservations.
- No unresolved shop settlement/refund/charge, pending payment confirmation requiring protection, admin dispute, reconciliation hold, or recovery error.
- Account is not frozen, protected, or marked as requiring manual administrator review.
- Player is neither owner nor member of an active organization, and has no organization-linked obligation. Organization/system accounts are **never** automatically deleted.
- Validated world backup available when `requireVerifiedBackup=true`, storage healthy, and journal compaction/backup state allows safe commit.

Re-check all conditions under the account transaction lock immediately before durable cleanup. Ineligibility leads to a **skip with reason**, not an error that deletes money. Automatic deletion of nonzero balances is outside approved scope. A future nonzero-balance forfeiture/escheat policy requires a separate explicit opt-in approval and transparent balance destination; do not implement it in v1.

## Durable cleanup transaction and reactivation

- Append an immutable `ACCOUNT_CLOSED_INACTIVE` maintenance operation containing account ID, player UUID, timestamp, cleanup policy version, no money delta, and a monotonic sequence number; check or write required safety status atomically as a single logical record.
- `FileChannel.force()` the journal before removing the active account from memory or returning operator success. If append/force fails, make no account state changes and fail closed. The same principle applies to automated runs.
- Preserve a **minimal durable identity/tombstone**: player UUID, first-join grant claim, relevant permanent reward claims, permanently idempotent integration and settlement IDs, and any needed known-player identity. Cleanup is not identity erasure and does not automatically erase administrative audit references.
- The prior verified snapshot and journal must recover both account activity and closure exactly. Old historical `CREATE_ACCOUNT` journal entries may be retired only after the checkpoint incorporates the tombstone and all required claims.
- Existing offline player UUID remains a valid payee even after zero-balance cleanup. Future login or authorized incoming transfer can **reactivate** a zero-balance account. No automatic re-grant of starting Coins; preserving claim state is mandatory.
- If a player has a nonzero balance, do **not** sweep to treasury, burn, or silently adjust it. It is exempt from automatic closure. Admin-initiated transfer/closure, if implemented, uses ordinary authorized durable monetary transactions and a separate command/policy.

## Proposed commands (administrator-only and console-authorized)

```text
/scrip admin cleanup preview [inactivityDays]
/scrip admin cleanup run
/scrip admin cleanup status
/scrip admin cleanup set enabled <true|false>
/scrip admin cleanup set inactivity-days <days>
/scrip admin cleanup set interval-hours <hours>
/scrip admin journal compact preview
/scrip admin journal compact run
/scrip admin journal verify
```

`preview` is read-only and reports scanned, eligible, skipped by reason, and estimated reclaimed space. An optional preview age never changes stored configuration. `run` enforces the configured age and safety guards, logs operator, timestamp, affected UUIDs and durable receipt IDs in a bounded administrative audit (distinct from 100-record player history). Show exact counts after completion. Limit permissions with a dedicated cleanup/compaction admin privilege and do not allow GUI/client requests to perform these operations. Configuration change commands validate bounds and persist server config. Print alerts when backup fails or eligible accounts cannot be processed. Schedule automatic tasks off the tick hot path and process work incrementally.

## Journal compaction safety

1. Acquire a short serialized-commit checkpoint barrier; hold or queue new economy operations without blocking game tick execution.
2. Write a complete versioned snapshot to a temporary file containing balances, roles, known players, cleanup tombstones, active history (100/account), persistent one-shot claims, all protected idempotency/settlement identifiers, and audit/recovery state.
3. Flush/force the file, verify schema, count, balances, identifiers and checksum; move atomically where supported, retain previous verified snapshot.
4. Persist a manifest/checkpoint pointer, with atomic move and validated recovery rules for filesystems lacking `ATOMIC_MOVE`.
5. Only then retire journal segments whose max sequence <= the verified snapshot checkpoint. Do not truncate an active journal or remove pending settlement state. Rotation must be safe on restart midway through checkpointing.
6. `verify` checks snapshot/journal continuity, monotonic sequences, hashes/record checksums, total balance conservation for transfers and reference integrity; report recovery errors without silently creating fresh accounts.

Journal entries are a durable replay log and cannot be naively removed by timestamp. Old account history must not be confused with replay data. Compact on size threshold and optionally after completed cleanup, but never perform large compaction on the server tick thread.

## Test acceptance matrix

- Inactivity boundary: just under/over cutoff, recently logged-in old account, active-but-no-login account, known offline recipient, server clock adjustment.
- Account eligibility: zero vs positive balance; negative forbidden; frozen; owner/member org; account lock; unsettled shop purchase/refund; delayed reward; pending idempotent operation.
- Cleanup: dry run makes no writes; manual run, auto interval, empty batch, bounded scan, config hot reload/restart; no non-admin use; skip reasons precise.
- Durability: crash before/after journal append/force, after in-memory close, during snapshot and compaction, interrupted backup, full disk/read-only files, corrupt middle/tail.
- Idempotency: first-join grant cannot reissue after cleanup/reactivation; quest grants and shop settlements cannot replay; repeated cleanup command and remote requests are safe.
- Client interoperability: vanilla and modded clients; Forge and NeoForge 1.21.1; no tick freezes or unexpected network packet requirements.
- Retention: each surviving account holds <=100 visible entries; old journal segments removed only after verified checkpoint; older backups obey independent retention setting.
- Migration: never mistake previous pre-cleanup snapshots for current state or delete claims during version upgrades; upgrade validates backup and restores gracefully on failure.

## Implementation sequencing

P01 proves journal append/force, snapshot checksum/replay, and crash-resilience. P02 records account lifecycle and activity timestamps and durable identities. **P03-M** implements read-only preview/status and protected operator commands. **P10-M** implements automated schedule, safe durable account-closure operations, compaction and backup integration, with fault testing. No cleanup task is allowed to mutate storage until P01/P02 contracts pass. This keeps implementation work bounded.
