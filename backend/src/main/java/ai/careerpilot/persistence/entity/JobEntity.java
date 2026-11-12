package ai.careerpilot.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "jobs")
public class JobEntity {
    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 240)
    private String title;

    @Column(name = "company_name", nullable = false, length = 240)
    private String companyName;

    @Column(name = "company_website", length = 2048)
    private String companyWebsite;

    @Column(length = 240)
    private String location;

    @Column(name = "employment_type", length = 64)
    private String employmentType;

    @Column(name = "work_mode", length = 64)
    private String workMode;

    @Column
    private String description;

    @Column(name = "source_url", length = 2048)
    private String sourceUrl;

    @Column(name = "source_name", length = 160)
    private String sourceName;

    @Column(name = "observed_at")
    private Instant observedAt;

    @Column(name = "posted_at")
    private LocalDate postedAt;

    @Column(name = "closing_at")
    private LocalDate closingAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected JobEntity() {}

    public JobEntity(UUID userId, String title, String companyName) {
        this.userId = userId;
        this.title = title;
        this.companyName = companyName;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getTitle() { return title; }
    public String getCompanyName() { return companyName; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
