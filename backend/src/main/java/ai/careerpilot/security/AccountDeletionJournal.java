package ai.careerpilot.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Sidecar deletion journal kept outside PostgreSQL backups and document storage. */
@Component
public class AccountDeletionJournal {
    private static final int SCHEMA_VERSION = 1;
    private static final long RETENTION_DAYS = 29;
    private final Path journalFile;
    private final Path lockFile;
    private final ObjectMapper mapper;

    public AccountDeletionJournal(
            @Value("${app.account-deletion.journal-file:${user.home}/.careerpilot/deletion-journal/account-deletions.jsonl}") String configuredFile,
            ObjectMapper mapper) throws IOException {
        Path configured = Path.of(configuredFile);
        if (!configured.isAbsolute()) throw new IllegalArgumentException("Deletion journal path must be absolute.");
        Path parent = configured.getParent();
        if (parent == null) throw new IllegalArgumentException("Deletion journal requires a parent directory.");
        Files.createDirectories(parent);
        if (Files.isSymbolicLink(parent)) throw new IOException("Deletion journal directory must not be a symbolic link.");
        try { Files.setPosixFilePermissions(parent, PosixFilePermissions.fromString("rwx------")); }
        catch (UnsupportedOperationException ignored) { /* Windows ACLs must restrict this directory to the operator/backend. */ }
        if (Files.exists(configured, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(configured)) {
            throw new IOException("Deletion journal must not be a symbolic link.");
        }
        this.journalFile = configured.toAbsolutePath().normalize();
        this.lockFile = parent.resolve(configured.getFileName().toString() + ".lock");
        this.mapper = mapper;
    }

    public synchronized void record(UUID operationId, UUID ownerId) throws IOException {
        recordEntry(new JournalEntry(SCHEMA_VERSION, operationId.toString(), ownerId.toString(),
                "ACCOUNT", null, Instant.now().toString()));
    }

    public synchronized void recordAssetDeletion(UUID operationId, UUID ownerId, UUID assetId) throws IOException {
        if (assetId == null) throw new IllegalArgumentException("Asset ID is required.");
        recordEntry(new JournalEntry(SCHEMA_VERSION, operationId.toString(), ownerId.toString(),
                "ASSET", assetId.toString(), Instant.now().toString()));
    }

    private void recordEntry(JournalEntry entry) throws IOException {
        UUID.fromString(entry.operationId());
        UUID.fromString(entry.ownerId());
        if (entry.assetId() != null) UUID.fromString(entry.assetId());
        try (FileChannel lockChannel = FileChannel.open(lockFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = lockChannel.lock()) {
            if (Files.exists(journalFile, LinkOption.NOFOLLOW_LINKS)) {
                for (String line : Files.readAllLines(journalFile, StandardCharsets.UTF_8)) {
                    if (line.isBlank()) continue;
                    JournalEntry existing = mapper.readValue(line, JournalEntry.class);
                    validate(existing);
                    if (entry.operationId().equals(existing.operationId())) {
                        if (!entry.ownerId().equals(existing.ownerId())
                                || !entry.deletionType().equals(existing.deletionType())
                                || !java.util.Objects.equals(entry.assetId(), existing.assetId())) {
                            throw new IOException("Deletion journal operation ID is already bound to another deletion.");
                        }
                        return;
                    }
                }
            }
            byte[] bytes = (mapper.writeValueAsString(entry) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(journalFile,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                ByteBuffer write = ByteBuffer.wrap(bytes);
                while (write.hasRemaining()) channel.write(write);
                channel.force(true);
            }
        }
    }

    public synchronized int pruneExpired() throws IOException {
        if (!Files.exists(journalFile, LinkOption.NOFOLLOW_LINKS)) return 0;
        Instant cutoff = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
        List<JournalEntry> retained = new ArrayList<>();
        int removed = 0;
        try (FileChannel lockChannel = FileChannel.open(lockFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = lockChannel.lock()) {
            for (String line : Files.readAllLines(journalFile, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                JournalEntry entry = mapper.readValue(line, JournalEntry.class);
                validate(entry);
                if (Instant.parse(entry.recordedAt()).isAfter(cutoff)) retained.add(entry);
                else removed++;
            }
            Path temp = Files.createTempFile(journalFile.getParent(), "deletion-journal-", ".tmp");
            try {
                StringBuilder output = new StringBuilder();
                for (JournalEntry entry : retained) output.append(mapper.writeValueAsString(entry)).append(System.lineSeparator());
                Files.writeString(temp, output, StandardCharsets.UTF_8,
                        StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                try { Files.setPosixFilePermissions(temp, PosixFilePermissions.fromString("rw-------")); }
                catch (UnsupportedOperationException ignoredPermission) { /* Windows ACLs inherit from the private directory. */ }
                try (FileChannel outputChannel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                    outputChannel.force(true);
                }
                Files.move(temp, journalFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp);
            }
        }
        return removed;
    }

    private static void validate(JournalEntry entry) throws IOException {
        if (entry.schemaVersion() != SCHEMA_VERSION) throw new IOException("Unsupported deletion journal schema version.");
        UUID.fromString(entry.operationId());
        UUID.fromString(entry.ownerId());
        if (!"ACCOUNT".equals(entry.deletionType()) && !"ASSET".equals(entry.deletionType())) {
            throw new IOException("Unsupported deletion journal event type.");
        }
        if ("ACCOUNT".equals(entry.deletionType()) && entry.assetId() != null) {
            throw new IOException("Account tombstone must not contain an asset ID.");
        }
        if ("ASSET".equals(entry.deletionType()) && entry.assetId() == null) {
            throw new IOException("Asset tombstone requires an asset ID.");
        }
        if (entry.assetId() != null) UUID.fromString(entry.assetId());
        Instant.parse(entry.recordedAt());
    }

    public record JournalEntry(int schemaVersion, String operationId, String ownerId,
            String deletionType, String assetId, String recordedAt) {}
}
