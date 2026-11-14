# ADR-0002 | Stale-account cleanup and safe journal cleansing

**Decision:** ACCEPTED CAPABILITY / RECOMMENDED SAFETY POLICY  
**User authorization:** 2026-11-12  
**Status:** Planning only.

## User-approved capability
- Administrator command for manual journal/account maintenance.
- Automatic adjustable cleanup of stale accounts after a configurable inactivity duration.
- Use durable snapshot/journal instead of embedded SQLite.

## Recommended policy for implementation
- Default automatic eligibility scan enabled, inactivity threshold 365 days, check interval 24 hours; all adjustable server-side.
- Only offline, zero-balance personal accounts with no organization obligations, frozen/protected flags, holds, or unresolved settlements may be closed automatically. Positive-balance accounts, organizations and system accounts remain indefinitely unless handled by a separately approved explicit administrative policy.
- Closed accounts receive forced durable `ACCOUNT_CLOSED_INACTIVE` record and minimal retained tombstone. Never discard UUID, known-player offline eligibility, first-join claim, permanent reward grants, or protected shop settlement/dedupe references.
- `cleanup preview`, `cleanup run`, `cleanup status`, `cleanup set ...`, `journal compact preview|run`, `journal verify` are namespaced admin-only command proposals.
- Journal compaction is checkpoint/verify/retire, not deleting arbitrary old records. Backups are separate. Stop when a safe recovery anchor cannot be verified.

## Decisions requiring new user authorization
- Automatic forfeiture, burning, transfer to treasury, or removal of accounts holding nonzero Coins.
- Automatic organization/system account closure.
- Physical erasure of permanent reward claims or previously verified offline recipient IDs.

**Full criteria, command syntax, configuration and test matrix:** `docs/MAINTENANCE_AND_CLEANUP.md`.
