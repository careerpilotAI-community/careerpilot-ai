package ai.careerpilot.document;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

public interface DocumentStorage {
    String storeQuarantined(InputStream input, String key) throws IOException;
    void promote(String key) throws IOException;
    InputStream open(String key) throws IOException;
    void remove(String key) throws IOException;
    Path resolveForInternalUse(String key);
}
