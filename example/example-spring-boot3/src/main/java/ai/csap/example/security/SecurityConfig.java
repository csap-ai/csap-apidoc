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
            // CSAP API Doc 数据接口:/csap/apidoc、/csap/apidoc/{tableName}、/csap/apidoc/parent、
            // /csap/apidoc/method 与试运行代理 /csap/apidoc/devtools/proxy(默认关闭)
            "/csap/apidoc",
            "/csap/apidoc/**",
            // OpenAPI / Postman 导出
            "/csap/openapi.json",
            "/csap/openapi/**",
            // 文档页面(csap-apidoc-ui)及其静态资源
            "/csap-api.html",
            "/assets/**",
            // DevTools 页面、静态资源和后端接口(/devtools/**、/csap/yaml/**)。
            // 只在开发环境开启 csap.apidoc.devtool.enabled;生产环境关闭 DevTools,或去掉这几行改为需要登录。
            "/csap-api-devtools.html",
            "/assets2/**",
            "/devtools-ui",
            "/devtools-ui/**",
            "/devtools/**",
            "/csap/yaml/**",
            // 内嵌 MCP 端点:token 模式由 MCP 自己校验令牌;passthrough 模式下接口的访问控制
            // 照常由本过滤链对回环请求执行(见官网 MCP 指南"和 Spring Security 一起使用")
            "/csap/mcp",
            // 示例登录接口本身不需要令牌
            "/auth/**",
            // 运维
            "/actuator/health",
            "/error",
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
