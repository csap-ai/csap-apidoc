package ai.csap.example.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for the demo security layer.
 *
 * <p>All keys live under {@code csap.example.security.*}. The whole feature
 * is gated by {@link #isEnabled()} — when {@code false} (the default), no
 * Spring Security rules are installed and the example app behaves exactly
 * as it did before this layer was added.
 *
 * <p>This class is intentionally kept simple: in-memory users, HS256 JWT
 * with a single shared secret. It is meant to be a demo of "what happens
 * when csap-apidoc-ui needs to send a Bearer token", NOT a production
 * security blueprint.
 */
@ConfigurationProperties(prefix = "csap.example.security")
public class SecurityProperties {

    private boolean enabled = false;

    /**
     * HS256 signing secret. Must be at least 32 bytes when decoded as UTF-8.
     * The default value below is INSECURE and only acceptable for local demos.
     */
    private String jwtSecret =
            "csap-apidoc-demo-jwt-secret-please-override-in-production-32b+";

    /** Token lifetime in minutes. */
    private int jwtExpirationMinutes = 60;

    /**
     * Issuer claim ({@code iss}) set on issued tokens. Echoed back by
     * {@code GET /auth/whoami} so the doc viewer can sanity-check that it is
     * pointing at the right environment.
     */
    private String issuer = "csap-apidoc-example";

    /**
     * Static list of users. If empty, a default admin/admin123 + user/user123
     * pair is installed so the demo is one-command runnable.
     */
    private List<DemoUser> users = new ArrayList<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getJwtSecret() { return jwtSecret; }
    public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }

    public int getJwtExpirationMinutes() { return jwtExpirationMinutes; }
    public void setJwtExpirationMinutes(int jwtExpirationMinutes) {
        this.jwtExpirationMinutes = jwtExpirationMinutes;
    }

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }

    public List<DemoUser> getUsers() { return users; }
    public void setUsers(List<DemoUser> users) { this.users = users; }

    /** A single configured user. */
    public static class DemoUser {
        private String username;
        private String password;
        private List<String> roles = new ArrayList<>();

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public List<String> getRoles() { return roles; }
        public void setRoles(List<String> roles) { this.roles = roles; }
    }
}
