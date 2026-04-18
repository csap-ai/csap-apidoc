package ai.csap.example.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Map;

/**
 * Spring Security wiring for the demo.
 *
 * <p>The class is unconditionally loaded but only one of its
 * {@link SecurityFilterChain} beans is active at a time:
 *
 * <ul>
 *   <li>{@link #jwtSecurityFilterChain} — when {@code csap.example.security.enabled=true}.
 *   <li>{@link #permitAllSecurityFilterChain} — otherwise (default).
 * </ul>
 *
 * <p>The permit-all chain explicitly disables Spring Boot's default
 * "everything requires basic auth + a generated password" behaviour so that
 * the demo continues to behave exactly as it did before this layer existed.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    /**
     * List of paths that are PUBLIC even when JWT auth is enabled, so the doc
     * viewer can fetch metadata, the login form works, and basic health
     * probes succeed.
     */
    static final String[] PUBLIC_PATHS = {
            // CSAP API Doc — metadata endpoints consumed by csap-apidoc-ui.
            "/csap/apidoc/**",
            "/api-doc",
            "/api-doc/**",
            // CSAP API Doc — embedded HTML viewers.
            "/csap-api.html",
            "/csap-api-devtools.html",
            // csap-apidoc-devtools backend (used by backend-side controllers
            // to manage doc metadata).
            "/devtools-ui",
            "/devtools-ui/**",
            "/api/devtools/**",
            // Demo auth endpoints themselves must be reachable without a token.
            "/auth/**",
            // Operational.
            "/actuator/health",
            "/error",
            "/static/**",
            "/assets/**",
            "/favicon.ico",
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @ConditionalOnProperty(prefix = "csap.example.security", name = "enabled", havingValue = "true")
    public AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(encoder);
        return new org.springframework.security.authentication.ProviderManager(List.of(provider));
    }

    @Bean
    @ConditionalOnProperty(prefix = "csap.example.security", name = "enabled", havingValue = "true")
    public SecurityFilterChain jwtSecurityFilterChain(
            HttpSecurity http,
            JwtService jwtService,
            ObjectMapper objectMapper) throws Exception {

        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtService);

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s ->
                        s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(eh ->
                        eh.authenticationEntryPoint((req, res, ex) -> writeJsonError(
                                res, objectMapper, HttpServletResponse.SC_UNAUTHORIZED,
                                "UNAUTHORIZED",
                                "Bearer token is missing or invalid. POST /auth/login to obtain one.")))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "csap.example.security", name = "enabled",
            havingValue = "false", matchIfMissing = true)
    public SecurityFilterChain permitAllSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s ->
                        s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable());
        return http.build();
    }

    /**
     * Permissive CORS so csap-apidoc-ui (running on a different origin during
     * development, e.g. http://localhost:5173) can issue Try-it-out requests.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", "Content-Disposition"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private static void writeJsonError(
            HttpServletResponse res, ObjectMapper mapper,
            int status, String code, String message) throws java.io.IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        mapper.writeValue(res.getWriter(), Map.of(
                "code", code,
                "message", message,
                "status", status));
    }
}
