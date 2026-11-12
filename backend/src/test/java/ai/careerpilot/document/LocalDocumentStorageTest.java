package ai.careerpilot.document;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class LocalDocumentStorageTest {
    @TempDir Path temp;
    @Test void storesOnlyUnderPrivateGeneratedKeyAndPromotesBeforeRead() throws Exception {
        LocalDocumentStorage storage=new LocalDocumentStorage(temp.resolve("private").toString());
        String key=UUID.randomUUID().toString();
        storage.storeQuarantined(new ByteArrayInputStream("private".getBytes()),key);
        assertThat(storage.resolveForInternalUse(key)).doesNotExist();
        storage.promote(key);
        assertThat(new String(storage.open(key).readAllBytes())).isEqualTo("private");
        assertThat(storage.resolveForInternalUse(key)).startsWith(temp.resolve("private"));
    }
    @Test void rejectsTraversalKeys() throws Exception {
        LocalDocumentStorage storage=new LocalDocumentStorage(temp.resolve("private").toString());
        assertThatThrownBy(()->storage.open("../../etc/passwd")).isInstanceOf(IllegalArgumentException.class);
    }
}
