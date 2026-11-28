package com.gogleox.scrip.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JournalFileLedgerStoreTest {
    @TempDir Path directory;

    @Test void forcedCommitSurvivesRestartAndReplayIsDeterministic() {
        JournalOperation operation = operation("create", "first");
        try (var store = JournalFileLedgerStore.open(directory)) {
            assertEquals(1, store.commit(operation).toCompletableFuture().join().sequence());
        }
        try (var reopened = JournalFileLedgerStore.open(directory)) {
            RecoveryState first = reopened.recover();
            RecoveryState second = reopened.recover();
            assertEquals(1, first.latestSequence());
            assertEquals(first.entries().size(), second.entries().size());
            assertEquals(first.entries().getFirst().sequence(), second.entries().getFirst().sequence());
            JournalOperation replayed = first.entries().getFirst().operation();
            assertEquals(operation.operationId(), replayed.operationId());
            assertEquals(operation.type(), replayed.type());
            assertArrayEquals(operation.payload(), replayed.payload());
        }
    }

    @Test void appendFailuresNeverReturnAReceiptAndTornTailIsRepaired() {
        for (FaultPoint point : List.of(FaultPoint.BEFORE_APPEND, FaultPoint.DURING_APPEND,
                FaultPoint.BEFORE_FORCE, FaultPoint.DURING_FORCE)) {
            Path testDirectory = directory.resolve(point.name());
            JournalOperation operation = operation("fault", point.name());
            try (var store = JournalFileLedgerStore.open(testDirectory, 8, failAt(point))) {
                assertCommitFails(store, operation);
            }
            try (var reopened = JournalFileLedgerStore.open(testDirectory)) {
                // A torn append is discarded. A complete record written before a simulated force fault
                // is recoverable, but it was never acknowledged to the caller.
                assertTrue(reopened.recover().entries().size() <= 1);
            }
        }
    }

    @Test void diskFullAndReadOnlyFailuresDoNotAcknowledgeOperations() {
        try (var diskFull = JournalFileLedgerStore.open(directory.resolve("full"), 8,
                point -> { if (point == FaultPoint.BEFORE_APPEND) throw new FileSystemException("journal", null, "No space left on device"); })) {
            assertCommitFails(diskFull, operation("create", "full"));
        }
        try (var readOnly = JournalFileLedgerStore.open(directory.resolve("readonly"), 8,
                point -> { if (point == FaultPoint.OPEN_JOURNAL) throw new AccessDeniedException("journal"); })) {
            assertCommitFails(readOnly, operation("create", "readonly"));
        }
    }

    @Test void corruptMiddleRecordFailsClosed() throws IOException {
        try (var store = JournalFileLedgerStore.open(directory)) {
            store.commit(operation("one", "a")).toCompletableFuture().join();
            store.commit(operation("two", "b")).toCompletableFuture().join();
        }
        Path journal = directory.resolve("ledger.journal");
        try (FileChannel channel = FileChannel.open(journal, StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(new byte[] {(byte) 0x7F}), Integer.BYTES + 20);
        }
        assertThrows(StorageException.class, () -> JournalFileLedgerStore.open(directory));
    }

    @Test void truncatedTailIsQuarantinedWithoutDiscardingValidRecords() throws IOException {
        try (var store = JournalFileLedgerStore.open(directory)) {
            store.commit(operation("one", "a")).toCompletableFuture().join();
        }
        Files.write(directory.resolve("ledger.journal"), new byte[] {0, 0, 1}, StandardOpenOption.APPEND);
        try (var reopened = JournalFileLedgerStore.open(directory)) {
            RecoveryState state = reopened.recover();
            assertEquals(1, state.entries().size());
        }
        try (var reopenedAgain = JournalFileLedgerStore.open(directory)) {
            assertEquals(1, reopenedAgain.recover().entries().size());
        }
    }

    @Test void duplicateOperationIdIsRejectedBeforeANewRecordIsWritten() {
        UUID id = UUID.randomUUID();
        try (var store = JournalFileLedgerStore.open(directory)) {
            store.commit(new JournalOperation(id, "transfer", new byte[] {1})).toCompletableFuture().join();
            assertCommitFails(store, new JournalOperation(id, "transfer", new byte[] {1}));
            assertEquals(1, store.recover().entries().size());
        }
    }

    @Test void checkpointIsVerifiedAndReplayStartsAfterItsSequence() {
        try (var store = JournalFileLedgerStore.open(directory)) {
            store.commit(operation("one", "a")).toCompletableFuture().join();
            store.checkpoint(new VersionedSnapshot(1, 1, new byte[] {9, 8, 7})).toCompletableFuture().join();
            store.commit(operation("two", "b")).toCompletableFuture().join();
        }
        try (var reopened = JournalFileLedgerStore.open(directory)) {
            RecoveryState state = reopened.recover();
            assertEquals(1, state.snapshot().orElseThrow().sequence());
            assertEquals(1, state.entries().size());
            assertEquals("two", state.entries().getFirst().operation().type());
            assertEquals(2, reopened.verify().latestSequence());
        }
    }

    @Test void interruptedSnapshotCreationLeavesThePreviousRecoveryPointUsable() {
        try (var store = JournalFileLedgerStore.open(directory, 8, failAt(FaultPoint.BEFORE_SNAPSHOT_WRITE))) {
            store.commit(operation("one", "a")).toCompletableFuture().join();
            assertCheckpointFails(store, new VersionedSnapshot(1, 1, new byte[] {1}));
        }
        try (var reopened = JournalFileLedgerStore.open(directory)) {
            assertTrue(reopened.recover().snapshot().isEmpty());
            assertEquals(1, reopened.recover().entries().size());
        }
    }

    @Test void interruptedSnapshotReplacementKeepsTheCommittedCheckpoint() {
        try (var initial = JournalFileLedgerStore.open(directory)) {
            initial.commit(operation("one", "a")).toCompletableFuture().join();
            initial.checkpoint(new VersionedSnapshot(1, 1, new byte[] {1})).toCompletableFuture().join();
            initial.commit(operation("two", "b")).toCompletableFuture().join();
        }
        try (var interrupted = JournalFileLedgerStore.open(directory, 8, failAt(FaultPoint.BEFORE_SNAPSHOT_REPLACEMENT))) {
            assertCheckpointFails(interrupted, new VersionedSnapshot(1, 2, new byte[] {2}));
        }
        try (var reopened = JournalFileLedgerStore.open(directory)) {
            RecoveryState state = reopened.recover();
            assertEquals(1, state.snapshot().orElseThrow().sequence());
            assertEquals(1, state.entries().size());
            assertEquals(2, state.entries().getFirst().sequence());
        }
    }

    @Test void checkpointCannotReplaceTheCurrentRecoveryAnchorAtTheSameSequence() {
        try (var store = JournalFileLedgerStore.open(directory)) {
            store.commit(operation("one", "a")).toCompletableFuture().join();
            store.checkpoint(new VersionedSnapshot(1, 1, new byte[] {1})).toCompletableFuture().join();
            assertCheckpointFails(store, new VersionedSnapshot(1, 1, new byte[] {9}));
        }
        try (var reopened = JournalFileLedgerStore.open(directory)) {
            assertArrayEquals(new byte[] {1}, reopened.recover().snapshot().orElseThrow().state());
        }
    }

    @Test void invalidSnapshotChecksumFailsClosed() throws IOException {
        try (var store = JournalFileLedgerStore.open(directory)) {
            store.commit(operation("one", "a")).toCompletableFuture().join();
            store.checkpoint(new VersionedSnapshot(1, 1, new byte[] {1})).toCompletableFuture().join();
        }
        Path snapshot = Files.list(directory).filter(path -> path.getFileName().toString().startsWith("snapshot-")).findFirst().orElseThrow();
        try (FileChannel channel = FileChannel.open(snapshot, StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(new byte[] {(byte) 0x55}), 12);
        }
        assertThrows(StorageException.class, () -> JournalFileLedgerStore.open(directory));
    }

    @Test void newerSnapshotSchemaFailsClosed() throws IOException {
        try (var store = JournalFileLedgerStore.open(directory)) {
            store.commit(operation("one", "a")).toCompletableFuture().join();
            store.checkpoint(new VersionedSnapshot(1, 1, new byte[] {1})).toCompletableFuture().join();
        }
        Path snapshot = Files.list(directory).filter(path -> path.getFileName().toString().startsWith("snapshot-")).findFirst().orElseThrow();
        try (FileChannel channel = FileChannel.open(snapshot, StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(new byte[] {0, 0, 0, 2}), Integer.BYTES * 2L);
        }
        assertThrows(StorageException.class, () -> JournalFileLedgerStore.open(directory));
    }

    @Test void oneBoundedWriterProducesAContinuousSequenceUnderConcurrency() throws Exception {
        try (var store = JournalFileLedgerStore.open(directory, 64, FaultInjector.NONE);
             ExecutorService callers = Executors.newFixedThreadPool(8)) {
            List<Future<?>> callersDone = new ArrayList<>();
            for (int index = 0; index < 40; index++) {
                int value = index;
                callersDone.add(callers.submit(() -> store.commit(operation("work", Integer.toString(value))).toCompletableFuture().join()));
            }
            for (Future<?> caller : callersDone) {
                caller.get();
            }
            List<Long> sequences = store.recover().entries().stream().map(JournalEntry::sequence).sorted().toList();
            assertEquals(40, sequences.size());
            for (int index = 0; index < sequences.size(); index++) {
                assertEquals(index + 1L, sequences.get(index));
            }
        }
    }

    @Test void backupIsConsistentDuringActivityAndRestoresOffline() {
        Path backupRoot = directory.resolve("backups");
        BackupReceipt backup;
        try (var store = JournalFileLedgerStore.open(directory.resolve("live"))) {
            store.commit(operation("one", "a")).toCompletableFuture().join();
            backup = store.backup(backupRoot).toCompletableFuture().join();
            store.commit(operation("two", "b")).toCompletableFuture().join();
        }
        BackupVerification verification = JournalFileLedgerStore.verifyBackup(backup.directory());
        assertTrue(verification.valid(), verification.detail());
        assertEquals(1, verification.latestSequence());
        Path restored = directory.resolve("restored");
        JournalFileLedgerStore.restoreBackup(backup.directory(), restored);
        try (var restoredStore = JournalFileLedgerStore.open(restored)) {
            assertEquals(1, restoredStore.recover().latestSequence());
            assertEquals("one", restoredStore.recover().entries().getFirst().operation().type());
        }
    }

    @Test void corruptedBackupAndNonemptyRestoreTargetFailClosed() throws IOException {
        BackupReceipt backup;
        try (var store = JournalFileLedgerStore.open(directory.resolve("live"))) {
            store.commit(operation("one", "a")).toCompletableFuture().join();
            backup = store.backup(directory.resolve("backups")).toCompletableFuture().join();
        }
        Path cleanTarget = directory.resolve("nonempty");
        Files.createDirectories(cleanTarget);
        Files.writeString(cleanTarget.resolve("keep"), "do not overwrite");
        assertThrows(StorageException.class, () -> JournalFileLedgerStore.restoreBackup(backup.directory(), cleanTarget));

        Files.write(backup.directory().resolve("ledger.journal"), new byte[] {99}, StandardOpenOption.APPEND);
        assertFalse(JournalFileLedgerStore.verifyBackup(backup.directory()).valid());
        assertThrows(StorageException.class, () -> JournalFileLedgerStore.restoreBackup(backup.directory(), directory.resolve("target")));
    }

    private static JournalOperation operation(String type, String payload) {
        return new JournalOperation(UUID.randomUUID(), type, payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static FaultInjector failAt(FaultPoint target) {
        return point -> {
            if (point == target) {
                throw new IOException("Injected failure at " + target);
            }
        };
    }

    private static void assertCommitFails(LedgerStore store, JournalOperation operation) {
        assertThrows(CompletionException.class, () -> store.commit(operation).toCompletableFuture().join());
    }

    private static void assertCheckpointFails(LedgerStore store, VersionedSnapshot snapshot) {
        assertThrows(CompletionException.class, () -> store.checkpoint(snapshot).toCompletableFuture().join());
    }
}
