package ai.careerpilot.security;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.UUID;

@Service
public class AccountDeletionService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public AccountDeletionService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public DeletionAccepted requestDeletion(String authenticatedEmail, String confirmationEmail, String password) {
        String email = normalize(authenticatedEmail);
        if (!email.equals(normalize(confirmationEmail))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account confirmation did not match.");
        }
        var rows = jdbc.query("""
            SELECT id, password_hash, account_status
            FROM users WHERE email=? FOR UPDATE
            """, (rs, row) -> new AccountRow(
                rs.getObject("id", UUID.class),
                rs.getString("password_hash"),
                rs.getString("account_status")), email);
        if (rows.isEmpty() || !"ACTIVE".equals(rows.get(0).status())
                || !passwordEncoder.matches(password, rows.get(0).passwordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account confirmation failed.");
        }
        UUID ownerId = rows.get(0).id();
        UUID operationId = UUID.randomUUID();
        int changed = jdbc.update("""
            UPDATE users SET account_status='DELETION_PENDING', updated_at=CURRENT_TIMESTAMP
            WHERE id=? AND account_status='ACTIVE'
            """, ownerId);
        if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Account state changed.");
        jdbc.update("""
            INSERT INTO account_deletion_jobs (operation_id, owner_id, job_status, requested_at, attempt_count)
            VALUES (?, ?, 'PENDING', CURRENT_TIMESTAMP, 0)
            """, operationId, ownerId);
        // Spring Session's principal index is the normalized authenticated email.
        jdbc.update("DELETE FROM spring_session WHERE principal_name=?", email);
        return new DeletionAccepted(operationId, "DELETION_PENDING");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record AccountRow(UUID id, String passwordHash, String status) {}
    public record DeletionAccepted(UUID operationId, String status) {}
}
