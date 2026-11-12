package ai.careerpilot.document;

import ai.careerpilot.persistence.entity.*;
import ai.careerpilot.persistence.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

@Service
public class ResumeManagementService {
    private final UserRepository users; private final ResumeAssetRepository assets; private final DocumentStorage storage;
    private final MalwareScanner scanner; private final ResumeFileValidator validator; private final JdbcTemplate jdbc;
    private final long userQuota; private final long deploymentQuota;
    public ResumeManagementService(UserRepository users, ResumeAssetRepository assets, DocumentStorage storage,
            MalwareScanner scanner, ResumeFileValidator validator, JdbcTemplate jdbc,
            @Value("${app.documents.user-quota-bytes:104857600}") long userQuota,
            @Value("${app.documents.deployment-quota-bytes:2147483648}") long deploymentQuota) {
        this.users=users;this.assets=assets;this.storage=storage;this.scanner=scanner;this.validator=validator;this.jdbc=jdbc;
        this.userQuota=userQuota;this.deploymentQuota=deploymentQuota;
    }
    @Transactional(readOnly=true)
    public List<AssetResponse> list(String email) {
        UUID owner=owner(email);
        return assets.findAllByUserIdOrderByCreatedAtDesc(owner).stream().map(this::response).toList();
    }
    @Transactional
    public AssetResponse upload(String email, MultipartFile file, String kind) {
        UUID owner=owner(email);
        if (file==null || file.isEmpty() || file.getSize()>ResumeFileValidator.MAX_BYTES)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"File must be between 1 byte and 10 MiB.");
        if (!"ORIGINAL".equals(kind) && !"TAILORED".equals(kind))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Asset kind must be ORIGINAL or TAILORED.");
        String filename=safeFilename(file.getOriginalFilename());
        byte[] bytes;
        try { bytes=file.getBytes(); } catch(IOException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unable to read uploaded file."); }
        ResumeFileValidator.ValidatedFile valid;
        try { valid=validator.validate(filename,bytes); } catch(IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,ex.getMessage()); }
        if(bytes.length>ResumeFileValidator.MAX_BYTES) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"File exceeds 10 MiB.");
        // Serialize quota checks across concurrent uploads in this PostgreSQL deployment.
        jdbc.execute("SELECT pg_advisory_xact_lock(300030)");
        long current=assets.findAllByUserIdOrderByCreatedAtDesc(owner).stream().mapToLong(ResumeAssetEntity::getSizeBytes).sum();
        long deployment=assets.findAll().stream().mapToLong(ResumeAssetEntity::getSizeBytes).sum();
        if(current+bytes.length>userQuota || deployment+bytes.length>deploymentQuota)
            throw new ResponseStatusException(HttpStatus.INSUFFICIENT_STORAGE,"Document storage quota exceeded.");
        String key=UUID.randomUUID().toString(); String verdict;
        try {
            storage.storeQuarantined(new ByteArrayInputStream(bytes),key);
            MalwareScanner.ScanResult scan=scanner.scan(new ByteArrayInputStream(bytes));
            if(!scan.clean()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"The uploaded document did not pass malware scanning.");
            verdict=scan.result();
            storage.promote(key);
            String checksum=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            ResumeAssetEntity saved=assets.save(new ResumeAssetEntity(owner,kind,filename,valid.mediaType(),bytes.length,checksum,key,verdict));
            return response(saved);
        } catch(ResponseStatusException ex) {
            removeQuietly(key); throw ex;
        } catch(Exception ex) {
            removeQuietly(key);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Document validation or malware scanning is unavailable; upload was rejected.");
        }
    }
    @Transactional(readOnly=true)
    public DownloadedAsset download(String email, UUID id) {
        UUID owner=owner(email);
        ResumeAssetEntity asset=assets.findByIdAndUserId(id,owner).filter(a->"AVAILABLE".equals(a.getStatus()))
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        try { return new DownloadedAsset(asset,storage.open(asset.getStorageKey())); }
        catch(IOException ex) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
    }
    private UUID owner(String email) {
        return users.findByEmail(email.trim().toLowerCase(Locale.ROOT)).map(UserEntity::getId)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
    private String safeFilename(String input) {
        String name=input==null?"resume":input.replaceAll("[\\r\\n\\\\/\\u0000-\\u001f]","_").trim();
        if(name.length()>180) name=name.substring(name.length()-180);
        if(name.isBlank()) name="resume";
        return name;
    }
    private AssetResponse response(ResumeAssetEntity a) {
        return new AssetResponse(a.getId(),a.getAssetKind(),a.getOriginalFilename(),a.getMediaType(),
            a.getSizeBytes(),a.getChecksumSha256(),a.getStatus(),a.getCreatedAt());
    }
    private void removeQuietly(String key){try{storage.remove(key);}catch(IOException ignored){}}
    public record AssetResponse(UUID id,String assetKind,String filename,String mediaType,long sizeBytes,String checksumSha256,String status,java.time.Instant createdAt){}
    public record DownloadedAsset(ResumeAssetEntity asset,InputStream stream){}
}
