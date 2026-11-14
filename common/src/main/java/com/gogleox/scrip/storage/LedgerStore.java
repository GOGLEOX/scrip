package com.gogleox.scrip.storage;

import java.util.concurrent.CompletionStage;

/**
 * Loader-neutral persistence boundary. Implementations must not acknowledge a commit before it is durable.
 * P01 intentionally stores opaque operation bytes; account semantics belong to P02.
 */
public interface LedgerStore extends AutoCloseable {
    CompletionStage<CommitReceipt> commit(JournalOperation operation);

    RecoveryState recover();

    VerificationReport verify();

    CompletionStage<SnapshotReceipt> checkpoint(VersionedSnapshot snapshot);

    @Override
    void close();
}
