# $crip | Backup and restore runbook

## Status

The storage layer can create and verify a consistent local journal backup with a manifest and SHA-256 digests. Administrators may use `/scrip admin backup create`, `status`, and `verify`; create is queued on the storage writer and completion feedback is returned on the server thread. Do not use an unverified copied world directory as a financial backup.

## Backup contents

Each backup is a self-contained directory containing the committed journal, and, when present, the checkpoint manifest and its referenced snapshot. `backup.manifest` records the creation instant, latest durable sequence, checkpoint sequence, and SHA-256 digest for every copied artifact.

The backup operation is queued behind existing journal writes on the single storage writer. It therefore includes all writes acknowledged before the backup request and none which are acknowledged afterwards. It does not block a Minecraft server tick.

## Offline verification and restore procedure

1. Stop the server and retain the original world directory unchanged.
2. Verify the intended backup with `JournalFileLedgerStore.verifyBackup(...)`. A failed result is not restorable.
3. Choose a new, empty restore directory. The restore API refuses an existing nonempty directory and never overwrites a live world.
4. Run `JournalFileLedgerStore.restoreBackup(backupDirectory, emptyTargetDirectory)`.
5. Open the restored ledger and run its normal `verify()` recovery check before starting a server against it.
6. Compare the reported durable sequence to the backup receipt. Do not replace the original world until this check succeeds.

## Failure handling

Corrupt manifests, absent committed journal files, digest mismatches, malformed journal frames, unsupported future schemas, and mid-journal corruption fail closed. A torn final journal tail remains the only repairable case and is quarantined by the existing journal recovery rule. No failure creates an empty ledger or resets balances, claims, organizations, idempotency records, or settlements.

## Current P10 stop condition

Account-ledger recovery now consumes a canonical operation-history checkpoint, so it preserves accounts, organizations, permanent claims, idempotency records, and settlement records through a checkpoint restart. This representation is bounded by the storage snapshot payload limit and grows with the journal. It is therefore not yet a production-safe replacement for a compact domain-state snapshot. Backup scheduling, backup-retention deletion, and journal segment retirement remain disabled until a compact account-state snapshot and its migration coverage are proven.
