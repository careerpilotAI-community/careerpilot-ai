package ai.careerpilot.service;

import ai.careerpilot.persistence.entity.*;
import ai.careerpilot.persistence.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class CoreTrackerService {
 private static final Set<String> STATUSES=Set.of("SAVED","APPLIED","SCREENING","INTERVIEW","OFFER","REJECTED","WITHDRAWN");
 private final UserRepository users; private final CareerProfileRepository profiles; private final ProfileSkillRepository skills;
 private final JobRepository jobs; private final ApplicationRepository applications; private final ObjectMapper mapper;
 public CoreTrackerService(UserRepository users,CareerProfileRepository profiles,ProfileSkillRepository skills,JobRepository jobs,ApplicationRepository applications,ObjectMapper mapper){
  this.users=users;this.profiles=profiles;this.skills=skills;this.jobs=jobs;this.applications=applications;this.mapper=mapper;
 }
 private UUID owner(String email){return users.findByEmail(email.trim().toLowerCase(Locale.ROOT)).map(UserEntity::getId).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED));}
 private static String clean(String value){return value==null?null:value.trim();}
 private static String status(String value){String s=value==null?"SAVED":value.trim().toUpperCase(Locale.ROOT);if(!STATUSES.contains(s))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported application status.");return s;}
 private JobEntity ownedJob(UUID id,UUID user){return jobs.findByIdAndUserId(id,user).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}
 private ApplicationEntity ownedApplication(UUID id,UUID user){return applications.findByIdAndUserId(id,user).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}

 public ProfileResponse getProfile(String email){UUID u=owner(email);CareerProfileEntity p=profiles.findByUserId(u).orElse(null);return p==null?new ProfileResponse(null,null,null,null,mapper.createObjectNode()):profileResponse(p);}
 public ProfileResponse saveProfile(String email,ProfileRequest r){UUID u=owner(email);JsonNode prefs=r.preferences()==null?mapper.createObjectNode():r.preferences();CareerProfileEntity p=profiles.findByUserId(u).orElseGet(()->new CareerProfileEntity(u,clean(r.displayName()),clean(r.professionalSummary()),r.experienceYears(),prefs));p.update(clean(r.displayName()),clean(r.professionalSummary()),r.experienceYears(),prefs);return profileResponse(profiles.save(p));}
 private ProfileResponse profileResponse(CareerProfileEntity p){return new ProfileResponse(p.getId(),p.getDisplayName(),p.getProfessionalSummary(),p.getExperienceYears(),p.getPreferencesJson());}

 public List<SkillResponse> listSkills(String email){return skills.findAllByUserIdOrderByNormalizedSkillNameAsc(owner(email)).stream().map(this::skillResponse).toList();}
 public SkillResponse addSkill(String email,SkillRequest r){UUID u=owner(email);String n=clean(r.skillName());if(skills.existsByUserIdAndNormalizedSkillName(u,n.toLowerCase(Locale.ROOT)))throw new ResponseStatusException(HttpStatus.CONFLICT,"This skill is already in your profile.");return skillResponse(skills.save(new ProfileSkillEntity(u,n,clean(r.proficiency()),r.yearsExperience())));}
 public SkillResponse updateSkill(String email,UUID id,SkillRequest r){UUID u=owner(email);ProfileSkillEntity s=skills.findByIdAndUserId(id,u).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));String n=clean(r.skillName()).toLowerCase(Locale.ROOT);if(!s.getNormalizedSkillName().equals(n)&&skills.existsByUserIdAndNormalizedSkillName(u,n))throw new ResponseStatusException(HttpStatus.CONFLICT,"This skill is already in your profile.");s.update(clean(r.skillName()),clean(r.proficiency()),r.yearsExperience());return skillResponse(s);}
 public void deleteSkill(String email,UUID id){if(skills.deleteByIdAndUserId(id,owner(email))==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND);}
 private SkillResponse skillResponse(ProfileSkillEntity s){return new SkillResponse(s.getId(),s.getSkillName(),s.getProficiency(),s.getYearsExperience());}

 public List<JobResponse> listJobs(String email){return jobs.findAllByUserIdOrderByCreatedAtDesc(owner(email)).stream().map(this::jobResponse).toList();}
 public JobResponse getJob(String email,UUID id){return jobResponse(ownedJob(id,owner(email)));}
 public JobResponse createJob(String email,JobRequest r){UUID u=owner(email);JobEntity j=new JobEntity(u,clean(r.title()),clean(r.companyName()));applyJob(j,r);return jobResponse(jobs.save(j));}
 public JobResponse updateJob(String email,UUID id,JobRequest r){JobEntity j=ownedJob(id,owner(email));applyJob(j,r);return jobResponse(j);}
 private void applyJob(JobEntity j,JobRequest r){j.update(clean(r.title()),clean(r.companyName()),clean(r.companyWebsite()),clean(r.location()),clean(r.employmentType()),clean(r.workMode()),clean(r.description()),clean(r.sourceUrl()),clean(r.sourceName()),r.postedAt(),r.closingAt());}
 public void deleteJob(String email,UUID id){UUID u=owner(email);if(applications.findByUserIdAndJobId(u,id).isPresent())throw new ResponseStatusException(HttpStatus.CONFLICT,"Remove the application record before deleting this job.");if(jobs.deleteByIdAndUserId(id,u)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND);}
 private JobResponse jobResponse(JobEntity j){return new JobResponse(j.getId(),j.getTitle(),j.getCompanyName(),j.getCompanyWebsite(),j.getLocation(),j.getEmploymentType(),j.getWorkMode(),j.getDescription(),j.getSourceUrl(),j.getSourceName(),j.getPostedAt(),j.getClosingAt(),j.getCreatedAt(),j.getUpdatedAt());}

 public List<ApplicationResponse> listApplications(String email){UUID u=owner(email);return applications.findAllByUserIdOrderByUpdatedAtDesc(u).stream().map(a->applicationResponse(a,u)).toList();}
 public ApplicationResponse getApplication(String email,UUID id){UUID u=owner(email);return applicationResponse(ownedApplication(id,u),u);}
 public ApplicationResponse createApplication(String email,ApplicationRequest r){UUID u=owner(email);ownedJob(r.jobId(),u);if(applications.findByUserIdAndJobId(u,r.jobId()).isPresent())throw new ResponseStatusException(HttpStatus.CONFLICT,"An application already exists for this job.");ApplicationEntity a=new ApplicationEntity(u,r.jobId());applyApplication(a,r);return applicationResponse(applications.save(a),u);}
 public ApplicationResponse updateApplication(String email,UUID id,ApplicationRequest r){UUID u=owner(email);ApplicationEntity a=ownedApplication(id,u);if(!a.getJobId().equals(r.jobId()))ownedJob(r.jobId(),u);Optional<ApplicationEntity> duplicate=applications.findByUserIdAndJobId(u,r.jobId());if(duplicate.isPresent()&&!duplicate.get().getId().equals(id))throw new ResponseStatusException(HttpStatus.CONFLICT,"An application already exists for this job.");applyApplication(a,r);return applicationResponse(a,u);}
 private void applyApplication(ApplicationEntity a,ApplicationRequest r){a.update(status(r.status()),r.appliedDate(),clean(r.notes()),r.nextActionDate(),r.interviewAt(),clean(r.interviewTimezone()));}
 private ApplicationResponse applicationResponse(ApplicationEntity a,UUID u){JobEntity j=ownedJob(a.getJobId(),u);return new ApplicationResponse(a.getId(),a.getJobId(),j.getTitle(),j.getCompanyName(),a.getStatus(),a.getAppliedDate(),a.getNotes(),a.getNextActionDate(),a.getInterviewAt(),a.getInterviewTimezone(),a.getCreatedAt(),a.getUpdatedAt());}

 public DashboardSummary dashboard(String email){UUID u=owner(email);List<ApplicationEntity> all=applications.findAllByUserIdOrderByUpdatedAtDesc(u);Map<String,Long> counts=all.stream().collect(Collectors.groupingBy(ApplicationEntity::getStatus,Collectors.counting()));List<UpcomingInterview> upcoming=all.stream().filter(a->a.getInterviewAt()!=null&&!a.getInterviewAt().isBefore(Instant.now())).sorted(Comparator.comparing(ApplicationEntity::getInterviewAt)).limit(5).map(a->{JobEntity j=ownedJob(a.getJobId(),u);return new UpcomingInterview(a.getId(),j.getTitle(),j.getCompanyName(),a.getInterviewAt(),a.getInterviewTimezone());}).toList();return new DashboardSummary(jobs.countByUserId(u),all.size(),counts,upcoming);}

 public record ProfileRequest(@Size(max=160) String displayName,@Size(max=10000) String professionalSummary,@DecimalMin("0.0") @DecimalMax("80.0") BigDecimal experienceYears,JsonNode preferences){}
 public record ProfileResponse(UUID id,String displayName,String professionalSummary,BigDecimal experienceYears,JsonNode preferences){}
 public record SkillRequest(@NotBlank @Size(max=120) String skillName,@Size(max=32) String proficiency,@DecimalMin("0.0") @DecimalMax("80.0") BigDecimal yearsExperience){}
 public record SkillResponse(UUID id,String skillName,String proficiency,BigDecimal yearsExperience){}
 public record JobRequest(@NotBlank @Size(max=240) String title,@NotBlank @Size(max=240) String companyName,@Size(max=2048) String companyWebsite,@Size(max=240) String location,@Size(max=64) String employmentType,@Size(max=64) String workMode,@Size(max=20000) String description,@Size(max=2048) String sourceUrl,@Size(max=160) String sourceName,LocalDate postedAt,LocalDate closingAt){}
 public record JobResponse(UUID id,String title,String companyName,String companyWebsite,String location,String employmentType,String workMode,String description,String sourceUrl,String sourceName,LocalDate postedAt,LocalDate closingAt,Instant createdAt,Instant updatedAt){}
 public record ApplicationRequest(@NotNull UUID jobId,String status,LocalDate appliedDate,@Size(max=10000) String notes,LocalDate nextActionDate,Instant interviewAt,@Size(max=64) String interviewTimezone){}
 public record ApplicationResponse(UUID id,UUID jobId,String jobTitle,String companyName,String status,LocalDate appliedDate,String notes,LocalDate nextActionDate,Instant interviewAt,String interviewTimezone,Instant createdAt,Instant updatedAt){}
 public record UpcomingInterview(UUID applicationId,String jobTitle,String companyName,Instant interviewAt,String timezone){}
 public record DashboardSummary(long totalJobs,long totalApplications,Map<String,Long> applicationsByStatus,List<UpcomingInterview> upcomingInterviews){}
}