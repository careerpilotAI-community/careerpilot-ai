package ai.careerpilot;

import ai.careerpilot.persistence.entity.JobEntity;
import ai.careerpilot.persistence.entity.UserEntity;
import ai.careerpilot.persistence.repository.JobRepository;
import ai.careerpilot.persistence.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.registration.enabled=true")
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

    @Test
    void contextLoadsAfterFreshFlywayMigration() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank DESC LIMIT 1",
                String.class)).isEqualTo("2");
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

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getAttribute("AUTHENTICATED_AT")).isInstanceOf(Long.class);

        mockMvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isOk());
    }

    @Test
    void protectedEndpointRejectsAnonymousRequest() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
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
    void databaseRejectsNonNormalizedEmail() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO users (id, email, password_hash)
                VALUES (?, ?, ?)
                """, UUID.randomUUID(), " MixedCase@example.com ", "test-only-password-hash"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
