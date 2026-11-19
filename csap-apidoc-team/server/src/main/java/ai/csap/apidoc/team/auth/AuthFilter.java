package ai.csap.apidoc.team.auth;

import java.io.IOException;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import ai.csap.apidoc.team.common.ApiException;
import ai.csap.apidoc.team.common.ApiExceptionHandler;
import ai.csap.apidoc.team.common.Json;
import ai.csap.apidoc.team.config.TeamProperties;
import ai.csap.apidoc.team.token.TokenService;
import ai.csap.apidoc.team.user.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * API 鉴权:
 * <ul>
 *   <li>{@code /api/open/v1/**}:{@code Authorization: Bearer <令牌>};</li>
 *   <li>{@code /api/v1/**}:Cookie {@code CSAP_TEAM_SESSION};除 GET/HEAD/OPTIONS 外必须带 {@code X-CSAP-Team: 1};
 *       未初始化时除 setup 外返回 SETUP_REQUIRED。</li>
 * </ul>
 */
@Component
public class AuthFilter extends OncePerRequestFilter {

    public static final String COOKIE = "CSAP_TEAM_SESSION";
    private static final Set<String> PUBLIC = Set.of("/api/v1/setup/status", "/api/v1/setup", "/api/v1/auth/login");

    private final UserService users;
    private final SessionTokens sessions;
    private final TokenService tokens;
    private final PasswordService passwords;
    private final ClientIp clientIp;
    private final TeamProperties props;

    public AuthFilter(UserService users, SessionTokens sessions, TokenService tokens, PasswordService passwords,
                      ClientIp clientIp, TeamProperties props) {
        this.users = users;
        this.sessions = sessions;
        this.tokens = tokens;
        this.passwords = passwords;
        this.clientIp = clientIp;
        this.props = props;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        try {
            RequestContext.setIp(clientIp.resolve(req));
            String path = req.getRequestURI();
            if (path.startsWith("/api/open/v1/")) {
                authOpen(req);
            } else if (path.startsWith("/api/v1/")) {
                authConsole(req, res, path);
            } else {
                throw ApiException.notFound("接口不存在");
            }
            chain.doFilter(req, res);
        } catch (ApiException e) {
            writeError(res, e);
        } finally {
            RequestContext.clear();
        }
    }

    private void authOpen(HttpServletRequest req) {
        if (!users.initialized()) {
            throw ApiException.setupRequired();
        }
        String h = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (h == null || !h.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw ApiException.unauthorized("缺少令牌");
        }
        TokenPrincipal p = tokens.authenticate(h.substring(7).trim());
        if (p == null) {
            throw ApiException.unauthorized("令牌无效或已撤销");
        }
        RequestContext.setToken(p);
    }

    private void authConsole(HttpServletRequest req, HttpServletResponse res, String path) {
        boolean isPublic = PUBLIC.contains(path);
        if (!isPublic && !users.initialized()) {
            throw ApiException.setupRequired();
        }
        String m = req.getMethod();
        boolean safe = "GET".equals(m) || "HEAD".equals(m) || "OPTIONS".equals(m);
        if (!safe && !"1".equals(req.getHeader("X-CSAP-Team"))) {
            throw ApiException.forbidden("缺少请求头 X-CSAP-Team: 1");
        }
        Cookie[] cookies = req.getCookies();
        String token = null;
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (COOKIE.equals(c.getName())) {
                    token = c.getValue();
                }
            }
        }
        SessionUser user = null;
        long now = System.currentTimeMillis();
        SessionTokens.Claims claims = sessions.verify(token, now);
        if (claims != null) {
            UserService.UserRow row = users.byId(claims.userId());
            if (row != null && "ACTIVE".equals(row.status()) && passwords.fingerprint(row.passwordHash()).equals(claims.pv())) {
                user = new SessionUser(row.id(), row.username(), row.displayName(), row.orgRole());
                // 滑动过期:超过 5 分钟就重新签发
                if (now - claims.issuedAt() > 300_000L) {
                    addSessionCookie(req, res, sessions.issue(row.id(), claims.pv(), now, ttlMs()));
                }
            }
        }
        if (user != null) {
            RequestContext.setUser(user);
        } else if (!isPublic) {
            throw ApiException.unauthorized("未登录或登录已过期");
        }
    }

    public long ttlMs() {
        return props.getSessionHours() * 3_600_000L;
    }

    public void addSessionCookie(HttpServletRequest req, HttpServletResponse res, String value) {
        ResponseCookie c = ResponseCookie.from(COOKIE, value).httpOnly(true).sameSite("Strict").path("/")
                .secure(req.isSecure()).maxAge(props.getSessionHours() * 3600L).build();
        res.addHeader(HttpHeaders.SET_COOKIE, c.toString());
    }

    public void clearSessionCookie(HttpServletRequest req, HttpServletResponse res) {
        ResponseCookie c = ResponseCookie.from(COOKIE, "").httpOnly(true).sameSite("Strict").path("/")
                .secure(req.isSecure()).maxAge(0).build();
        res.addHeader(HttpHeaders.SET_COOKIE, c.toString());
    }

    private void writeError(HttpServletResponse res, ApiException e) throws IOException {
        res.setStatus(e.status().value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write(Json.write(ApiExceptionHandler.body(e.code(), e.getMessage())));
    }
}
