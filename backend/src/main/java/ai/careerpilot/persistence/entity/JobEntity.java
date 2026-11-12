package ai.careerpilot.persistence.entity;
import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
@Entity @Table(name="jobs")
public class JobEntity {
 @Id @UuidGenerator private UUID id;
 @Column(name="user_id",nullable=false) private UUID userId;
 @Column(nullable=false,length=240) private String title;
 @Column(name="company_name",nullable=false,length=240) private String companyName;
 @Column(name="company_website",length=2048) private String companyWebsite;
 @Column(length=240) private String location;
 @Column(name="employment_type",length=64) private String employmentType;
 @Column(name="work_mode",length=64) private String workMode;
 @Column private String description;
 @Column(name="source_url",length=2048) private String sourceUrl;
 @Column(name="source_name",length=160) private String sourceName;
 @Column(name="observed_at") private Instant observedAt;
 @Column(name="posted_at") private LocalDate postedAt;
 @Column(name="closing_at") private LocalDate closingAt;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 protected JobEntity(){}
 public JobEntity(UUID userId,String title,String companyName){this.userId=userId;this.title=title;this.companyName=companyName;}
 public void update(String title,String companyName,String companyWebsite,String location,String employmentType,String workMode,String description,String sourceUrl,String sourceName,LocalDate postedAt,LocalDate closingAt){this.title=title;this.companyName=companyName;this.companyWebsite=companyWebsite;this.location=location;this.employmentType=employmentType;this.workMode=workMode;this.description=description;this.sourceUrl=sourceUrl;this.sourceName=sourceName;this.postedAt=postedAt;this.closingAt=closingAt;}
 @PrePersist void onCreate(){Instant n=Instant.now();createdAt=n;updatedAt=n;} @PreUpdate void onUpdate(){updatedAt=Instant.now();}
 public UUID getId(){return id;} public UUID getUserId(){return userId;} public String getTitle(){return title;} public String getCompanyName(){return companyName;}
 public String getCompanyWebsite(){return companyWebsite;} public String getLocation(){return location;} public String getEmploymentType(){return employmentType;}
 public String getWorkMode(){return workMode;} public String getDescription(){return description;} public String getSourceUrl(){return sourceUrl;}
 public String getSourceName(){return sourceName;} public Instant getObservedAt(){return observedAt;} public LocalDate getPostedAt(){return postedAt;}
 public LocalDate getClosingAt(){return closingAt;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}