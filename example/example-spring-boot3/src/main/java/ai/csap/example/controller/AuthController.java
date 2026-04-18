package ai.csap.example.controller;

import ai.csap.apidoc.annotation.Api;
import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ApiModelProperty;
import ai.csap.apidoc.annotation.ApiOperation;
import ai.csap.example.model.Response;
import ai.csap.example.security.JwtService;
import ai.csap.example.security.SecurityProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * Issue / inspect demo JWTs.
 *
 * <p>Only registered when {@code csap.example.security.enabled=true}. When the
 * security layer is OFF, {@code /auth/**} simply does not exist (404), which
 * matches the pre-feature behaviour.
 */
@RestController
@RequestMapping("/auth")
@Api(value = "示例：认证（仅启用 demo 鉴权时存在）", description = "登录换取 JWT，并查看当前身份信息")
@ConditionalOnProperty(prefix = "csap.example.security", name = "enabled", havingValue = "true")
public class AuthController {

    private final AuthenticationManager authManager;
    private final JwtService jwtService;
    private final SecurityProperties props;

    public AuthController(
            AuthenticationManager authManager,
            JwtService jwtService,
            SecurityProperties props) {
        this.authManager = authManager;
        this.jwtService = jwtService;
        this.props = props;
    }

    @PostMapping("/login")
    @ApiOperation(value = "登录", description = "用账号密码换取 access token，放入 Authorization: Bearer ...")
    public Response<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));

        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                .toList();

        var issued = jwtService.issueToken(auth.getName(), roles);

        LoginResponse body = new LoginResponse();
        body.setTokenType("Bearer");
        body.setAccessToken(issued.token());
        body.setExpiresAt(issued.expiresAt());
        body.setUsername(auth.getName());
        body.setRoles(roles);
        body.setIssuer(props.getIssuer());
        return Response.success(body);
    }

    @GetMapping("/whoami")
    @ApiOperation(value = "查看当前身份", description = "返回 Bearer token 解析后的用户、角色、过期时间")
    public Response<WhoAmIResponse> whoami() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return Response.error(401, "Not authenticated");
        }
        WhoAmIResponse body = new WhoAmIResponse();
        body.setUsername(auth.getName());
        body.setRoles(auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                .toList());
        body.setIssuer(props.getIssuer());
        return Response.success(body);
    }

    @Data
    @ApiModel(description = "登录请求")
    public static class LoginRequest {
        @ApiModelProperty(value = "用户名", example = "admin", forceRep = true)
        @NotBlank
        private String username;

        @ApiModelProperty(value = "密码", example = "admin123", forceRep = true)
        @NotBlank
        private String password;
    }

    @Data
    @ApiModel(description = "登录响应")
    public static class LoginResponse {
        @ApiModelProperty(value = "Token 类型", example = "Bearer", forceRep = true)
        private String tokenType;

        @ApiModelProperty(value = "Access Token (JWT)", forceRep = true)
        private String accessToken;

        @ApiModelProperty(value = "过期时间 (UTC)", forceRep = true)
        private Instant expiresAt;

        @ApiModelProperty(value = "用户名", forceRep = true)
        private String username;

        @ApiModelProperty(value = "角色列表", forceRep = true)
        private List<String> roles;

        @ApiModelProperty(value = "签发者 (iss)", forceRep = true)
        private String issuer;
    }

    @Data
    @ApiModel(description = "当前身份信息")
    public static class WhoAmIResponse {
        @ApiModelProperty(value = "用户名", forceRep = true)
        private String username;

        @ApiModelProperty(value = "角色列表", forceRep = true)
        private List<String> roles;

        @ApiModelProperty(value = "签发者 (iss)", forceRep = true)
        private String issuer;
    }
}
