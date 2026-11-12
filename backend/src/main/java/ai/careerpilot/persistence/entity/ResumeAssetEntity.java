package ai.careerpilot.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "resume_assets")
public class ResumeAssetEntity {
    @Id @UuidGenerator private UUID id;
    @Column(name = "user_id", nullable = false, updatable = false) private UUID userId;
    @Column(name = "asset_kind", nullable = false, updatable = false, length = 16) private String assetKind;
    @Column(name = "original_filename", nullable = false, updatable = false, length = 255) private String originalFilename;
    @Column(name = "media_type", nullable = false, updatable = false, length = 100) private String mediaType;
    @Column(name = "size_bytes", nullable = false, updatable = false) private long sizeBytes;
    @Column(name = "checksum_sha256", nullable = false, updatable = false, length = 64) private String checksumSha256;
    @Column(name = "storage_key", nullable = false, unique = true, updatable = false, length = 80) private String storageKey;
    @Column(nullable = false, updatable = false, length = 20) private String status;
    @Column(name = "scanner_result", nullable = false, updatable = false, length = 32) private String scannerResult;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    protected ResumeAssetEntity() {}
    public ResumeAssetEntity(UUID userId, String assetKind, String originalFilename, String mediaType,
                             long sizeBytes, String checksumSha256, String storageKey, String scannerResult) {
        this.userId=userId; this.assetKind=assetKind; this.originalFilename=originalFilename;
        this.mediaType=mediaType; this.sizeBytes=sizeBytes; this.checksumSha256=checksumSha256;
        this.storageKey=storageKey; this.scannerResult=scannerResult; this.status="AVAILABLE";
    }
    @PrePersist void created() { createdAt=Instant.now(); }
    public UUID getId(){return id;} public UUID getUserId(){return userId;}
    public String getAssetKind(){return assetKind;} public String getOriginalFilename(){return originalFilename;}
    public String getMediaType(){return mediaType;} public long getSizeBytes(){return sizeBytes;}
    public String getChecksumSha256(){return checksumSha256;} public String getStorageKey(){return storageKey;}
    public String getStatus(){return status;} public String getScannerResult(){return scannerResult;}
    public Instant getCreatedAt(){return createdAt;}
}
