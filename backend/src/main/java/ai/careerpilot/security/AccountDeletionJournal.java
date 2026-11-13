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

/**
 * Sidecar deletion journal kept outside PostgreSQL backups and document storage.
 * Entries contain opaque IDs only and are replayed before a restored instance is exposed.
 */
@Component
public class AccountDeletionJournal {
    private static final int SCHEMA_VERSION = 1;
    private static final long RETENTION_DAYS = 29;
    private final Path journalFile;
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
        this.mapper = mapper;
    }

    public synchronized void record(UUID operationId, UUID ownerId) throws IOException {
        if (operationId == null || ownerId == null) throw new IllegalArgumentException("Deletion journal IDs are required.");
        Files.createDirectories(journalFile.getParent());
        try (FileChannel channel = FileChannel.open(journalFile,
                StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            channel.position(0);
            byte[] existing = new byte[(int) channel.size()];
            ByteBuffer read = ByteBuffer.wrap(existing);
            while (read.hasRemaining() && channel.read(read) >= 0) { /* read complete journal */ }
            String content = new String(existing, StandardCharsets.UTF_8);
            for (String line : content.split("\\R")) {
                if (line.isBlank()) continue;
                JournalEntry entry = mapper.readValue(line, JournalEntry.class);
                if (operationId.toString().equals(entry.operationId())) return;
            }
            JournalEntry entry = new JournalEntry(SCHEMA_VERSION, operationId.toString(),
                    ownerId.toString(), Instant.now().toString());
            byte[] bytes = (mapper.writeValueAsString(entry) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
            channel.position(channel.size());
            ByteBuffer write = ByteBuffer.wrap(bytes);
            while (write.hasRemaining()) channel.write(write);
            channel.force(true);
        }
    }

    public synchronized int pruneExpired() throws IOException {
        if (!Files.exists(journalFile, LinkOption.NOFOLLOW_LINKS)) return 0;
        Instant cutoff = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
        List<JournalEntry> retained = new ArrayList<>();
        int removed = 0;
        try (FileChannel channel = FileChannel.open(journalFile,
                StandardOpenOption.READ, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            channel.position(0);
            byte[] existing = new byte[(int) channel.size()];
            ByteBuffer read = ByteBuffer.wrap(existing);
            while (read.hasRemaining() && channel.read(read) >= 0) { /* read complete journal */ }
            for (String line : new String(existing, StandardCharsets.UTF_8).split("\\R")) {
                if (line.isBlank()) continue;
                JournalEntry entry = mapper.readValue(line, JournalEntry.class);
                if (Instant.parse(entry.recordedAt()).isAfter(cutoff)) retained.add(entry);
                else removed++;
            }
            Path temp = Files.createTempFile(journalFile.getParent(), "deletion-journal-", ".tmp");
            try {
                StringBuilder output = new StringBuilder();
                for (JournalEntry entry : retained) output.append(mapper.writeValueAsString(entry)).append(System.lineSeparator());
                Files.writeString(temp, output, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
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

    public record JournalEntry(int schemaVersion, String operationId, String ownerId, String recordedAt) {}
}
