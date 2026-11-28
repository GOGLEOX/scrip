package com.gogleox.scrip.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;

/**
 * Pure Java, per-world journal implementation. A single bounded writer establishes the commit order.
 * It writes opaque operations because account state and validation start in P02.
 */
public final class JournalFileLedgerStore implements LedgerStore {
    private static final int JOURNAL_MAGIC = 0x53434A31; // SCJ1
    private static final int SNAPSHOT_MAGIC = 0x53435331; // SCS1
    private static final int MANIFEST_MAGIC = 0x53434D31; // SCM1
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_RECORD_BYTES = 1_048_576;
    private static final String JOURNAL_NAME = "ledger.journal";
    private static final String MANIFEST_NAME = "checkpoint.manifest";
    private static final String BACKUP_MANIFEST_NAME = "backup.manifest";
    private static final String BACKUP_MAGIC = "SCRIP_BACKUP_V1";

    private final Path directory;
    private final Path journal;
    private final Path manifest;
    private final FaultInjector faults;
    private final ThreadPoolExecutor writer;
    private final Set<UUID> committedOperationIds = new HashSet<>();
    private long nextSequence;
    private boolean closed;
    private boolean failed;

    private JournalFileLedgerStore(Path directory, int queueCapacity, FaultInjector faults) {
        this.directory = directory;
        this.journal = directory.resolve(JOURNAL_NAME);
        this.manifest = directory.resolve(MANIFEST_NAME);
        this.faults = faults == null ? FaultInjector.NONE : faults;
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "scrip-journal-writer");
            thread.setDaemon(true);
            return thread;
        };
        this.writer = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity), factory, new ThreadPoolExecutor.AbortPolicy());
    }

    public static JournalFileLedgerStore open(Path directory) {
        return open(directory, 128, FaultInjector.NONE);
    }

    public static JournalFileLedgerStore open(Path directory, int queueCapacity, FaultInjector faults) {
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("Writer queue capacity must be positive");
        }
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw new StorageException("Cannot create ledger directory", exception);
        }
        JournalFileLedgerStore store = new JournalFileLedgerStore(directory, queueCapacity, faults);
        RecoveryState recovery = store.readRecovery(true);
        for (JournalEntry entry : store.readAllJournalEntries()) {
            store.committedOperationIds.add(entry.operation().operationId());
        }
        store.nextSequence = recovery.latestSequence() + 1;
        return store;
    }

    /** Root reserved for verified backups; it is never replayed as ledger state. */
    public Path backupRoot() { return directory.resolve("backups"); }

    public CompletionStage<Optional<BackupVerification>> verifyLatestBackup() {
        return CompletableFuture.supplyAsync(() -> {
            Path root = backupRoot();
            if (!Files.isDirectory(root)) return Optional.empty();
            try (var candidates = Files.list(root)) {
                Optional<Path> latest = candidates.filter(Files::isDirectory)
                        .filter(path -> Files.isRegularFile(path.resolve(BACKUP_MANIFEST_NAME)))
                        .max(java.util.Comparator.comparing(path -> path.getFileName().toString()));
                return latest.map(JournalFileLedgerStore::verifyBackup);
            } catch (IOException exception) {
                throw new StorageException("Cannot inspect backup status", exception);
            }
        });
    }

    /**
     * Creates a point-in-time backup after every write already queued before it. The writer lock prevents an
     * append or checkpoint from changing source files while their digests are calculated and copied.
     */
    public CompletionStage<BackupReceipt> backup(Path backupRoot) {
        Objects.requireNonNull(backupRoot, "backupRoot");
        CompletableFuture<BackupReceipt> result = new CompletableFuture<>();
        synchronized (this) {
            if (closed || failed) {
                result.completeExceptionally(new StorageException("Ledger store is unavailable"));
                return result;
            }
            try {
                writer.execute(() -> writeBackup(backupRoot, result));
            } catch (RuntimeException exception) {
                result.completeExceptionally(new StorageException("Journal writer queue is full", exception));
            }
        }
        return result;
    }

    /** Validates a backup without modifying it. Invalid manifests and hashes fail closed. */
    public static BackupVerification verifyBackup(Path backupDirectory) {
        try {
            BackupManifest manifest = readBackupManifest(backupDirectory.resolve(BACKUP_MANIFEST_NAME));
            List<String> names = new ArrayList<>();
            for (BackupFile file : manifest.files()) {
                Path path = safeChild(backupDirectory, file.name());
                if (!Files.isRegularFile(path) || !sha256(path).equals(file.sha256())) {
                    throw new StorageException("Backup file checksum failed: " + file.name());
                }
                names.add(file.name());
            }
            Path journal = safeChild(backupDirectory, JOURNAL_NAME);
            if (!Files.exists(journal)) throw new StorageException("Backup is missing the committed journal");
            try (JournalFileLedgerStore store = JournalFileLedgerStore.open(backupDirectory)) {
                VerificationReport report = store.verify();
                if (report.latestSequence() != manifest.latestSequence()) {
                    throw new StorageException("Backup journal sequence does not match manifest");
                }
            }
            return new BackupVerification(true, manifest.latestSequence(), manifest.checkpointSequence(), manifest.createdAt(), names, "verified");
        } catch (IOException | RuntimeException exception) {
            return new BackupVerification(false, 0, 0, Instant.EPOCH, List.of(), exception.getMessage() == null ? "backup verification failed" : exception.getMessage());
        }
    }

    /**
     * Offline restore only. The target must not contain ledger data; this method never replaces a live world.
     */
    public static BackupReceipt restoreBackup(Path backupDirectory, Path targetDirectory) {
        BackupVerification verification = verifyBackup(backupDirectory);
        if (!verification.valid()) throw new StorageException("Backup is not restorable: " + verification.detail());
        try {
            if (Files.exists(targetDirectory)) {
                try (var contents = Files.list(targetDirectory)) {
                    if (contents.findAny().isPresent()) throw new StorageException("Restore target must be empty");
                }
            }
            Files.createDirectories(targetDirectory);
            for (String name : verification.files()) {
                Files.copy(safeChild(backupDirectory, name), safeChild(targetDirectory, name), StandardCopyOption.COPY_ATTRIBUTES);
            }
            try (JournalFileLedgerStore restored = JournalFileLedgerStore.open(targetDirectory)) {
                VerificationReport report = restored.verify();
                if (report.latestSequence() != verification.latestSequence()) throw new StorageException("Restored sequence does not match backup");
            }
            return new BackupReceipt(targetDirectory, verification.latestSequence(), verification.checkpointSequence(), verification.createdAt());
        } catch (IOException exception) {
            throw new StorageException("Offline restore failed", exception);
        }
    }

    @Override
    public CompletionStage<CommitReceipt> commit(JournalOperation operation) {
        CompletableFuture<CommitReceipt> result = new CompletableFuture<>();
        synchronized (this) {
            if (closed) {
                result.completeExceptionally(new StorageException("Ledger store is closed"));
                return result;
            }
            if (failed) {
                result.completeExceptionally(new StorageException("Ledger store needs recovery after a write failure"));
                return result;
            }
            try {
                writer.execute(() -> append(operation, result));
            } catch (RuntimeException exception) {
                result.completeExceptionally(new StorageException("Journal writer queue is full", exception));
            }
        }
        return result;
    }

    private void append(JournalOperation operation, CompletableFuture<CommitReceipt> result) {
        synchronized (this) {
            if (failed || closed) {
                result.completeExceptionally(new StorageException("Ledger store is unavailable"));
                return;
            }
            if (committedOperationIds.contains(operation.operationId())) {
                result.completeExceptionally(new StorageException("Duplicate journal operation identifier"));
                return;
            }
            long sequence = nextSequence;
            boolean writeStarted = false;
            try {
                faults.at(FaultPoint.OPEN_JOURNAL);
                byte[] frame = frame(sequence, operation);
                faults.at(FaultPoint.BEFORE_APPEND);
                try (FileChannel channel = FileChannel.open(journal, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                        StandardOpenOption.APPEND)) {
                    writeStarted = true;
                    writeFrame(channel, frame, FaultPoint.DURING_APPEND);
                    faults.at(FaultPoint.BEFORE_FORCE);
                    faults.at(FaultPoint.DURING_FORCE);
                    channel.force(true);
                }
                committedOperationIds.add(operation.operationId());
                nextSequence++;
                result.complete(new CommitReceipt(sequence, operation.operationId()));
            } catch (IOException | RuntimeException exception) {
                if (writeStarted) {
                    failed = true;
                }
                result.completeExceptionally(new StorageException("Journal commit was not acknowledged", exception));
            }
        }
    }

    @Override
    public RecoveryState recover() {
        synchronized (this) {
            return readRecovery(true);
        }
    }

    @Override
    public VerificationReport verify() {
        synchronized (this) {
            RecoveryState recovery = readRecovery(false);
            return new VerificationReport(recovery.latestSequence(), recovery.entries().size(), recovery.snapshot().isPresent());
        }
    }

    @Override
    public CompletionStage<SnapshotReceipt> checkpoint(VersionedSnapshot snapshot) {
        CompletableFuture<SnapshotReceipt> result = new CompletableFuture<>();
        synchronized (this) {
            if (closed || failed) {
                result.completeExceptionally(new StorageException("Ledger store is unavailable"));
                return result;
            }
            if (snapshot.sequence() > nextSequence - 1) {
                result.completeExceptionally(new StorageException("Checkpoint sequence is ahead of the durable journal"));
                return result;
            }
            try {
                writer.execute(() -> writeCheckpoint(snapshot, result));
            } catch (RuntimeException exception) {
                result.completeExceptionally(new StorageException("Journal writer queue is full", exception));
            }
        }
        return result;
    }

    private void writeCheckpoint(VersionedSnapshot snapshot, CompletableFuture<SnapshotReceipt> result) {
        synchronized (this) {
            try {
                Optional<VersionedSnapshot> existing = readCommittedSnapshot();
                if (existing.isPresent() && snapshot.sequence() <= existing.get().sequence()) {
                    throw new StorageException("Checkpoint sequence must advance the existing recovery anchor");
                }
                faults.at(FaultPoint.BEFORE_SNAPSHOT_WRITE);
                byte[] snapshotBytes = snapshotBytes(snapshot);
                String name = "snapshot-" + snapshot.sequence() + ".bin";
                Path target = directory.resolve(name);
                Path temporary = Files.createTempFile(directory, "snapshot-", ".tmp");
                try {
                    writeFile(temporary, snapshotBytes, FaultPoint.DURING_SNAPSHOT_WRITE, FaultPoint.BEFORE_SNAPSHOT_FORCE);
                    readSnapshot(temporary); // Validate before publishing a checkpoint.
                    faults.at(FaultPoint.BEFORE_SNAPSHOT_REPLACEMENT);
                    moveReplace(temporary, target);
                } finally {
                    Files.deleteIfExists(temporary);
                }
                CheckpointManifest checkpoint = new CheckpointManifest(name, snapshot.sequence(), checksum(snapshotBytes));
                Path manifestTemporary = Files.createTempFile(directory, "manifest-", ".tmp");
                try {
                    writeFile(manifestTemporary, manifestBytes(checkpoint), null, null);
                    readManifest(manifestTemporary);
                    faults.at(FaultPoint.BEFORE_MANIFEST_REPLACEMENT);
                    moveReplace(manifestTemporary, manifest);
                } finally {
                    Files.deleteIfExists(manifestTemporary);
                }
                result.complete(new SnapshotReceipt(snapshot.sequence(), name));
            } catch (IOException | RuntimeException exception) {
                result.completeExceptionally(new StorageException("Checkpoint was not acknowledged", exception));
            }
        }
    }

    private void writeBackup(Path backupRoot, CompletableFuture<BackupReceipt> result) {
        synchronized (this) {
            Path temporary = null;
            try {
                Files.createDirectories(backupRoot);
                Replay replay = readJournal(false);
                Optional<VersionedSnapshot> snapshot = readCommittedSnapshot();
                long checkpoint = snapshot.map(VersionedSnapshot::sequence).orElse(0L);
                long latest = Math.max(checkpoint, replay.latestSequence());
                Instant created = Instant.now();
                String backupName = "backup-" + latest + "-" + created.toEpochMilli();
                Path target = safeChild(backupRoot, backupName);
                if (Files.exists(target)) throw new StorageException("Backup target already exists");
                temporary = Files.createTempDirectory(backupRoot, ".backup-");
                List<BackupFile> files = new ArrayList<>();
                copyBackupFile(journal, temporary, JOURNAL_NAME, files);
                if (Files.exists(manifest)) {
                    CheckpointManifest checkpointManifest = readManifest(manifest);
                    copyBackupFile(directory.resolve(checkpointManifest.snapshotFile()), temporary, checkpointManifest.snapshotFile(), files);
                    copyBackupFile(manifest, temporary, MANIFEST_NAME, files);
                }
                writeBackupManifest(temporary.resolve(BACKUP_MANIFEST_NAME), new BackupManifest(created, latest, checkpoint, files));
                verifyBackupOrThrow(temporary);
                moveReplace(temporary, target);
                temporary = null;
                result.complete(new BackupReceipt(target, latest, checkpoint, created));
            } catch (IOException | RuntimeException exception) {
                result.completeExceptionally(new StorageException("Backup was not acknowledged", exception));
            } finally {
                if (temporary != null) {
                    try { deleteTree(temporary); } catch (IOException ignored) { }
                }
            }
        }
    }

    private static void copyBackupFile(Path source, Path destinationDirectory, String name, List<BackupFile> files) throws IOException {
        if (!Files.isRegularFile(source)) throw new StorageException("Backup source is missing: " + name);
        Path destination = safeChild(destinationDirectory, name);
        Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
        try (FileChannel channel = FileChannel.open(destination, StandardOpenOption.WRITE)) { channel.force(true); }
        files.add(new BackupFile(name, sha256(destination)));
    }

    private RecoveryState readRecovery(boolean repairTail) {
        Optional<VersionedSnapshot> snapshot = readCommittedSnapshot();
        long checkpoint = snapshot.map(VersionedSnapshot::sequence).orElse(0L);
        Replay replay = readJournal(repairTail);
        List<JournalEntry> afterCheckpoint = replay.entries().stream()
                .filter(entry -> entry.sequence() > checkpoint)
                .toList();
        long latest = Math.max(checkpoint, replay.latestSequence());
        return new RecoveryState(snapshot, afterCheckpoint, latest, replay.truncatedTail());
    }

    private Optional<VersionedSnapshot> readCommittedSnapshot() {
        if (!Files.exists(manifest)) {
            return Optional.empty();
        }
        CheckpointManifest checkpoint = readManifest(manifest);
        Path snapshotPath = directory.resolve(checkpoint.snapshotFile());
        if (!snapshotPath.normalize().getParent().equals(directory.normalize())) {
            throw new StorageException("Checkpoint manifest references an invalid snapshot path");
        }
        try {
            byte[] bytes = Files.readAllBytes(snapshotPath);
            if (checksum(bytes) != checkpoint.snapshotChecksum()) {
                throw new StorageException("Checkpoint snapshot checksum does not match manifest");
            }
            VersionedSnapshot snapshot = readSnapshotBytes(bytes);
            if (snapshot.sequence() != checkpoint.sequence()) {
                throw new StorageException("Checkpoint sequence does not match manifest");
            }
            return Optional.of(snapshot);
        } catch (IOException exception) {
            throw new StorageException("Cannot read checkpoint snapshot", exception);
        }
    }

    private List<JournalEntry> readAllJournalEntries() {
        return readJournal(false).entries();
    }

    private Replay readJournal(boolean repairTail) {
        if (!Files.exists(journal)) {
            return new Replay(List.of(), 0, false);
        }
        List<JournalEntry> entries = new ArrayList<>();
        Set<UUID> ids = new HashSet<>();
        long lastSequence = 0;
        long validLength = 0;
        boolean tail = false;
        try (FileChannel channel = FileChannel.open(journal, StandardOpenOption.READ)) {
            long size = channel.size();
            long offset = 0;
            while (offset < size) {
                long recordStart = offset;
                if (size - offset < Integer.BYTES) {
                    tail = true;
                    break;
                }
                ByteBuffer lengthBuffer = ByteBuffer.allocate(Integer.BYTES);
                readFully(channel, lengthBuffer, offset);
                offset += Integer.BYTES;
                int length = lengthBuffer.flip().getInt();
                if (length < 1 || length > MAX_RECORD_BYTES) {
                    throw new StorageException("Invalid journal record length at byte " + recordStart);
                }
                if (size - offset < length) {
                    tail = true;
                    break;
                }
                ByteBuffer body = ByteBuffer.allocate(length);
                readFully(channel, body, offset);
                offset += length;
                JournalEntry entry = parseEntry(body.array());
                if (entry.sequence() <= lastSequence) {
                    throw new StorageException("Journal sequence is not strictly increasing");
                }
                if (!ids.add(entry.operation().operationId())) {
                    throw new StorageException("Duplicate operation identifier in journal");
                }
                entries.add(entry);
                lastSequence = entry.sequence();
                validLength = offset;
            }
            if (tail && repairTail) {
                try (FileChannel writable = FileChannel.open(journal, StandardOpenOption.WRITE)) {
                    writable.truncate(validLength);
                    writable.force(true);
                }
            }
        } catch (IOException exception) {
            throw new StorageException("Cannot read journal", exception);
        }
        return new Replay(entries, lastSequence, tail);
    }

    private static void readFully(FileChannel channel, ByteBuffer buffer, long position) throws IOException {
        while (buffer.hasRemaining()) {
            int count = channel.read(buffer, position);
            if (count < 0) {
                throw new EOFException("Unexpected end of file");
            }
            position += count;
        }
    }

    private byte[] frame(long sequence, JournalOperation operation) throws IOException {
        byte[] body = entryBody(sequence, operation);
        ByteBuffer frame = ByteBuffer.allocate(Integer.BYTES + body.length);
        frame.putInt(body.length).put(body);
        return frame.array();
    }

    private static byte[] entryBody(long sequence, JournalOperation operation) {
        try {
            byte[] type = operation.type().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] payload = operation.payload();
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(raw)) {
                out.writeInt(JOURNAL_MAGIC);
                out.writeInt(FORMAT_VERSION);
                out.writeLong(sequence);
                out.writeLong(operation.operationId().getMostSignificantBits());
                out.writeLong(operation.operationId().getLeastSignificantBits());
                out.writeInt(type.length);
                out.write(type);
                out.writeInt(payload.length);
                out.write(payload);
                out.writeInt(checksum(raw.toByteArray()));
            }
            return raw.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static JournalEntry parseEntry(byte[] body) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(body))) {
            if (in.readInt() != JOURNAL_MAGIC || in.readInt() != FORMAT_VERSION) {
                throw new StorageException("Unsupported journal format");
            }
            long sequence = in.readLong();
            UUID id = new UUID(in.readLong(), in.readLong());
            byte[] type = readSized(in, 128);
            byte[] payload = readSized(in, MAX_RECORD_BYTES);
            int actual = in.readInt();
            if (in.available() != 0) {
                throw new StorageException("Journal record contains trailing bytes");
            }
            if (actual != checksum(Arrays.copyOf(body, body.length - Integer.BYTES))) {
                throw new StorageException("Journal record checksum failed");
            }
            return new JournalEntry(sequence, new JournalOperation(id,
                    new String(type, java.nio.charset.StandardCharsets.UTF_8), payload));
        } catch (IOException | IllegalArgumentException exception) {
            throw new StorageException("Cannot decode journal record", exception);
        }
    }

    private static byte[] readSized(DataInputStream in, int maximum) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > maximum) {
            throw new StorageException("Invalid encoded length");
        }
        byte[] value = in.readNBytes(length);
        if (value.length != length) {
            throw new EOFException("Unexpected end of encoded field");
        }
        return value;
    }

    private static void writeFully(FileChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    private void writeFrame(FileChannel channel, byte[] bytes, FaultPoint midWrite) throws IOException {
        int first = Math.min(bytes.length, 17);
        writeFully(channel, ByteBuffer.wrap(bytes, 0, first));
        faults.at(midWrite);
        writeFully(channel, ByteBuffer.wrap(bytes, first, bytes.length - first));
    }

    private void writeFile(Path target, byte[] bytes, FaultPoint duringWrite, FaultPoint beforeForce) throws IOException {
        try (FileChannel channel = FileChannel.open(target, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            int first = Math.min(bytes.length, 17);
            writeFully(channel, ByteBuffer.wrap(bytes, 0, first));
            if (duringWrite != null) {
                faults.at(duringWrite);
            }
            writeFully(channel, ByteBuffer.wrap(bytes, first, bytes.length - first));
            if (beforeForce != null) {
                faults.at(beforeForce);
            }
            channel.force(true);
        }
    }

    private static byte[] snapshotBytes(VersionedSnapshot snapshot) {
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(raw)) {
                out.writeInt(SNAPSHOT_MAGIC);
                out.writeInt(FORMAT_VERSION);
                out.writeInt(snapshot.schemaVersion());
                out.writeLong(snapshot.sequence());
                byte[] state = snapshot.state();
                out.writeInt(state.length);
                out.write(state);
                out.writeInt(checksum(raw.toByteArray()));
            }
            return raw.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static VersionedSnapshot readSnapshot(Path path) {
        try {
            return readSnapshotBytes(Files.readAllBytes(path));
        } catch (IOException exception) {
            throw new StorageException("Cannot validate temporary snapshot", exception);
        }
    }

    private static VersionedSnapshot readSnapshotBytes(byte[] bytes) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != SNAPSHOT_MAGIC || in.readInt() != FORMAT_VERSION) {
                throw new StorageException("Unsupported snapshot format");
            }
            int schema = in.readInt();
            if (schema > FORMAT_VERSION) {
                throw new StorageException("Snapshot schema is newer than this server supports");
            }
            long sequence = in.readLong();
            byte[] state = readSized(in, MAX_RECORD_BYTES);
            int actual = in.readInt();
            if (in.available() != 0 || actual != checksum(Arrays.copyOf(bytes, bytes.length - Integer.BYTES))) {
                throw new StorageException("Snapshot checksum failed");
            }
            return new VersionedSnapshot(schema, sequence, state);
        } catch (IOException | IllegalArgumentException exception) {
            throw new StorageException("Cannot decode snapshot", exception);
        }
    }

    private static byte[] manifestBytes(CheckpointManifest checkpoint) {
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(raw)) {
                out.writeInt(MANIFEST_MAGIC);
                out.writeInt(FORMAT_VERSION);
                out.writeUTF(checkpoint.snapshotFile());
                out.writeLong(checkpoint.sequence());
                out.writeInt(checkpoint.snapshotChecksum());
                out.writeInt(checksum(raw.toByteArray()));
            }
            return raw.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static CheckpointManifest readManifest(Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
                if (in.readInt() != MANIFEST_MAGIC || in.readInt() != FORMAT_VERSION) {
                    throw new StorageException("Unsupported checkpoint manifest format");
                }
                String file = in.readUTF();
                long sequence = in.readLong();
                int snapshotChecksum = in.readInt();
                int actual = in.readInt();
                if (in.available() != 0 || actual != checksum(Arrays.copyOf(bytes, bytes.length - Integer.BYTES))) {
                    throw new StorageException("Checkpoint manifest checksum failed");
                }
                return new CheckpointManifest(file, sequence, snapshotChecksum);
            }
        } catch (IOException exception) {
            throw new StorageException("Cannot read checkpoint manifest", exception);
        }
    }

    private static void moveReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path safeChild(Path parent, String name) {
        if (name == null || name.isBlank() || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.contains("..")) {
            throw new StorageException("Invalid backup file name");
        }
        Path normalizedParent = parent.toAbsolutePath().normalize();
        Path child = normalizedParent.resolve(name).normalize();
        if (!child.getParent().equals(normalizedParent)) throw new StorageException("Backup path escapes its directory");
        return child;
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                for (int read; (read = input.read(buffer)) >= 0;) digest.update(buffer, 0, read);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void writeBackupManifest(Path path, BackupManifest backup) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(BACKUP_MAGIC);
        lines.add("created=" + backup.createdAt().toEpochMilli());
        lines.add("latest=" + backup.latestSequence());
        lines.add("checkpoint=" + backup.checkpointSequence());
        for (BackupFile file : backup.files()) lines.add("file=" + file.name() + ":" + file.sha256());
        Files.write(path, lines, java.nio.charset.StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) { channel.force(true); }
    }

    private static BackupManifest readBackupManifest(Path path) {
        try {
            List<String> lines = Files.readAllLines(path, java.nio.charset.StandardCharsets.UTF_8);
            if (lines.size() < 5 || !BACKUP_MAGIC.equals(lines.getFirst())) throw new StorageException("Unsupported backup manifest");
            long created = readManifestLong(lines.get(1), "created=");
            long latest = readManifestLong(lines.get(2), "latest=");
            long checkpoint = readManifestLong(lines.get(3), "checkpoint=");
            List<BackupFile> files = new ArrayList<>();
            for (String line : lines.subList(4, lines.size())) {
                if (!line.startsWith("file=")) throw new StorageException("Invalid backup manifest record");
                String[] parts = line.substring(5).split(":", -1);
                if (parts.length != 2 || !parts[1].matches("[0-9a-f]{64}")) throw new StorageException("Invalid backup checksum entry");
                safeChild(path.getParent(), parts[0]);
                files.add(new BackupFile(parts[0], parts[1]));
            }
            if (files.stream().noneMatch(file -> JOURNAL_NAME.equals(file.name()))) throw new StorageException("Backup manifest lacks journal");
            return new BackupManifest(Instant.ofEpochMilli(created), latest, checkpoint, files);
        } catch (IOException | NumberFormatException exception) {
            throw new StorageException("Cannot read backup manifest", exception);
        }
    }

    private static long readManifestLong(String line, String prefix) {
        if (!line.startsWith(prefix)) throw new StorageException("Invalid backup manifest field");
        long value = Long.parseLong(line.substring(prefix.length()));
        if (value < 0) throw new StorageException("Negative backup manifest sequence");
        return value;
    }

    private static void verifyBackupOrThrow(Path directory) {
        BackupVerification verification = verifyBackup(directory);
        if (!verification.valid()) throw new StorageException("Backup verification failed: " + verification.detail());
    }

    private static void deleteTree(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException exception) { throw new StorageException("Cannot remove incomplete backup", exception); }
            });
        }
    }

    private static int checksum(byte[] bytes) {
        CRC32 crc = new CRC32();
        crc.update(bytes);
        return (int) crc.getValue();
    }

    @Override
    public void close() {
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            writer.shutdown();
        }
        try {
            if (!writer.awaitTermination(10, TimeUnit.SECONDS)) {
                writer.shutdownNow();
                throw new StorageException("Journal writer did not stop cleanly");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
            throw new StorageException("Interrupted while stopping journal writer", exception);
        }
    }

    private record Replay(List<JournalEntry> entries, long latestSequence, boolean truncatedTail) {}

    private record CheckpointManifest(String snapshotFile, long sequence, int snapshotChecksum) {}

    private record BackupManifest(Instant createdAt, long latestSequence, long checkpointSequence, List<BackupFile> files) {
        private BackupManifest {
            if (latestSequence < 0 || checkpointSequence < 0 || checkpointSequence > latestSequence) throw new StorageException("Invalid backup manifest sequences");
            files = List.copyOf(files);
        }
    }

    private record BackupFile(String name, String sha256) {}
}
