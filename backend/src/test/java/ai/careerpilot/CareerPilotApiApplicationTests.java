package ai.careerpilot;

import ai.careerpilot.persistence.entity.JobEntity;
import ai.careerpilot.document.MalwareScanner;
import ai.careerpilot.document.DocumentStorage;
import ai.careerpilot.document.ResumeDeletionWorker;
import ai.careerpilot.document.ResumeUploadRetentionWorker;
import ai.careerpilot.persistence.entity.UserEntity;
import ai.careerpilot.persistence.repository.JobRepository;
import ai.careerpilot.persistence.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.registration.enabled=true", "app.documents.cleanup.scheduling-enabled=false", "app.documents.retention.scheduling-enabled=false"})
@AutoConfigureMockMvc
@Testcontainers
class CareerPilotApiApplicationTests {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6")
            .withDatabaseName("careerpilot_test")
            .withUsername("careerpilot_test")
            .withPassword("careerpilot_test_password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ResumeDeletionWorker resumeDeletionWorker;

    @Autowired
    private ResumeUploadRetentionWorker resumeUploadRetentionWorker;

    @Autowired
    private DocumentStorage documentStorage;

    @MockBean
    private MalwareScanner malwareScanner;

    @Test
    void contextLoadsAfterFreshFlywayMigration() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank DESC LIMIT 1",
                String.class)).isEqualTo("6");
    }


    @Test
    void registrationAndLoginCreateAnAuthenticatedServerSession() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"new.user@example.com","password":"a-very-long-test-password"}
                    """))
            .andExpect(status().isAccepted());

        var login = mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"NEW.USER@example.com","password":"a-very-long-test-password"}
                    """))
            .andExpect(status().isOk())
            .andReturn();

        var sessionCookie = login.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).isNotNull();

        mockMvc.perform(get("/api/auth/me").cookie(sessionCookie))
            .andExpect(status().isOk());
    }

    @Test
    void protectedEndpointRejectsAnonymousRequest() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized());
    }


    @Test
    void unsafeRequestWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
            .andExpect(status().isForbidden());
    }

    @Test
    void logoutInvalidatesTheAuthenticatedSession() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"logout.user@example.com","password":"a-very-long-test-password"}
                    """))
            .andExpect(status().isAccepted());

        var login = mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"logout.user@example.com","password":"a-very-long-test-password"}
                    """))
            .andExpect(status().isOk())
            .andReturn();
        var sessionCookie = login.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).isNotNull();

        mockMvc.perform(post("/api/auth/logout").cookie(sessionCookie).with(csrf()))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/auth/me").cookie(sessionCookie))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void repositoriesKeepJobQueriesOwnerScoped() {
        UserEntity owner = userRepository.saveAndFlush(
                new UserEntity("Owner@Example.com", "test-only-password-hash"));
        UserEntity other = userRepository.saveAndFlush(
                new UserEntity("Other@Example.com", "test-only-password-hash"));

        JobEntity ownedJob = jobRepository.saveAndFlush(
                new JobEntity(owner.getId(), "Backend Engineer", "Example Ltd"));

        assertThat(jobRepository.findByIdAndUserId(ownedJob.getId(), owner.getId())).isPresent();
        assertThat(jobRepository.findByIdAndUserId(ownedJob.getId(), other.getId())).isEmpty();
        assertThat(userRepository.findByEmail("owner@example.com")).isPresent();
    }

    @Test
    void databaseRejectsApplicationReferencingAnotherUsersJob() {
        UserEntity owner = userRepository.saveAndFlush(
                new UserEntity("first@example.com", "test-only-password-hash"));
        UserEntity other = userRepository.saveAndFlush(
                new UserEntity("second@example.com", "test-only-password-hash"));
        JobEntity job = jobRepository.saveAndFlush(
                new JobEntity(owner.getId(), "Java Engineer", "Example Ltd"));

        UUID applicationId = UUID.randomUUID();
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO applications (id, user_id, job_id, status)
                VALUES (?, ?, ?, 'SAVED')
                """, applicationId, other.getId(), job.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseEnforcesOneCurrentApplicationPerUserAndJob() {
        UserEntity owner = userRepository.saveAndFlush(
                new UserEntity("unique@example.com", "test-only-password-hash"));
        JobEntity job = jobRepository.saveAndFlush(
                new JobEntity(owner.getId(), "Platform Engineer", "Example Ltd"));

        jdbcTemplate.update("""
                INSERT INTO applications (id, user_id, job_id, status)
                VALUES (?, ?, ?, 'SAVED')
                """, UUID.randomUUID(), owner.getId(), job.getId());

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO applications (id, user_id, job_id, status)
                VALUES (?, ?, ?, 'APPLIED')
                """, UUID.randomUUID(), owner.getId(), job.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void trackerApisPersistRecordsAndEnforceAuthenticatedOwnership() throws Exception {
        UserEntity owner = userRepository.saveAndFlush(
                new UserEntity("tracker-owner-" + UUID.randomUUID() + "@example.com", "test-only-password-hash"));
        UserEntity other = userRepository.saveAndFlush(
                new UserEntity("tracker-other-" + UUID.randomUUID() + "@example.com", "test-only-password-hash"));

        mockMvc.perform(put("/api/profile")
                .with(user(owner.getEmail())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"displayName":"Career User","professionalSummary":"Java backend engineer","experienceYears":4.5}
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/profile/skills")
                .with(user(owner.getEmail())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"skillName":"Java","proficiency":"ADVANCED","yearsExperience":4.5}
                    """))
            .andExpect(status().isCreated());

        var jobResponse = mockMvc.perform(post("/api/jobs")
                .with(user(owner.getEmail())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"Senior Java Engineer","companyName":"Example Ltd","location":"Bengaluru"}
                    """))
            .andExpect(status().isCreated())
            .andReturn();
        UUID jobId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(
                jobResponse.getResponse().getContentAsString(), "$.id"));

        mockMvc.perform(post("/api/applications")
                .with(user(owner.getEmail())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"jobId":"%s","status":"APPLIED"}
                    """.formatted(jobId)))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/dashboard/summary").with(user(owner.getEmail())))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.totalJobs").value(1))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.totalApplications").value(1));

        mockMvc.perform(get("/api/jobs/{id}", jobId).with(user(other.getEmail())))
            .andExpect(status().isNotFound());
    }


    @Test
    void resumeUploadIsScannedAndDownloadsAreOwnerScoped() throws Exception {
        when(malwareScanner.scan(any(InputStream.class))).thenReturn(new MalwareScanner.ScanResult(true, "CLEAN"));
        UserEntity owner=userRepository.saveAndFlush(new UserEntity("resume-owner@example.com","test-only-password-hash"));
        UserEntity other=userRepository.saveAndFlush(new UserEntity("resume-other@example.com","test-only-password-hash"));
        byte[] pdf;
        try(PDDocument document=new PDDocument(); ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            document.addPage(new PDPage()); document.save(output); pdf=output.toByteArray();
        }
        MockMultipartFile file=new MockMultipartFile("file","original.pdf","application/pdf",pdf);
        var result=mockMvc.perform(multipart("/api/resumes").file(file).param("kind","ORIGINAL")
                .with(user(owner.getEmail())).with(csrf()))
            .andExpect(status().isCreated()).andReturn();
        UUID id=UUID.fromString(com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(),"$.id"));
        assertThat(jdbcTemplate.queryForObject(
            "SELECT job_status FROM resume_upload_cleanup_jobs WHERE reason_code='UPLOAD_ACCEPTED' ORDER BY created_at DESC LIMIT 1",
            String.class)).isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT storage_key FROM resume_upload_cleanup_jobs WHERE reason_code='UPLOAD_ACCEPTED' ORDER BY created_at DESC LIMIT 1",
            String.class)).isNull();
        mockMvc.perform(get("/api/resumes/{id}/download",id).with(user(owner.getEmail())))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Content-Type-Options","nosniff"));
        mockMvc.perform(get("/api/resumes/{id}/download",id).with(user(other.getEmail())))
            .andExpect(status().isNotFound());
    }

    @Test
    void resumeUploadFailsClosedWhenMalwareScannerIsUnavailable() throws Exception {
        when(malwareScanner.scan(any(InputStream.class))).thenThrow(new java.io.IOException("scanner unavailable"));
        UserEntity owner=userRepository.saveAndFlush(new UserEntity("resume-scan-failure@example.com","test-only-password-hash"));
        byte[] pdf;
        try(PDDocument document=new PDDocument(); ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            document.addPage(new PDPage()); document.save(output); pdf=output.toByteArray();
        }
        MockMultipartFile file=new MockMultipartFile("file","resume.pdf","application/pdf",pdf);
        mockMvc.perform(multipart("/api/resumes").file(file).param("kind","ORIGINAL")
                .with(user(owner.getEmail())).with(csrf()))
            .andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/api/resumes").with(user(owner.getEmail())))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$").isEmpty());
        var failedAttempt = jdbcTemplate.queryForMap("""
            SELECT job_status, reason_code, expires_at
            FROM resume_upload_cleanup_jobs
            WHERE reason_code='UPLOAD_FAILED'
            ORDER BY created_at DESC LIMIT 1
            """);
        assertThat(failedAttempt.get("job_status")).isEqualTo("PENDING");
        assertThat(failedAttempt.get("reason_code")).isEqualTo("UPLOAD_FAILED");
        assertThat(((java.sql.Timestamp) failedAttempt.get("expires_at")).toInstant())
            .isAfter(java.time.Instant.now().plus(java.time.Duration.ofHours(23)));
    }

    @Test
    void malwareRejectedUploadGetsTwentyFourHourCleanupDeadline() throws Exception {
        when(malwareScanner.scan(any(InputStream.class))).thenReturn(new MalwareScanner.ScanResult(false, "INFECTED"));
        UserEntity owner = userRepository.saveAndFlush(new UserEntity(
            "resume-malware-" + UUID.randomUUID() + "@example.com", "test-only-password-hash"));
        byte[] pdf;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            pdf = output.toByteArray();
        }
        mockMvc.perform(multipart("/api/resumes")
                .file(new MockMultipartFile("file", "rejected.pdf", "application/pdf", pdf))
                .param("kind", "ORIGINAL").with(user(owner.getEmail())).with(csrf()))
            .andExpect(status().isUnprocessableEntity());

        var rejectedAttempt = jdbcTemplate.queryForMap("""
            SELECT job_status, reason_code, expires_at
            FROM resume_upload_cleanup_jobs
            WHERE reason_code='UPLOAD_REJECTED'
            ORDER BY created_at DESC LIMIT 1
            """);
        assertThat(rejectedAttempt.get("job_status")).isEqualTo("PENDING");
        assertThat(((java.sql.Timestamp) rejectedAttempt.get("expires_at")).toInstant())
            .isAfter(java.time.Instant.now().plus(java.time.Duration.ofHours(23)));
    }

    @Test
    void abandonedUploadIsPurgedAfterRetentionDeadlineAndAbsenceIsVerified() throws Exception {
        String key = UUID.randomUUID().toString();
        documentStorage.storeQuarantined(new java.io.ByteArrayInputStream(new byte[] {1, 2, 3}), key);
        UUID operationId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO resume_upload_cleanup_jobs
                (operation_id, storage_key, job_status, reason_code, created_at, expires_at,
                 attempt_count, next_attempt_at)
            VALUES (?, ?, 'PENDING', 'UPLOAD_IN_PROGRESS',
                    CURRENT_TIMESTAMP - INTERVAL '25 hours',
                    CURRENT_TIMESTAMP - INTERVAL '1 hour', 0, CURRENT_TIMESTAMP - INTERVAL '1 hour')
            """, operationId, key);

        assertThat(documentStorage.exists(key)).isTrue();
        assertThat(resumeUploadRetentionWorker.processNextJob()).isTrue();
        assertThat(documentStorage.exists(key)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT job_status FROM resume_upload_cleanup_jobs WHERE operation_id=?",
            String.class, operationId)).isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT completed_at FROM resume_upload_cleanup_jobs WHERE operation_id=?",
            java.sql.Timestamp.class, operationId)).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT storage_key FROM resume_upload_cleanup_jobs WHERE operation_id=?",
            String.class, operationId)).isNull();
    }

    @Test
    void databaseRejectsNonNormalizedEmail() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO users (id, email, password_hash)
                VALUES (?, ?, ?)
                """, UUID.randomUUID(), " MixedCase@example.com ", "test-only-password-hash"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void resumeDeletionImmediatelyRevokesAccessAndQueuesDurableCleanup() throws Exception {
        when(malwareScanner.scan(any(InputStream.class))).thenReturn(new MalwareScanner.ScanResult(true, "CLEAN"));
        UserEntity owner=userRepository.saveAndFlush(new UserEntity("resume-delete-owner-" + UUID.randomUUID() + "@example.com","test-only-password-hash"));
        UserEntity other=userRepository.saveAndFlush(new UserEntity("resume-delete-other-" + UUID.randomUUID() + "@example.com","test-only-password-hash"));
        byte[] pdf;
        try(PDDocument document=new PDDocument(); ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            document.addPage(new PDPage()); document.save(output); pdf=output.toByteArray();
        }
        MockMultipartFile file=new MockMultipartFile("file","delete-me.pdf","application/pdf",pdf);
        var uploaded=mockMvc.perform(multipart("/api/resumes").file(file).param("kind","ORIGINAL")
                .with(user(owner.getEmail())).with(csrf()))
            .andExpect(status().isCreated()).andReturn();
        UUID id=UUID.fromString(com.jayway.jsonpath.JsonPath.read(uploaded.getResponse().getContentAsString(),"$.id"));

        var deletion=mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/resumes/{id}",id)
                .with(user(owner.getEmail())).with(csrf()))
            .andExpect(status().isAccepted())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("DELETING"))
            .andReturn();
        String operationId=com.jayway.jsonpath.JsonPath.read(deletion.getResponse().getContentAsString(),"$.operationId");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM resume_assets WHERE id=?",String.class,id)).isEqualTo("DELETING");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM resume_deletion_jobs WHERE asset_id=? AND job_status='PENDING'",Integer.class,id)).isEqualTo(1);

        mockMvc.perform(get("/api/resumes/{id}/download",id).with(user(owner.getEmail())))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/resumes").with(user(owner.getEmail())))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$").isEmpty());

        var repeated=mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/resumes/{id}",id)
                .with(user(owner.getEmail())).with(csrf()))
            .andExpect(status().isAccepted()).andReturn();
        String repeatedOperationId=com.jayway.jsonpath.JsonPath.read(repeated.getResponse().getContentAsString(),"$.operationId");
        assertThat(repeatedOperationId).isEqualTo(operationId);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/resumes/{id}",id)
                .with(user(other.getEmail())).with(csrf()))
            .andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM resume_assets WHERE id=?",String.class,id)).isEqualTo("DELETING");
    }

    @Test
    void resumeDeletionRequiresCsrfAndAuthentication() throws Exception {
        UUID unknown=UUID.randomUUID();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/resumes/{id}",unknown))
            .andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/resumes/{id}",unknown).with(csrf()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void deletionWorkerRemovesBinaryVerifiesAbsenceAndScrubsAssetMetadata() throws Exception {
        // Other integration tests may leave older durable jobs in the shared Testcontainers database.
        // Keep this test focused on the job it creates below.
        jdbcTemplate.update("DELETE FROM resume_deletion_jobs");
        when(malwareScanner.scan(any(InputStream.class))).thenReturn(new MalwareScanner.ScanResult(true, "CLEAN"));
        UserEntity owner=userRepository.saveAndFlush(new UserEntity("resume-worker-" + UUID.randomUUID() + "@example.com","test-only-password-hash"));
        byte[] pdf;
        try(PDDocument document=new PDDocument(); ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            document.addPage(new PDPage()); document.save(output); pdf=output.toByteArray();
        }
        var upload=mockMvc.perform(multipart("/api/resumes").file(new MockMultipartFile("file","worker-test.pdf","application/pdf",pdf))
                .param("kind","ORIGINAL").with(user(owner.getEmail())).with(csrf()))
            .andExpect(status().isCreated()).andReturn();
        UUID id=UUID.fromString(com.jayway.jsonpath.JsonPath.read(upload.getResponse().getContentAsString(),"$.id"));
        String key=jdbcTemplate.queryForObject("SELECT storage_key FROM resume_assets WHERE id=?",String.class,id);
        assertThat(documentStorage.exists(key)).isTrue();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/resumes/{id}",id)
                .with(user(owner.getEmail())).with(csrf()))
            .andExpect(status().isAccepted());

        assertThat(resumeDeletionWorker.processNextJob()).isTrue();
        var jobState = jdbcTemplate.queryForMap("SELECT job_status, last_error_code FROM resume_deletion_jobs WHERE asset_id=?", id);
        assertThat(jobState.get("job_status")).as("deletion worker job state; error code: %s", jobState.get("last_error_code")).isEqualTo("COMPLETED");
        assertThat(documentStorage.exists(key)).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM resume_assets WHERE id=?",String.class,id)).isEqualTo("DELETED");
        assertThat(jdbcTemplate.queryForObject("SELECT storage_key FROM resume_assets WHERE id=?",String.class,id)).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT original_filename FROM resume_assets WHERE id=?",String.class,id)).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT job_status FROM resume_deletion_jobs WHERE asset_id=?",String.class,id)).isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject("SELECT completed_at FROM resume_deletion_jobs WHERE asset_id=?",java.sql.Timestamp.class,id)).isNotNull();
    }
}
