package ai.careerpilot.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AccountDeletionJournalTest {
    @TempDir
    Path tempDir;

    @Test
    void recordsAccountAndAssetTombstonesOnceWithoutPersonalContent() throws Exception {
        Path journalFile = tempDir.resolve("journal").resolve("account-deletions.jsonl");
        AccountDeletionJournal journal = new AccountDeletionJournal(journalFile.toString(), new ObjectMapper());
        UUID accountOperation = UUID.randomUUID();
        UUID assetOperation = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        journal.record(accountOperation, ownerId);
        journal.record(accountOperation, ownerId);
        journal.recordAssetDeletion(assetOperation, ownerId, assetId);
        journal.recordAssetDeletion(assetOperation, ownerId, assetId);

        String content = Files.readString(journalFile);
        assertThat(content.lines()).hasSize(2);
        assertThat(content).contains(accountOperation.toString(), ownerId.toString(), assetId.toString());
        assertThat(content).contains("\"deletionType\":\"ACCOUNT\"", "\"deletionType\":\"ASSET\"");
        assertThat(content).doesNotContain("@", "password", "resume", "filename", "storageKey");
    }

    @Test
    void prunesJournalEntriesOlderThanTwentyNineDays() throws Exception {
        Path journalFile = tempDir.resolve("journal").resolve("account-deletions.jsonl");
        AccountDeletionJournal journal = new AccountDeletionJournal(journalFile.toString(), new ObjectMapper());
        UUID operationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Files.createDirectories(journalFile.getParent());
        Files.writeString(journalFile,
                "{\"schemaVersion\":1,\"operationId\":\"" + operationId
                        + "\",\"ownerId\":\"" + ownerId
                        + "\",\"deletionType\":\"ACCOUNT\",\"assetId\":null,"
                        + "\"recordedAt\":\"2000-01-01T00:00:00Z\"}\n");

        assertThat(journal.pruneExpired()).isEqualTo(1);
        assertThat(Files.readString(journalFile)).isEmpty();
    }
}
