package ai.csap.example.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plaintext-config backed user store.
 *
 * <p>Reads {@code csap.example.security.users}; if empty, falls back to a
 * built-in admin/admin123 + user/user123 pair so the demo is one-command
 * runnable.
 */
@Component
@ConditionalOnProperty(prefix = "csap.example.security", name = "enabled", havingValue = "true")
public class InMemoryUserStore implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(InMemoryUserStore.class);

    private final Map<String, UserDetails> usersByName = new HashMap<>();

    public InMemoryUserStore(SecurityProperties props, PasswordEncoder encoder) {
        List<SecurityProperties.DemoUser> configured = props.getUsers();
        if (configured == null || configured.isEmpty()) {
            register(encoder, "admin", "admin123", List.of("ADMIN"));
            register(encoder, "user", "user123", List.of("USER"));
            log.warn("[csap-example] no users configured under csap.example.security.users; "
                    + "installed built-in demo pair admin/admin123 and user/user123");
        } else {
            for (SecurityProperties.DemoUser u : configured) {
                if (u.getUsername() == null || u.getPassword() == null) continue;
                register(encoder, u.getUsername(), u.getPassword(),
                        u.getRoles() == null ? List.of("USER") : u.getRoles());
            }
        }
    }

    private void register(
            PasswordEncoder encoder, String username, String rawPassword, List<String> roles) {
        UserDetails ud = User.builder()
                .username(username)
                .password(encoder.encode(rawPassword))
                .authorities(roles.stream()
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                        .toList())
                .build();
        usersByName.put(username, ud);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserDetails ud = usersByName.get(username);
        if (ud == null) throw new UsernameNotFoundException(username);
        return ud;
    }
}
