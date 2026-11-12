package ai.careerpilot.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "career_profiles")
public class CareerProfileEntity {
    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "display_name", length = 160)
    private String displayName;

    @Column(name = "professional_summary")
    private String professionalSummary;

    @Column(name = "experience_years", precision = 4, scale = 1)
    private BigDecimal experienceYears;

    @Column(name = "preferences_schema_version", nullable = false)
    private int preferencesSchemaVersion = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferences_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode preferencesJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CareerProfileEntity() {}

    public CareerProfileEntity(UUID userId, String displayName, String professionalSummary,
                               BigDecimal experienceYears, JsonNode preferencesJson) {
        this.userId = userId;
        this.displayName = displayName;
        this.professionalSummary = professionalSummary;
        this.experienceYears = experienceYears;
        this.preferencesJson = preferencesJson;
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
    public String getDisplayName() { return displayName; }
    public String getProfessionalSummary() { return professionalSummary; }
    public BigDecimal getExperienceYears() { return experienceYears; }
    public int getPreferencesSchemaVersion() { return preferencesSchemaVersion; }
    public JsonNode getPreferencesJson() { return preferencesJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
