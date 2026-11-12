package ai.careerpilot.security;

import ai.careerpilot.persistence.repository.UserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AccountUserDetailsService implements UserDetailsService {
    private final UserRepository users;

    public AccountUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return users.findByEmail(username.trim().toLowerCase(java.util.Locale.ROOT))
            .map(account -> User.withUsername(account.getEmail())
                .password(account.getPasswordHash())
                .roles("USER")
                .disabled(!"ACTIVE".equals(account.getAccountStatus()))
                .build())
            .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }
}
