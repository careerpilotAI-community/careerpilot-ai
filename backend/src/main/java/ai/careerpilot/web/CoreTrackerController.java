package ai.careerpilot.web;
import ai.careerpilot.service.CoreTrackerService;
import ai.careerpilot.service.CoreTrackerService.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
@RestController @RequestMapping("/api")
public class CoreTrackerController {
 private final CoreTrackerService tracker;
 public CoreTrackerController(CoreTrackerService tracker){this.tracker=tracker;}
 @GetMapping("/profile") public ProfileResponse profile(Principal p){return tracker.getProfile(p.getName());}
 @PutMapping("/profile") public ProfileResponse saveProfile(Principal p,@Valid @RequestBody ProfileRequest r){return tracker.saveProfile(p.getName(),r);}
 @GetMapping("/profile/skills") public List<SkillResponse> skills(Principal p){return tracker.listSkills(p.getName());}
 @PostMapping("/profile/skills") @ResponseStatus(HttpStatus.CREATED) public SkillResponse addSkill(Principal p,@Valid @RequestBody SkillRequest r){return tracker.addSkill(p.getName(),r);}
 @PutMapping("/profile/skills/{id}") public SkillResponse updateSkill(Principal p,@PathVariable UUID id,@Valid @RequestBody SkillRequest r){return tracker.updateSkill(p.getName(),id,r);}
 @DeleteMapping("/profile/skills/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteSkill(Principal p,@PathVariable UUID id){tracker.deleteSkill(p.getName(),id);}
 @GetMapping("/jobs") public List<JobResponse> jobs(Principal p){return tracker.listJobs(p.getName());}
 @PostMapping("/jobs") @ResponseStatus(HttpStatus.CREATED) public JobResponse createJob(Principal p,@Valid @RequestBody JobRequest r){return tracker.createJob(p.getName(),r);}
 @GetMapping("/jobs/{id}") public JobResponse job(Principal p,@PathVariable UUID id){return tracker.getJob(p.getName(),id);}
 @PutMapping("/jobs/{id}") public JobResponse updateJob(Principal p,@PathVariable UUID id,@Valid @RequestBody JobRequest r){return tracker.updateJob(p.getName(),id,r);}
 @DeleteMapping("/jobs/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteJob(Principal p,@PathVariable UUID id){tracker.deleteJob(p.getName(),id);}
 @GetMapping("/applications") public List<ApplicationResponse> applications(Principal p){return tracker.listApplications(p.getName());}
 @PostMapping("/applications") @ResponseStatus(HttpStatus.CREATED) public ApplicationResponse createApplication(Principal p,@Valid @RequestBody ApplicationRequest r){return tracker.createApplication(p.getName(),r);}
 @GetMapping("/applications/{id}") public ApplicationResponse application(Principal p,@PathVariable UUID id){return tracker.getApplication(p.getName(),id);}
 @PutMapping("/applications/{id}") public ApplicationResponse updateApplication(Principal p,@PathVariable UUID id,@Valid @RequestBody ApplicationRequest r){return tracker.updateApplication(p.getName(),id,r);}
 @GetMapping("/dashboard/summary") public DashboardSummary dashboard(Principal p){return tracker.dashboard(p.getName());}
}