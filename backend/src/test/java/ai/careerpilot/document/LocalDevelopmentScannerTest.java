package ai.careerpilot.document;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalDevelopmentScannerTest {

    private final LocalDevelopmentScanner scanner = new LocalDevelopmentScanner();

    @Test
    void acceptsSyntheticFixtureMarkerWithinTheInspectedPrefix() throws Exception {
        byte[] fixture = "CAREERPILOT-SYNTHETIC-TEST-FIXTURE".getBytes(StandardCharsets.UTF_8);

        MalwareScanner.ScanResult result = scanner.scan(new ByteArrayInputStream(fixture));

        assertThat(result.clean()).isTrue();
        assertThat(result.result()).isEqualTo("DEV_BYPASS");
    }

    @Test
    void rejectsOrdinaryDocumentBytes() {
        assertThatThrownBy(() -> scanner.scan(new ByteArrayInputStream(
                "ordinary resume content".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("synthetic test fixtures only");
    }

    @Test
    void rejectsMarkerBeyondTheInspectedPrefix() {
        byte[] prefix = new byte[4096];
        byte[] marker = "CAREERPILOT-SYNTHETIC-TEST-FIXTURE".getBytes(StandardCharsets.UTF_8);
        byte[] content = new byte[prefix.length + marker.length];
        System.arraycopy(marker, 0, content, prefix.length, marker.length);

        assertThatThrownBy(() -> scanner.scan(new ByteArrayInputStream(content)))
                .isInstanceOf(IllegalStateException.class);
    }
}
