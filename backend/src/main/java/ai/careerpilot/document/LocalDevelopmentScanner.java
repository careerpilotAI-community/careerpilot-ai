package ai.careerpilot.document;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
@Profile("local")
public class LocalDevelopmentScanner implements MalwareScanner {
    @Override public ScanResult scan(InputStream input) throws Exception {
        byte[] sample=input.readNBytes(4096);
        String marker=new String(sample,StandardCharsets.ISO_8859_1);
        if (!marker.contains("CAREERPILOT-SYNTHETIC-TEST-FIXTURE"))
            throw new IllegalStateException("Scanner bypass accepts synthetic test fixtures only.");
        return new ScanResult(true,"DEV_BYPASS");
    }
}
