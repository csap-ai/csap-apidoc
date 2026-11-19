package ai.csap.apidoc.team.auth;

import java.nio.charset.StandardCharsets;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import ai.csap.apidoc.team.common.ApiException;

@Component
public class PasswordService {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
    private final String dummyHash = encoder.encode("csap-team-dummy");

    /** 用户不存在时也做一次比较,避免通过耗时枚举用户名。 */
    public void burn(String raw) {
        matches(raw, dummyHash);
    }

    public String hash(String raw) {
        return encoder.encode(raw);
    }

    public boolean matches(String raw, String hash) {
        try {
            return raw != null && hash != null && encoder.matches(raw, hash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** 密码规则:8–72 字节(BCrypt 上限)。 */
    public void validate(String raw) {
        if (raw == null || raw.length() < 8) {
            throw ApiException.validation("密码至少 8 位");
        }
        if (raw.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.validation("密码过长(最多 72 字节)");
        }
    }

    /** 密码版本指纹,放进会话令牌;改密码后旧会话失效。 */
    public String fingerprint(String hash) {
        return Integer.toHexString(hash.hashCode()) + hash.length();
    }
}
