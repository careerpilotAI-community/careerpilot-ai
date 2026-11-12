package ai.careerpilot.persistence.entity;
import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
@Entity @Table(name="profile_skills")
public class ProfileSkillEntity {
 @Id @UuidGenerator private UUID id;
 @Column(name="user_id",nullable=false) private UUID userId;
 @Column(name="skill_name",nullable=false,length=120) private String skillName;
 @Column(name="normalized_skill_name",nullable=false,length=120) private String normalizedSkillName;
 @Column(length=32) private String proficiency;
 @Column(name="years_experience",precision=4,scale=1) private BigDecimal yearsExperience;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 protected ProfileSkillEntity(){}
 public ProfileSkillEntity(UUID userId,String skillName,String proficiency,BigDecimal yearsExperience){this.userId=userId;update(skillName,proficiency,yearsExperience);}
 public void update(String skillName,String proficiency,BigDecimal yearsExperience){this.skillName=skillName==null?null:skillName.trim();this.normalizedSkillName=skillName==null?null:skillName.trim().toLowerCase(Locale.ROOT);this.proficiency=proficiency;this.yearsExperience=yearsExperience;}
 @PrePersist void onCreate(){createdAt=Instant.now();}
 public UUID getId(){return id;} public UUID getUserId(){return userId;} public String getSkillName(){return skillName;}
 public String getNormalizedSkillName(){return normalizedSkillName;} public String getProficiency(){return proficiency;}
 public BigDecimal getYearsExperience(){return yearsExperience;} public Instant getCreatedAt(){return createdAt;}
}