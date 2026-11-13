package ai.careerpilot.security;

import ai.careerpilot.persistence.entity.UserEntity;
import ai.careerpilot.persistence.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final boolean registrationEnabled;
    private final AccountDeletionService accountDeletionService;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(UserRepository users, PasswordEncoder passwordEncoder,
                          AuthenticationManager authenticationManager,
                          AccountDeletionService accountDeletionService,
                          @Value("${app.registration.enabled:false}") boolean registrationEnabled) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.accountDeletionService = accountDeletionService;
        this.registrationEnabled = registrationEnabled;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegistrationRequest request) {
        if (!registrationEnabled) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", "Registration is not enabled for this deployment."));
        }
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            return ResponseEntity.accepted()
                .body(Map.of("message", "If registration is available for this address, the account can now be used."));
        }
        UserEntity account = new UserEntity(email, passwordEncoder.encode(request.password()));
        account.setAccountStatus("ACTIVE");
        users.save(account);
        return ResponseEntity.accepted()
            .body(Map.of("message", "If registration is available for this address, the account can now be used."));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
                                   HttpServletRequest servletRequest,
                                   HttpServletResponse servletResponse) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(normalize(request.email()), request.password()));
            if (servletRequest.getSession(false) != null) {
                servletRequest.changeSessionId();
            } else {
                servletRequest.getSession(true);
            }
            servletRequest.getSession().setAttribute("AUTHENTICATED_AT", System.currentTimeMillis());
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            contextRepository.saveContext(context, servletRequest, servletResponse);
            return ResponseEntity.ok(Map.of("email", authentication.getName()));
        } catch (AuthenticationException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Invalid email or password."));
        }
    }

    @GetMapping("/me")
    public Map<String, String> me(Authentication authentication) {
        return Map.of("email", authentication.getName());
    }

    @DeleteMapping("/account")
    public ResponseEntity<?> deleteAccount(Authentication authentication,
            @Valid @RequestBody AccountDeletionRequest confirmation,
            HttpServletRequest servletRequest) {
        AccountDeletionService.DeletionAccepted accepted = accountDeletionService.requestDeletion(
            authentication.getName(), confirmation.confirmationEmail(), confirmation.password());
        if (servletRequest.getSession(false) != null) {
            servletRequest.getSession(false).invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.accepted().body(Map.of(
            "operationId", accepted.operationId(),
            "status", accepted.status(),
            "message", "Account deletion has started. This account can no longer be used."));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public record RegistrationRequest(@NotBlank @Email @Size(max = 320) String email,
                                      @NotBlank @Size(min = 12, max = 128) String password) {}
    public record LoginRequest(@NotBlank @Email @Size(max = 320) String email,
                               @NotBlank @Size(max = 128) String password) {}
    public record AccountDeletionRequest(@NotBlank @Email @Size(max = 320) String confirmationEmail,
                                         @NotBlank @Size(max = 128) String password) {}
}
